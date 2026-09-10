#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""End-to-end test for all /opc/** endpoints exposed by frontend.

Run: PYTHONIOENCODING=utf-8 python tmp_e2e/e2e_all.py
Output: tmp_e2e/e2e_report.md
"""
import json as jsonlib
import os
import sys
import urllib.request
import urllib.error
from datetime import date, timedelta

BASE = "http://localhost:8079/prod-api"
USER = "admin"
PASS = "admin123"

results = []  # (group, method, path, status_code, r_code, msg, note)


def call(method, path, token=None, body=None, params=None, files=None, raw=False):
    url = BASE + path
    if params:
        from urllib.parse import urlencode
        url += "?" + urlencode(params)
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        data = jsonlib.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=30)
        body_b = resp.read()
    except urllib.error.HTTPError as e:
        body_b = e.read()
    try:
        d = jsonlib.loads(body_b)
    except Exception:
        d = {"_raw": body_b[:300].decode("utf-8", errors="replace")}
    return resp.status, d


def login():
    s, d = call("POST", "/login", body={"username": USER, "password": PASS})
    if s != 200 or d.get("code") != 200:
        print("LOGIN FAILED:", s, d)
        sys.exit(1)
    return d["data"]["access_token"]


def record(group, method, path, status, body, note=""):
    rc = body.get("code") if isinstance(body, dict) else None
    msg = (body.get("msg") or "") if isinstance(body, dict) else str(body)[:60]
    results.append((group, method, path, status, rc, msg[:60], note))
    mark = "OK" if (status == 200 and rc == 200) else "FAIL"
    print(f"  [{mark}] {method:6s} {path:55s} HTTP={status} R={rc} {msg[:50]}")


def main():
    token = login()
    print(f"=== TOKEN OK (len={len(token)}) ===\n")

    today = date.today()
    today_s = today.isoformat()
    month_start = today.replace(day=1).isoformat()

    # ============ USER CENTER ============
    print("\n--- USER CENTER ---")
    s, d = call("GET", "/opc/user/profile", token=token); record("user", "GET", "/opc/user/profile", s, d)
    s, d = call("POST", "/opc/user/profile", token=token, body={"nickname":"e2e","industry":"ECOM"}); record("user", "POST", "/opc/user/profile", s, d)
    s, d = call("GET", "/opc/user/companies", token=token); record("user", "GET", "/opc/user/companies", s, d)
    s, d = call("GET", "/opc/user/company/1", token=token); record("user", "GET", "/opc/user/company/1", s, d)
    s, d = call("PUT", "/opc/user/company", token=token, body={"id":1,"introduction":"e2e updated"}); record("user", "PUT", "/opc/user/company", s, d)
    s, d = call("GET", "/opc/user/home", token=token); record("user", "GET", "/opc/user/home", s, d)
    s, d = call("POST", "/opc/user/company", token=token, body={"companyName":"e2e测试公司","companyType":"INDIVIDUAL","industryName":"测试","province":"北京","city":"北京","phone":"13900139000"}); record("user", "POST", "/opc/user/company (create)", s, d)
    s, d = call("GET", "/opc/user/invitations", token=token); record("user", "GET", "/opc/user/invitations", s, d)
    s, d = call("POST", "/opc/user/invitations/generate", token=token); record("user", "POST", "/opc/user/invitations/generate", s, d, note="creates invite")

    # ============ AGENT HUB ============
    print("\n--- AGENT HUB ---")
    s, d = call("GET", "/opc/agent/market", token=token); record("agent", "GET", "/opc/agent/market", s, d)
    for cat in ["FINANCE", "ERP", "CRM", "HR", "ECOM", "CONTENT", "INSIGHT"]:
        s, d = call("GET", "/opc/agent/market", token=token, params={"category": cat}); record("agent", "GET", f"/opc/agent/market?category={cat}", s, d)
    s, d = call("GET", "/opc/agent/detail/1", token=token); record("agent", "GET", "/opc/agent/detail/1", s, d)
    s, d = call("GET", "/opc/agent/detail/4", token=token); record("agent", "GET", "/opc/agent/detail/4", s, d)
    s, d = call("GET", "/opc/agent/instances", token=token); record("agent", "GET", "/opc/agent/instances", s, d)

    # Hire (use existing company 1 + definition 1 to avoid duplicate trial conflicts)
    s, d = call("POST", "/opc/agent/hire", token=token, body={"companyId":1,"definitionId":1,"hireType":"TRIAL","duration":7,"nickname":"e2e-test"}); record("agent", "POST", "/opc/agent/hire", s, d, note="creates instance")
    inst_id = None
    if isinstance(d, dict) and d.get("data"):
        inst_id = d["data"].get("id") or (d["data"][0].get("id") if isinstance(d["data"], list) else None)
    if inst_id:
        s, d = call("GET", f"/opc/agent/instance/{inst_id}", token=token); record("agent", "GET", f"/opc/agent/instance/{inst_id}", s, d)
        s, d = call("GET", f"/opc/agent/instance/{inst_id}/tasks", token=token); record("agent", "GET", f"/opc/agent/instance/{inst_id}/tasks", s, d)
        s, d = call("GET", f"/opc/agent/instance/{inst_id}/usage", token=token); record("agent", "GET", f"/opc/agent/instance/{inst_id}/usage", s, d)
        s, d = call("POST", f"/opc/agent/instance/{inst_id}/action", token=token, params={"action":"PAUSE"}); record("agent", "POST", f"/opc/agent/instance/{inst_id}/action?action=PAUSE", s, d)
        s, d = call("POST", f"/opc/agent/instance/{inst_id}/action", token=token, params={"action":"RESUME"}); record("agent", "POST", f"/opc/agent/instance/{inst_id}/action?action=RESUME", s, d)

    s, d = call("GET", "/opc/agent/usage/summary", token=token, params={"companyId":1,"bizDate":today_s}); record("agent", "GET", "/opc/agent/usage/summary", s, d)
    s, d = call("GET", "/opc/agent/usage/daily", token=token, params={"companyId":1,"startDate":month_start,"endDate":today_s}); record("agent", "GET", "/opc/agent/usage/daily", s, d)
    s, d = call("POST", "/opc/agent/task/run", token=token, body={"instanceId":inst_id or 1,"taskCode":"DAILY_REPORT"}); record("agent", "POST", "/opc/agent/task/run", s, d)

    # ============ BILLING ============
    print("\n--- BILLING ---")
    s, d = call("GET", "/opc/billing/wallet", token=token, params={"companyId":1}); record("billing", "GET", "/opc/billing/wallet", s, d)
    s, d = call("GET", "/opc/billing/orders", token=token, params={"companyId":1,"limit":10}); record("billing", "GET", "/opc/billing/orders", s, d)
    s, d = call("POST", "/opc/billing/wallet/recharge", token=token, body={"companyId":1,"amount":100,"payMethod":"ALIPAY"}); record("billing", "POST", "/opc/billing/wallet/recharge", s, d, note="creates order")
    order_no = None
    if isinstance(d, dict) and d.get("data") and isinstance(d["data"], dict):
        order_no = d["data"].get("orderNo")
    if order_no:
        s, d = call("GET", f"/opc/billing/order/{order_no}", token=token); record("billing", "GET", f"/opc/billing/order/{order_no}", s, d)

    # ============ FINANCE ============
    print("\n--- FINANCE ---")
    s, d = call("GET", "/opc/finance/vouchers", token=token, params={"companyId":1,"limit":10}); record("finance", "GET", "/opc/finance/vouchers", s, d)
    # Create voucher (needs review pass + post flow)
    s, d = call("POST", "/opc/finance/voucher", token=token, body={"companyId":1,"summary":"e2e测试凭证","totalDebit":100,"totalCredit":100,"entriesJson":"[{\"subject\":\"6601\",\"direction\":\"借\",\"amount\":100}]","voucherDate":today_s}); record("finance", "POST", "/opc/finance/voucher (create)", s, d)
    vid = None
    if isinstance(d, dict) and d.get("data"):
        vid = d["data"].get("id") if isinstance(d["data"], dict) else None
    if vid:
        s, d = call("GET", f"/opc/finance/voucher/{vid}", token=token); record("finance", "GET", f"/opc/finance/voucher/{vid}", s, d)
        s, d = call("PUT", "/opc/finance/voucher", token=token, body={"id":vid,"summary":"e2e更新摘要","amount":150}); record("finance", "PUT", "/opc/finance/voucher", s, d)
        s, d = call("POST", f"/opc/finance/voucher/{vid}/review-pass", token=token); record("finance", "POST", f"/opc/finance/voucher/{vid}/review-pass", s, d)
        s, d = call("POST", f"/opc/finance/voucher/{vid}/post", token=token); record("finance", "POST", f"/opc/finance/voucher/{vid}/post", s, d)

    s, d = call("GET", "/opc/finance/flows/pending", token=token, params={"companyId":1,"limit":10}); record("finance", "GET", "/opc/finance/flows/pending", s, d)
    s, d = call("POST", "/opc/finance/flows/extract", token=token, params={"companyId":1}); record("finance", "POST", "/opc/finance/flows/extract", s, d)
    # flows/upload 接收 List<OpcFinanceBankFlow>,前端 flows.vue 就是发 List
    flows_payload = [
        {"companyId":1,"flowCode":"F20260910001","tradeTime":"2026-09-10 10:00:00","direction":"IN","amount":1000.00,"counterParty":"客户A","bankName":"手动","bankAccount":"-","currency":"CNY","memo":"e2e测试收入","status":"PENDING"},
        {"companyId":1,"flowCode":"F20260910002","tradeTime":"2026-09-10 11:00:00","direction":"OUT","amount":500.00,"counterParty":"供应商B","bankName":"手动","bankAccount":"-","currency":"CNY","memo":"e2e测试支出","status":"PENDING"},
    ]
    s, d = call("POST", "/opc/finance/flows/upload", token=token, body=flows_payload); record("finance", "POST", "/opc/finance/flows/upload", s, d, note="batch upload 2 flows")

    s, d = call("GET", "/opc/finance/tax-reports", token=token, params={"companyId":1,"limit":5}); record("finance", "GET", "/opc/finance/tax-reports", s, d)
    s, d = call("POST", "/opc/finance/tax-reports/generate", token=token, params={"companyId":1,"period":today_s[:7]}); record("finance", "POST", "/opc/finance/tax-reports/generate", s, d, note="creates tax report")
    tid = None
    if isinstance(d, dict) and d.get("data") and isinstance(d["data"], dict):
        tid = d["data"].get("id")
    if tid:
        s, d = call("GET", f"/opc/finance/tax-reports/{tid}", token=token); record("finance", "GET", f"/opc/finance/tax-reports/{tid}", s, d)

    s, d = call("POST", "/opc/finance/daily-report", token=token, params={"companyId":1}); record("finance", "POST", "/opc/finance/daily-report", s, d)

    # ============ INSIGHT ============
    print("\n--- INSIGHT ---")
    s, d = call("GET", "/opc/insight/dashboard", token=token); record("insight", "GET", "/opc/insight/dashboard", s, d)
    s, d = call("GET", "/opc/insight/alerts", token=token, params={"limit":5}); record("insight", "GET", "/opc/insight/alerts", s, d)
    s, d = call("GET", "/opc/insight/advice", token=token, params={"limit":5}); record("insight", "GET", "/opc/insight/advice", s, d)
    s, d = call("GET", "/opc/insight/daily", token=token, params={"from":month_start,"to":today_s,"limit":5}); record("insight", "GET", "/opc/insight/daily", s, d)
    s, d = call("POST", "/opc/insight/daily/generate", token=token, params={"date":today_s}); record("insight", "POST", "/opc/insight/daily/generate", s, d, note="creates daily report")
    did = None
    if isinstance(d, dict) and d.get("data") and isinstance(d["data"], dict):
        did = d["data"].get("id")
    if did:
        s, d = call("GET", f"/opc/insight/daily/{did}", token=token); record("insight", "GET", f"/opc/insight/daily/{did}", s, d)
    aid = None
    if isinstance(d, dict) and d.get("data") and isinstance(d["data"], list) and len(d["data"]) > 0:
        # need to fetch advice list to get an advice id
        pass
    s2, d2 = call("GET", "/opc/insight/advice", token=token, params={"limit":1}); record("insight", "GET", "/opc/insight/advice (for id)", s2, d2)
    if isinstance(d2, dict) and d2.get("data") and isinstance(d2["data"], list) and len(d2["data"]) > 0:
        aid = d2["data"][0].get("id")
    if aid:
        s, d = call("GET", f"/opc/insight/advice/{aid}", token=token); record("insight", "GET", f"/opc/insight/advice/{aid}", s, d)
        s, d = call("POST", f"/opc/insight/advice/{aid}/regenerate", token=token); record("insight", "POST", f"/opc/insight/advice/{aid}/regenerate", s, d)
    # alerts ack
    s2, d2 = call("GET", "/opc/insight/alerts", token=token, params={"limit":1}); record("insight", "GET", "/opc/insight/alerts (for id)", s2, d2)
    if isinstance(d2, dict) and d2.get("data") and isinstance(d2["data"], list) and len(d2["data"]) > 0:
        anid = d2["data"][0].get("id")
        if anid:
            s, d = call("POST", f"/opc/insight/alerts/{anid}/ack", token=token); record("insight", "POST", f"/opc/insight/alerts/{anid}/ack", s, d)

    # ============ LLM ============
    print("\n--- LLM ---")
    s, d = call("GET", "/opc/llm/models", token=token); record("llm", "GET", "/opc/llm/models", s, d)
    s, d = call("POST", "/opc/llm/chat", token=token, body={"messages":[{"role":"user","content":"hello e2e"}]}); record("llm", "POST", "/opc/llm/chat", s, d, note="EXPECTED FAIL: no real OpenAI key, LLM provider fallback fails")

    # ============ AGGREGATE (W48.5: 这些是 INSIGHT 服务间 Feign 调用,有 @InnerAuth,前端不直接 hit) ============
    print("\n--- AGGREGATE (service-to-service only, expected fail from external) ---")
    # SKIP — these have @InnerAuth require from-source:inner header, blocked from public gateway.
    # Frontend does NOT call these. Tested via Insight Feign only.

    # ============ WORKFLOW trigger (Feign inside) ============
    # workflowCronJob runs from quartz, but we can hit the trigger endpoint directly if @InnerAuth
    # SKIP: requires from-source:inner header

    # ============ REPORT ============
    print("\n" + "="*60)
    print("SUMMARY")
    print("="*60)
    fails = [r for r in results if r[3] != 200 or r[4] != 200]
    print(f"Total: {len(results)}  Pass: {len(results)-len(fails)}  Fail: {len(fails)}\n")
    if fails:
        print("FAILURES:")
        for r in fails:
            print(f"  {r[1]:6s} {r[2]:55s} HTTP={r[3]} R={r[4]} {r[5][:50]} {r[6]}")

    # write markdown
    with open("tmp_e2e/e2e_report.md", "w", encoding="utf-8") as f:
        f.write(f"# E2E 报告 ({today_s})\n\n")
        f.write(f"**Total**: {len(results)} | **Pass**: {len(results)-len(fails)} | **Fail**: {len(fails)}\n\n")
        f.write("| Group | Method | Path | HTTP | R-code | Msg | Note |\n|---|---|---|---|---|---|---|\n")
        for r in results:
            mark = "✅" if (r[3]==200 and r[4]==200) else "❌"
            f.write(f"| {r[0]} | {r[1]} | `{r[2]}` | {r[3]} | {r[4]} | {r[5][:40]} | {r[6]} {mark} |\n")
    print("\nReport written to tmp_e2e/e2e_report.md")


if __name__ == "__main__":
    main()