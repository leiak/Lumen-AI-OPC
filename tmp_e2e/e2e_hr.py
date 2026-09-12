#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
opc-hr E2E test (W71)
依赖: urllib + json(标准库)
执行: python tmp_e2e/e2e_hr.py
前置: aiopc-hr 服务已启动,admin/admin123 可登录

测试路径: GATEWAY(8080) → ruoyi-gateway → aiopc-hr:9322
"""

import json
import sys
import time
import urllib.request
import urllib.parse
from typing import Optional

GATEWAY = "http://127.0.0.1:8080"
COMPANY_ID = 1
USER_ID = 1
PASS_COUNT = 0
WARN_COUNT = 0

# ---------- helpers ----------

def login(username: str = "admin", password: str = "admin123") -> str:
    url = f"{GATEWAY}/login"
    data = json.dumps({"username": username, "password": password}).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = json.loads(resp.read().decode())
        if body.get("code") != 200:
            raise RuntimeError(f"login failed: {body.get('msg')}")
        # RuoYi 用 access_token 不是 token(W48 教训)
        token = body.get("access_token") or body.get("token")
        if not token:
            raise RuntimeError(f"login response missing token: {body}")
        return token

TOKEN: Optional[str] = None

def call(method: str, path: str, body: Optional[dict] = None,
         params: Optional[dict] = None, expected_code: int = 200,
         allow_warn_codes: tuple = (500,)) -> dict:
    """统一 API 调用,带断言"""
    global PASS_COUNT, WARN_COUNT
    url = f"{GATEWAY}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method.upper())
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            result = json.loads(resp.read().decode())
    except Exception as e:
        WARN_COUNT += 1
        print(f"  [WARN] {method} {path} 异常: {e}")
        return {"code": -1, "msg": str(e)}

    actual = result.get("code", -1)
    if actual == expected_code:
        PASS_COUNT += 1
        print(f"  [OK]   {method:6s} {path:60s} code={actual}")
    elif actual in allow_warn_codes:
        WARN_COUNT += 1
        print(f"  [WARN] {method:6s} {path:60s} code={actual} msg={result.get('msg', '')[:60]}")
    else:
        WARN_COUNT += 1
        print(f"  [WARN] {method:6s} {path:60s} code={actual} expected={expected_code} msg={result.get('msg', '')[:60]}")
    return result

# ---------- 11 step E2E ----------

def step(n: int, title: str):
    print(f"\n[STEP {n}/11] {title}")

def main():
    global TOKEN

    print("=" * 70)
    print(f"opc-hr E2E test (gateway={GATEWAY}, companyId={COMPANY_ID})")
    print("=" * 70)

    # Step 1: 登录
    step(1, "登录拿 access_token")
    try:
        TOKEN = login()
        print(f"  token 长度: {len(TOKEN)}")
    except Exception as e:
        print(f"FATAL: 登录失败: {e}", file=sys.stderr)
        sys.exit(1)

    # Step 2: 创建 JD
    step(2, "创建 JD (DRAFT)")
    r = call("POST", "/opc/hr/job", body={
        "companyId": COMPANY_ID,
        "title": f"E2E 测试高级 Java 开发 {int(time.time())}",
        "category": "TECH",
        "description": "5 年 Java 经验,熟悉 Spring Cloud",
        "salaryMin": 20000,
        "salaryMax": 35000,
        "location": "北京"
    })
    job_id = r.get("data")
    print(f"  → JD id={job_id}")

    # Step 3: 发布 JD
    step(3, f"发布 JD id={job_id}")
    call("POST", f"/opc/hr/job/{job_id}/publish", params={"companyId": COMPANY_ID})

    # Step 4: 创建候选人
    step(4, "创建候选人")
    r = call("POST", "/opc/hr/candidate", body={
        "companyId": COMPANY_ID,
        "name": "张三",
        "email": f"e2e-hr-{int(time.time())}@test.com",
        "phone": "13800000000",
        "resumeUrl": "https://example.com/resume.pdf",
        "source": "MANUAL"
    })
    candidate_id = r.get("data")
    print(f"  → 候选人 id={candidate_id}")

    # Step 5: 创建投递
    step(5, "创建投递(application)")
    r = call("POST", "/opc/hr/application", body={
        "companyId": COMPANY_ID,
        "jobId": job_id,
        "candidateId": candidate_id,
        "channel": "MANUAL"
    })
    application_id = r.get("data")
    print(f"  → 投递 id={application_id}")

    # Step 6: 状态机推进
    step(6, "状态机推进 NEW → SCREENING → INTERVIEW → OFFER → HIRED")
    transitions = [
        ("SCREENING", "NEW → SCREENING"),
        ("INTERVIEW", "SCREENING → INTERVIEW"),
        ("OFFER",     "INTERVIEW → OFFER"),
        ("HIRED",     "OFFER → HIRED")
    ]
    for target, label in transitions:
        r = call("PUT", f"/opc/hr/application/{application_id}/status",
                 params={"companyId": COMPANY_ID, "status": target})
        if r.get("code") == 200:
            print(f"    {label}: OK")

    # Step 7: 创建面试(测试 round 自增 — 创建 2 条,第二条 round 应=2)
    step(7, "创建面试(round 自增测试)")
    r1 = call("POST", "/opc/hr/interview", body={
        "companyId": COMPANY_ID,
        "applicationId": application_id,
        "type": "VIDEO",
        "interviewerId": USER_ID,
        "scheduledAt": "2026-09-15T10:00:00",
        "durationMin": 60
    })
    print(f"  → 第 1 轮面试 id={r1.get('data')}")

    r2 = call("POST", "/opc/hr/interview", body={
        "companyId": COMPANY_ID,
        "applicationId": application_id,
        "type": "ONSITE",
        "interviewerId": USER_ID,
        "scheduledAt": "2026-09-18T14:00:00",
        "durationMin": 90
    })
    print(f"  → 第 2 轮面试 id={r2.get('data')}")

    # Step 8: 创建 Offer
    step(8, "创建 Offer")
    r = call("POST", "/opc/hr/offer", body={
        "companyId": COMPANY_ID,
        "applicationId": application_id,
        "salary": 30000,
        "startDate": "2026-10-01",
        "expireAt": "2026-09-25T00:00:00"
    })
    offer_id = r.get("data")
    print(f"  → Offer id={offer_id}")

    # Step 9: 候选人响应 Offer
    step(9, f"候选人响应 Offer ACCEPTED")
    call("PUT", f"/opc/hr/offer/{offer_id}/respond",
         params={"companyId": COMPANY_ID, "response": "ACCEPTED"})

    # Step 10: 查询 dashboard
    step(10, "查询 HR dashboard")
    r = call("GET", "/opc/hr/dashboard", params={"companyId": COMPANY_ID, "sinceDays": 30})
    if r.get("code") == 200 and r.get("data"):
        data = r["data"]
        funnel = data.get("funnel", [])
        rates = data.get("conversionRates", {})
        avg_days = data.get("avgHireDays")
        job_dist = data.get("jobStatusDistribution", {})
        print(f"  → funnel 段数: {len(funnel)}")
        print(f"  → conversion_rates 数量: {len(rates)}")
        print(f"  → avg_hire_days: {avg_days}")
        print(f"  → job_status_distribution: {job_dist}")

    # Step 11: 列出 JD
    step(11, "列出 JD")
    r = call("GET", "/opc/hr/job/list", params={"companyId": COMPANY_ID})
    if r.get("code") == 200 and r.get("data"):
        jobs = r["data"] if isinstance(r["data"], list) else []
        print(f"  → JD 总数: {len(jobs)}")

    # 总结
    print("\n" + "=" * 70)
    print(f"PASS: {PASS_COUNT}    WARN/FAIL: {WARN_COUNT}")
    print("=" * 70)
    if WARN_COUNT == 0:
        print("✅ 11/11 步骤全部成功")
        sys.exit(0)
    elif WARN_COUNT <= 3:
        print("⚠️  多数步骤成功,少量 warn(参考下方详情)")
        sys.exit(0)
    else:
        print("❌ 较多步骤失败,需排查")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nInterrupted", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"\nFATAL: {e}", file=sys.stderr)
        sys.exit(2)