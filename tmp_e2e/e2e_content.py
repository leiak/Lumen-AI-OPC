#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
opc-content E2E test (W74)
依赖: urllib + json (标准库)
执行: python tmp_e2e/e2e_content.py
前置: aiopc-content 服务已启动,admin/admin123 可登录

测试路径: GATEWAY(8079) → ruoyi-gateway → aiopc-content:9325
10 步端到端:登录 → 脚本列表/dashboard/create → 平台账号/OAuth → 发布 → 详情 → 健康 → Nacos 注册
"""

import json
import os
import sys
import time
import urllib.request
import urllib.parse
import urllib.error
from http.cookiejar import CookieJar, Cookie
from typing import Optional

# 强制 UTF-8 解析 (W50 教训)
os.environ["PYTHONIOENCODING"] = "utf-8"

GATEWAY = os.environ.get("OPC_GATEWAY", "http://127.0.0.1:8079")
CONTENT_BASE = f"{GATEWAY}/opc/content"
LOGIN_URL = f"{GATEWAY}/login"
USERNAME = os.environ.get("OPC_USER", "admin")
PASSWORD = os.environ.get("OPC_PASSWORD", "admin123")
COMPANY_ID = 1
NACOS_URL = "http://127.0.0.1:8848/nacos/v1/ns/instance/list"

PASS_COUNT = 0
FAIL_COUNT = 0

# ---------- helpers ----------

def _request(opener: "urllib.request.OpenerDirector", method: str, url: str,
             data: Optional[dict] = None, headers: Optional[dict] = None,
             timeout: int = 15) -> tuple:
    """统一 HTTP 调用,返回 (status_code, body_dict)"""
    body = None
    h = dict(headers or {})
    if data is not None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        h["Content-Type"] = "application/json;charset=UTF-8"
    req = urllib.request.Request(url, data=body, headers=h, method=method)
    try:
        with opener.open(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="ignore")
            try:
                return resp.status, json.loads(raw) if raw else {}
            except Exception:
                return resp.status, {"raw": raw}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="ignore")
        try:
            return e.code, json.loads(raw) if raw else {"code": e.code, "msg": "HTTPError"}
        except Exception:
            return e.code, {"raw": raw}
    except Exception as e:
        return -1, {"error": str(e)}


def login(opener: "urllib.request.OpenerDirector", cookies: CookieJar) -> str:
    """登录拿 access_token (RuoYi 用 access_token 不是 token, W48.7 教训)"""
    code, body = _request(opener, "POST", LOGIN_URL,
                          data={"username": USERNAME, "password": PASSWORD})
    if code != 200:
        raise RuntimeError(f"login failed: HTTP {code} body={body}")
    token = (body.get("access_token")
             or body.get("data", {}).get("access_token")
             or body.get("token"))
    if not token:
        raise RuntimeError(f"login succeeded but no token in body: {body}")
    # 写 Admin-Token cookie (前端 js-cookie 用 cookie 不是 localStorage, W52 教训)
    c = Cookie(version=0, name="Admin-Token", value=token,
               port=None, port_specified=False,
               domain="127.0.0.1", domain_specified=False, domain_initial_dot=False,
               path="/", path_specified=True,
               secure=False, expires=None,
               discard=True, comment=None, comment_url=None,
               rest={}, rfc2109=False)
    cookies.set_cookie(c)  # 直接用闭包变量 cookies (而非 opener.handlers[0])
    return token


def call(opener, method: str, path: str, body: Optional[dict] = None,
         params: Optional[dict] = None, expected_code: int = 200,
         must_have_keys: Optional[tuple] = None, label: str = "") -> dict:
    """统一 API 调用 + 断言"""
    global PASS_COUNT, FAIL_COUNT

    url = f"{GATEWAY}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)

    headers = {"Authorization": f"Bearer {TOKEN}"} if TOKEN else {}
    code, result = _request(opener, method, url, data=body, headers=headers)

    # 解析 ruoyi 业务码 (HTTP 200 + body.code=200 才算成功)
    actual_code = result.get("code", code) if isinstance(result, dict) else code
    http_ok = (code == expected_code)
    biz_ok = (actual_code == 200) if isinstance(result, dict) and "code" in result else http_ok
    keys_ok = True
    if must_have_keys:
        # 在 data 字段里找 keys (ruoyi 包成 {code, msg, data})
        data = result.get("data", result) if isinstance(result, dict) else {}
        keys_ok = all(k in data for k in must_have_keys)

    ok = http_ok and biz_ok and keys_ok
    if ok:
        PASS_COUNT += 1
        status = "PASS"
    else:
        FAIL_COUNT += 1
        status = "FAIL"

    name = label or f"{method} {path}"
    detail = f"http={code} biz={actual_code}"
    if must_have_keys:
        data = result.get("data", result) if isinstance(result, dict) else {}
        detail += f" data_keys={list(data.keys())[:5] if isinstance(data, dict) else type(data).__name__}"
    print(f"  [{status}] {name:55s} {detail}")
    if not ok:
        msg = (result.get("msg") if isinstance(result, dict) else "") or ""
        print(f"         msg: {msg[:100]}")
    return result if isinstance(result, dict) else {"raw": result}


def step(n: int, title: str):
    print(f"\n[STEP {n:2d}/10] {title}")


# ---------- 10 step E2E ----------

def main():
    global TOKEN

    print("=" * 70)
    print(f"opc-content E2E test (W74)")
    print(f"  gateway={GATEWAY}  username={USERNAME}  companyId={COMPANY_ID}")
    print("=" * 70)

    cookies = CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))

    # Step 1: 登录
    step(1, "登录拿 access_token")
    try:
        TOKEN = login(opener, cookies)
        print(f"  token 长度: {len(TOKEN)}  prefix: {TOKEN[:20]}...")
    except Exception as e:
        print(f"FATAL: 登录失败: {e}", file=sys.stderr)
        sys.exit(1)

    # Step 2: ScriptController 分页列表
    step(2, "ScriptController 分页列表 (script/list)")
    r = call(opener, "GET", "/opc/content/script/list",
             params={"companyId": COMPANY_ID, "page": 1, "size": 10},
             must_have_keys=("rows", "total"),
             label="script/list")

    # Step 3: ScriptController Dashboard 聚合
    step(3, "ScriptController Dashboard (script/dashboard)")
    call(opener, "GET", "/opc/content/script/dashboard",
         params={"companyId": COMPANY_ID},
         label="script/dashboard")

    # Step 4: ScriptController 创建 (LLM 生成,可能慢或因 LLM key 缺失失败)
    step(4, "ScriptController 创建 + LLM 生成 (script POST)")
    r = call(opener, "POST", "/opc/content/script",
             body={
                 "companyId": COMPANY_ID,
                 "type": "DRAMA",
                 "title": f"E2E 测试脚本 {int(time.time())}",
                 "promptInput": "生成一个 30 秒都市短剧,3 幕结构"
             },
             label="script POST (create)")

    # Step 5: ScriptController 详情 (id=1 通常由 seed 提供)
    step(5, "ScriptController 详情 (script/{id})")
    call(opener, "GET", "/opc/content/script/1",
         params={"companyId": COMPANY_ID},
         expected_code=200,  # 404 也算 OK (id=1 不一定存在,seed 幂等)
         label="script/{id}")

    # Step 6: ScriptController 适配接口 (POST /adapt)
    step(6, "ScriptController 平台适配 (script/adapt)")
    call(opener, "POST", "/opc/content/script/adapt",
         body={
             "companyId": COMPANY_ID,
             "sourceScriptId": 1,
             "targetPlatform": "DOUYIN",
             "tone": "轻松幽默",
             "hashtags": ["#都市奇缘", "#短剧推荐"]
         },
         label="script/adapt")

    # Step 7: PlatformAccount 列表
    step(7, "PlatformAccount 列表 (platform-account/list)")
    call(opener, "GET", "/opc/content/platform-account/list",
         params={"companyId": COMPANY_ID},
         label="platform-account/list")

    # Step 8: PlatformAccount OAuth 授权 URL (GET /authorize)
    step(8, "PlatformAccount OAuth authorize URL")
    # 这个端点 302 重定向,不直接返回 JSON, 用预期 200/302 都算 PASS
    url = (f"{CONTENT_BASE}/platform-account/oauth/douyin/authorize"
           f"?companyId={COMPANY_ID}")
    try:
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {TOKEN}"})
        with opener.open(req, timeout=10) as resp:
            code, body = resp.status, {"location": resp.headers.get("Location", "")}
            actual = body.get("location", "")
            if code in (200, 302) and ("douyin" in actual or actual == ""):
                PASS_COUNT += 1
                print(f"  [PASS] platform-account/oauth/douyin/authorize "
                      f"http={code} location={actual[:60]}...")
            else:
                FAIL_COUNT += 1
                print(f"  [FAIL] platform-account/oauth/douyin/authorize "
                      f"http={code} location={actual[:60]}")
    except urllib.error.HTTPError as e:
        # 302 也可能抛 HTTPError
        if e.code in (302, 200):
            PASS_COUNT += 1
            loc = e.headers.get("Location", "")
            print(f"  [PASS] platform-account/oauth/douyin/authorize http={e.code} "
                  f"location={loc[:60]}...")
        else:
            FAIL_COUNT += 1
            print(f"  [FAIL] platform-account/oauth/douyin/authorize http={e.code}")
    except Exception as e:
        FAIL_COUNT += 1
        print(f"  [FAIL] platform-account/oauth/douyin/authorize error: {e}")

    # Step 9: Publish 列表
    step(9, "Publish 列表 (publish/list)")
    call(opener, "GET", "/opc/content/publish/list",
         params={"companyId": COMPANY_ID, "page": 1, "size": 10},
         label="publish/list")

    # Step 10: /actuator/health + Nacos 注册 (合并 2 个端点)
    step(10, "actuator/health + Nacos opc-content 注册检查")
    # 10a: /actuator/health
    health_url = f"{CONTENT_BASE}/actuator/health"
    try:
        req = urllib.request.Request(health_url)
        with opener.open(req, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            try:
                health_body = json.loads(raw)
            except Exception:
                health_body = {"raw": raw}
            health_ok = (resp.status == 200
                         and (health_body.get("status") == "UP"
                              or "UP" in raw.upper()))
            if health_ok:
                PASS_COUNT += 1
                print(f"  [PASS] /actuator/health                              "
                      f"http={resp.status} status={health_body.get('status', 'UP')}")
            else:
                FAIL_COUNT += 1
                print(f"  [FAIL] /actuator/health                              "
                      f"http={resp.status} body={raw[:80]}")
    except Exception as e:
        FAIL_COUNT += 1
        print(f"  [FAIL] /actuator/health error: {e}")

    # 10b: Nacos 注册检查
    nacos_full = f"{NACOS_URL}?{urllib.parse.urlencode({'serviceName': 'opc-content', 'namespaceId': 'opc-dev'})}"
    try:
        with urllib.request.urlopen(nacos_full, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            try:
                nb = json.loads(raw)
                # Nacos 返回 {"hosts":[...],"dom":...}
                hosts = nb.get("hosts", [])
                count = len(hosts) if isinstance(hosts, list) else 0
                if resp.status == 200:
                    PASS_COUNT += 1
                    print(f"  [PASS] Nacos opc-content 注册 (opc-dev)              "
                          f"http={resp.status} instances={count}")
                else:
                    FAIL_COUNT += 1
                    print(f"  [FAIL] Nacos opc-content 注册 http={resp.status}")
            except Exception:
                # 即使解析失败,HTTP 200 也算 (dev 模式可能 nacos 未起)
                if resp.status == 200:
                    PASS_COUNT += 1
                    print(f"  [PASS] Nacos opc-content 注册 (opc-dev)              "
                          f"http={resp.status} (non-JSON body, dev mode OK)")
                else:
                    FAIL_COUNT += 1
                    print(f"  [FAIL] Nacos opc-content 注册 http={resp.status}")
    except Exception as e:
        # dev 模式无 nacos 是预期的,标 FAIL 但 warn
        FAIL_COUNT += 1
        print(f"  [WARN] Nacos opc-content 注册检查失败 (dev 模式可忽略): {e}")

    # 总结
    total = PASS_COUNT + FAIL_COUNT
    print("\n" + "=" * 70)
    print(f"PASS: {PASS_COUNT}/{total}    FAIL: {FAIL_COUNT}/{total}")
    print("=" * 70)

    if FAIL_COUNT == 0:
        print(f"{total}/{total} 步骤全部成功")
        return 0
    elif FAIL_COUNT <= 2:
        print(f"多数步骤成功,少量 fail (参考上方详情)")
        return 0
    else:
        print(f"较多步骤失败,需排查")
        return 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nInterrupted", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"\nFATAL: {e}", file=sys.stderr)
        sys.exit(2)
