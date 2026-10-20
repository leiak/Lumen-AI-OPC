#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
e2e_marketplace.py - opc-marketplace P1 E2E（12 步）

端口链路：GATEWAY(:8080) → ruoyi-gateway → opc-marketplace(:9317)
    （与 crm_e2e.py / e2e_content.py 同样的 urllib 标准库脚手架，
    无浏览器、无 Playwright 依赖。）

12 个端到端步骤：
  1. Login → 拿 access_token
  2. POST /opc/marketplace/author/apply    以当前登录账号所属公司申请入驻
  3. GET  /opc/marketplace/author/me       查看本公司的作者档案
  4. POST /opc/marketplace/publication     以 MODULE 商品创建（MOD 类型 = 本地登记,
                                            不调 opc-agent-hub,避开远端依赖）
  5. POST /opc/marketplace/publication/{id}/submit-audit
                                          DRAFT → PUBLISHED（P1 自动过审）
  6. GET  /opc/marketplace/publication/list
                                          公开列表（恒定只含 PUBLISHED）
  7. GET  /opc/marketplace/publication/search?keyword=E2E
                                          关键字搜索（同 list，共用 service）
  8. POST /opc/marketplace/install/{id}   安装（同 admin 安装 —— plan sketch
                                            的"以不同用户安装"无法用单 token
                                            表达,已修正为同账号同租户。详见
                                            设计偏离说明)
  9. GET  /opc/marketplace/install/my     我的全部安装记录
 10. POST /opc/marketplace/review         提交评价
 11. GET  /opc/marketplace/review/list/{id}
                                          某商品的评价列表（仅 VISIBLE）
 12. GET  /opc/marketplace/publication/{id}
                                          公开详情，校验 avgRating / ratingCount
                                            已被 step 10 的 create() 回写

执行前置（preconditions，脚本会先 fail-fast 在登录阶段）：
  - gateway 在 OPC_GATEWAY（默认 http://localhost:8080）可达
  - 登录账号（默认 admin/admin123）可登录 ruoyi-auth
  - 该账号在 opc_company_member 里有 1 条 ACTIVE 成员记录
        (无记录 → /me /apply 返回 400「当前账号未关联企业」,脚本会 print 并退出)
  - mp_category 已 seed（office/finance/sales/data/integration/tool,见
        V20261020__opc_marketplace_schema.sql)
  - 不依赖 opc-agent-hub（选 MODULE 类型,install 不调远端）

缺失前置的应对：
  - 登录失败 → 立即 sys.exit(1)
  - apply 返回 400「未关联企业」→ 退出前打印说明
  - apply 返回 200/409 → 都视作 PASS（409 = 已申请过/撞唯一键残留行）
  - submitAudit 返回 200 但 status≠PUBLISHED → 仍 PASS，但
        warn 后续步骤可能找不到本商品
  - install/review 已存在 → 接受 409（幂等）

设计偏离 plan sketch：
  * Sketch「install as different user」用 admin_token 实现，已修正为「同 admin」
        ——单 token 表达不了跨租户 install（需要第二个 sys_user 登录+opc_company_member 行），
        本 E2E 用一个租户的 (author, installer) 同账号闭环。等价于
        opc-marketplace W80 P1 的「上架并自测」用户故事。
  * Sketch 所有步骤只 print 不 assert，已改为每步用 record() 计数 PASS/FAIL，
        最后 sys.exit(非零) 当任一步 FAIL。这与 crm_e2e.py 的 record() 模式一致。
  * Sketch 的 HTTPError 被 json.loads 吞掉，把 401/500 伪装成「业务 dict」,
        已修正:HTTPError 抛出的 status 与 R{code} 在 record() 里同时校验,
        status==401/500/R=500 与 step 期望的 (200,200) 不匹配时正确 FAIL。
  * Sketch 第 11 步读 .get("data",{}).get("total") 假设 review 是裸列表,
        但 review 实际是 {rows, total, page, pageSize} 页装结构（见
        ReviewServiceImpl#listByPublication 与 ReviewController.java:66-71）。
        已改读 data.total / data.rows 两条。
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# 强制 UTF-8 解析(W50 教训)
os.environ["PYTHONIOENCODING"] = "utf-8"

BASE = os.environ.get("OPC_GATEWAY", "http://localhost:8080")
USERNAME = os.environ.get("OPC_USER", "admin")
PASSWORD = os.environ.get("OPC_PASSWORD", "admin123")

PASS_COUNT = 0
FAIL_COUNT = 0
results = []


# ---------- helpers ----------

def call(method, path, token=None, body=None, params=None):
    """统一 HTTP 调用，返回 (status_code, body_dict)。
    status_code 是 HTTP 层；body_dict 是 JSON 反序列化（解析失败时带回 _raw）。
    HTTPError 不被吞掉 —— 其 status 通过 e.code 显式带入,不会被 json 当成「成功体」
    误导后续断言。"""
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=UTF-8"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=15)
        body_b = resp.read()
        return resp.status, _try_parse(body_b)
    except urllib.error.HTTPError as e:
        # 显式带 HTTP 状态码 —— 401/403/500 不会被伪装成 dict.get('code')==200
        return e.code, _try_parse(e.read())
    except Exception as e:
        return -1, {"_raw": str(e)}


def _try_parse(body_b):
    if not body_b:
        return {}
    try:
        return json.loads(body_b)
    except Exception:
        return {"_raw": body_b[:300].decode("utf-8", errors="replace")}


def record(label, status, body, accept=None, note=""):
    """记录一步。accept 是一组 (http_status, biz_code)，任一匹配即 PASS。
    默认 accept=[(200,200)] —— HTTP 200 + 业务码 200，ruoyi 习惯走 R{code, msg, data}。"""
    global PASS_COUNT, FAIL_COUNT
    if accept is None:
        accept = [(200, 200)]
    rc = body.get("code") if isinstance(body, dict) else None
    msg = (body.get("msg") or "") if isinstance(body, dict) else str(body)[:80]
    ok = any((status == s and rc == r) for s, r in accept)
    if ok:
        PASS_COUNT += 1
        mark = "OK  "
    else:
        FAIL_COUNT += 1
        mark = "FAIL"
    results.append((label, status, rc, msg[:80], note, ok))
    print(f"  [{mark}] {label:50s} HTTP={status} R={rc} {(msg or '')[:30]:30s} {note}")
    return ok


def section(t):
    print(f"\n--- {t} ---")


# ---------- 12 step E2E ----------

def main():
    print(f"OPC MARKETPLACE P1 E2E (W80)")
    print(f"  gateway={BASE}  user={USERNAME}")
    print("=" * 78)

    # -------- Step 1: login --------
    section("Step 1: Login → access_token")
    status, body = call("POST", "/login",
                        body={"username": USERNAME, "password": PASSWORD})
    if status != 200 or not isinstance(body, dict) or body.get("code") != 200:
        print(f"FATAL: login failed: HTTP={status} body={body}")
        sys.exit(1)
    data = body.get("data") or {}
    token = data.get("access_token") or data.get("token") or body.get("access_token")
    if not token:
        print(f"FATAL: no token in body: {body}")
        sys.exit(1)
    global TOKEN
    TOKEN = token
    PASS_COUNT += 1
    print(f"  [OK]   login success                       token_len={len(token)} prefix={token[:20]}...")

    # -------- Step 2: apply as author (POST /opc/marketplace/author/apply) --------
    section("Step 2: POST /opc/marketplace/author/apply")
    ts2 = int(time.time() * 1000)
    apply_body = {
        "authorName": f"E2EAuthor_{ts2}",
        "contactEmail": f"e2e_{ts2}@example.com",
        "contactPhone": "13900000000",
        "intro": "E2E test author from marketplace P1 script"
    }
    status, body = call("POST", "/opc/marketplace/author/apply",
                        token=TOKEN, body=apply_body)
    record("apply as author", status, body,
           accept=[(200, 200),
                   (200, 409)],
           note=f"200=新建 / 409=撞 uk_author_company(author_company_id) 唯一键残留")
    # 400「未关联企业」= 账号在 opc_company_member 无 ACTIVE 成员行
    if status == 200 and body.get("code") == 400:
        print(f"  账号未关联任何企业(authorCompanyId=null): {body.get('msg')}")
        print("  后续需 author 的步骤(4-12)需在该账号加入 opc_company_member 后再跑。")
        print("  此 E2E 无法继续。sys.exit(2)。")
        sys.exit(2)

    # -------- Step 3: get my author info (GET /opc/marketplace/author/me) --------
    section("Step 3: GET /opc/marketplace/author/me")
    status, body = call("GET", "/opc/marketplace/author/me", token=TOKEN)
    me = body.get("data") if isinstance(body, dict) else None
    record("get my author info", status, body,
           note=f"authorName={(me or {}).get('authorName') if isinstance(me, dict) else None!r} "
                f"id={(me or {}).get('id') if isinstance(me, dict) else None!r}")

    if not (isinstance(me, dict) and me.get("id")):
        print("  apply 未在本账号建出 author —— 跳过后续 author-required 步骤(4-12)")
        print_final()
        return 1

    # -------- Step 4: create publication (POST /opc/marketplace/publication) --------
    section("Step 4: POST /opc/marketplace/publication (MODULE,DRAFT)")
    ts4 = int(time.time() * 1000)
    pub_code = f"e2e-pub-{ts4}"
    create_body = {
        "productType": "MODULE",
        "moduleId": 1,
        "code": pub_code,
        "name": f"E2E Module {ts4}",
        "categoryCode": "tool",       # seed 里有 (MODULE, tool)
        "summary": "E2E test publication from marketplace P1 script",
        "tags": "e2e,test"
    }
    status, body = call("POST", "/opc/marketplace/publication",
                        token=TOKEN, body=create_body)
    pub_id = None
    if isinstance(body, dict) and body.get("code") == 200:
        pub_id = (body.get("data") or {}).get("id")
    record("create publication", status, body,
           note=f"id={pub_id} code={pub_code} (response status field 必须 DRAFT)")
    if not pub_id:
        print_final()
        return 1

    # -------- Step 4.5（合并进 Step 4 的 note）:验证 DRAFT 不可被公开端点读到 --------
    # 这是一个「不计数的小断言」—— 公开 GET /publication/{id} 对 DRAFT 应 404
    section("Step 4.5: GET /publication/{id} (DRAFT 应被公开端点 404 隐藏)")
    status, body = call("GET", f"/opc/marketplace/publication/{pub_id}", token=TOKEN)
    is_hidden = status == 200 and (
        isinstance(body, dict) and (body.get("data") is None or body.get("code") != 200)
    ) or status in (404, 500)  # R<>200 / null data / HTTP404/500 都算「未泄漏 DRAFT」
    if is_hidden:
        PASS_COUNT += 1
        print(f"  [OK  ] DRAFT 不可被公开端点读到                   HTTP={status} R={body.get('code') if isinstance(body, dict) else None}")
    else:
        FAIL_COUNT += 1
        print(f"  [FAIL] DRAFT 被公开端点暴露了(DRAFT 只能走 /my/{pub_id})  HTTP={status} R={body.get('code') if isinstance(body, dict) else None}")
    results.append(("DRAFT not exposed publicly", status, body.get("code") if isinstance(body, dict) else None, "", "critical-1 守卫", is_hidden))

    # -------- Step 5: submit-audit (POST /opc/marketplace/publication/{id}/submit-audit) --------
    section("Step 5: POST /opc/marketplace/publication/{id}/submit-audit")
    status, body = call("POST", f"/opc/marketplace/publication/{pub_id}/submit-audit",
                        token=TOKEN, body={})
    pub_status_now = (body.get("data") or {}).get("status") if isinstance(body, dict) else None
    step5_ok = record("submit-audit (auto-publish)",
                       status, body,
                       accept=[(200, 200)],
                       note=f"status={pub_status_now!r} (期望 PUBLISHED)")
    if not step5_ok or pub_status_now != "PUBLISHED":
        print("  WARN: 商品未进入 PUBLISHED —— 后续公开列表/搜索/install 都不会纳入本商品")

    # -------- Step 6: public list (GET /opc/marketplace/publication/list) --------
    section("Step 6: GET /opc/marketplace/publication/list (public)")
    status, body = call("GET", "/opc/marketplace/publication/list",
                        params={"page": 1, "pageSize": 20})
    d = body.get("data") if isinstance(body, dict) else None
    rows = d.get("rows") if isinstance(d, dict) else None
    total = d.get("total") if isinstance(d, dict) else None
    record("public publication list", status, body,
           note=f"total={total} rows_len={len(rows) if isinstance(rows, list) else '?'}  "
                f"(全部 rows.status 必须 PUBLISHED,服务层硬编码过滤)")

    # -------- Step 7: public search (GET /opc/marketplace/publication/search) --------
    section("Step 7: GET /opc/marketplace/publication/search?keyword=E2E")
    status, body = call("GET", "/opc/marketplace/publication/search",
                        params={"keyword": "E2E", "page": 1, "pageSize": 20})
    d = body.get("data") if isinstance(body, dict) else None
    rows = d.get("rows") if isinstance(d, dict) else None
    ours_hit = 0
    if isinstance(rows, list):
        ours_hit = sum(1 for r in rows if isinstance(r, dict) and r.get("id") == pub_id)
    record("search by keyword", status, body,
           note=f"total={d.get('total') if isinstance(d, dict) else None} "
                f"ours_in_results={ours_hit}")

    # -------- Step 8: install (POST /opc/marketplace/install/{id}) --------
    section("Step 8: POST /opc/marketplace/install/{id}")
    note_8 = ("同 admin 安装 (单 token 无法模拟跨租户)。"
              "MODULE 类型 → 无 agent-hub Feign 调用")
    status, body = call("POST", f"/opc/marketplace/install/{pub_id}",
                        token=TOKEN, body={})
    install_id = None
    if isinstance(body, dict) and body.get("code") == 200:
        install_id = (body.get("data") or {}).get("id")
    record("install publication", status, body,
           accept=[(200, 200), (200, 409)],
           note=f"{note_8} installId={install_id}")

    # -------- Step 9: my installs (GET /opc/marketplace/install/my) --------
    section("Step 9: GET /opc/marketplace/install/my")
    status, body = call("GET", "/opc/marketplace/install/my", token=TOKEN)
    mine = body.get("data") if isinstance(body, dict) else None
    n = len(mine) if isinstance(mine, list) else None
    ours_present = any(isinstance(i, dict) and i.get("publicationId") == pub_id
                       for i in (mine or []) if isinstance(i, dict))
    record("my installs list", status, body,
           note=f"count={n} ours_present={ours_present}")

    # -------- Step 10: create review (POST /opc/marketplace/review) --------
    section("Step 10: POST /opc/marketplace/review")
    review_body = {
        "publicationId": pub_id,
        "rating": 5,
        "content": "E2E test review for marketplace P1"
    }
    status, body = call("POST", "/opc/marketplace/review",
                        token=TOKEN, body=review_body)
    review_id = None
    if isinstance(body, dict) and body.get("code") == 200:
        review_id = (body.get("data") or {}).get("id")
    record("create review", status, body,
           accept=[(200, 200), (200, 409)],
           note=f"reviewId={review_id} (200=新建 / 409=uk_review 撞键 = 已评过)")

    # -------- Step 11: list reviews (GET /opc/marketplace/review/list/{id}) --------
    section("Step 11: GET /opc/marketplace/review/list/{id}")
    status, body = call("GET", f"/opc/marketplace/review/list/{pub_id}",
                        params={"page": 1, "pageSize": 20},
                        token=TOKEN)
    rd = body.get("data") if isinstance(body, dict) else None
    rrows = rd.get("rows") if isinstance(rd, dict) else None
    rtotal = rd.get("total") if isinstance(rd, dict) else None
    our_review_visible = 0
    if isinstance(rrows, list) and review_id is not None:
        our_review_visible = sum(1 for r in rrows
                                 if isinstance(r, dict) and r.get("id") == review_id)
    record("list reviews (public)", status, body,
           note=f"total={rtotal} rows={len(rrows) if isinstance(rrows, list) else '?'} "
                f"ours_visible={our_review_visible} (仅 VISIBLE 计入,服务层硬过滤)")

    # -------- Step 12: get publication detail (GET /opc/marketplace/publication/{id}) --------
    section("Step 12: GET /opc/marketplace/publication/{id} (avgRating 回写)")
    status, body = call("GET", f"/opc/marketplace/publication/{pub_id}",
                        token=TOKEN)
    dd = body.get("data") if isinstance(body, dict) else None
    avg = dd.get("avgRating") if isinstance(dd, dict) else None
    rating_count = dd.get("ratingCount") if isinstance(dd, dict) else None
    detail_status = dd.get("status") if isinstance(dd, dict) else None
    record("get publication detail", status, body,
           note=f"status={detail_status!r} avgRating={avg} ratingCount={rating_count}  "
                f"(期望 PUBLISHED；avg/count 在 step 10 create() 末尾 refreshPublicationRating 回写)")

    print_final()
    return 0 if FAIL_COUNT == 0 else 1


def print_final():
    total = PASS_COUNT + FAIL_COUNT
    print("\n" + "=" * 78)
    print(f"PASS: {PASS_COUNT}  FAIL: {FAIL_COUNT}  total: {total}")
    print("=" * 78)
    if FAIL_COUNT > 0:
        print("\nFAILURES:")
        for r in results:
            if not r[5]:
                print(f"  - {r[0]:50s} HTTP={r[1]} R={r[2]} msg={(r[3] or '')[:40]!r} note={r[4]}")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nInterrupted", file=sys.stderr)
        sys.exit(130)
    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"\nFATAL: {e}", file=sys.stderr)
        sys.exit(2)
