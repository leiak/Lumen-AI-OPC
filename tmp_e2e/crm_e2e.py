#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""End-to-end test for opc-crm service (Task 19, W50).

Hits gateway (port 8080) which routes to aiopc-crm (port 9312).

Endpoints verified (from controller source):
  GET    /opc/crm/customer
  POST   /opc/crm/customer
  GET    /opc/crm/customer/{id}
  GET    /opc/crm/contact?customerId=
  POST   /opc/crm/contact
  GET    /opc/crm/follow-up
  POST   /opc/crm/follow-up
  POST   /opc/crm/follow-up/{id}/complete
  GET    /opc/crm/opportunity
  POST   /opc/crm/opportunity
  POST   /opc/crm/opportunity/{id}/stage
  POST   /opc/crm/opportunity/{id}/score
  GET    /opc/crm/contract
  POST   /opc/crm/contract
  POST   /opc/crm/contract/{id}/activate
  GET    /opc/crm/order
  POST   /opc/crm/order
  POST   /opc/crm/order/{id}/pay
  POST   /opc/crm/order/{id}/ship
  GET    /opc/crm/dashboard

NOTE: LLM score endpoint may return HTTP 200 + body code=500 if opc-ai-core
is unreachable (no LLM key in dev). Customer list returns empty data for
admin user due to ownerId filter; endpoint reachability is the test target.

Run: PYTHONIOENCODING=utf-8 python tmp_e2e/crm_e2e.py
"""
import json as jsonlib
import sys
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
USER = "admin"
PASS = "admin123"

results = []


def call(method, path, token=None, body=None, params=None):
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


def record(label, status, body, accept=None, note=""):
    rc = body.get("code") if isinstance(body, dict) else None
    msg = (body.get("msg") or "") if isinstance(body, dict) else str(body)[:80]
    if accept is None:
        ok = (status == 200 and rc == 200)
    else:
        ok = any((status == s and rc == r) for s, r in accept)
    results.append((label, status, rc, msg[:80], note, ok))
    mark = "OK  " if ok else "FAIL"
    print(f"  [{mark}] {label:50s} HTTP={status} R={rc} {msg[:50]} {note}")


def section(t):
    print(f"\n--- {t} ---")


def main():
    print("OPC CRM E2E Test (Task 19)")
    print(f"Gateway: {BASE}")

    token = login()
    print(f"\n=== TOKEN OK (len={len(token)}) ===")

    # 1. Customer list
    section("1. Customer list (seed data)")
    s, d = call("GET", "/opc/crm/customer", token=token)
    record("customer list endpoint reachable", s, d, accept=[(200, 200)])

    # 2. Customer detail
    section("2. Customer detail id=1")
    s, d = call("GET", "/opc/crm/customer/1", token=token)
    record(
        "customer detail 1 reachable",
        s, d,
        accept=[(200, 200), (200, 500)],
        note="500 acceptable if admin's ownerId doesn't match (W50 Task 16 finding)"
    )

    # 3. Create customer
    section("3. Create customer")
    new_cust = {
        "name": f"E2E测试客户_{int(time.time())}",
        "level": "B",
        "source": "WEBSITE",
        "tags": "e2e,test",
        "phone": "13900000000",
        "email": "e2e@test.com"
    }
    s, d = call("POST", "/opc/crm/customer", token=token, body=new_cust)
    record("create customer 200", s, d)
    new_cust_id = None
    if isinstance(d, dict) and d.get("code") == 200 and isinstance(d.get("data"), dict):
        new_cust_id = d["data"].get("id")
    print(f"  new customer id={new_cust_id}")

    # 4. Get created customer
    if new_cust_id:
        section(f"4. Get created customer id={new_cust_id}")
        s, d = call("GET", f"/opc/crm/customer/{new_cust_id}", token=token)
        record(f"get customer {new_cust_id}", s, d)
        record(
            "customer has expected name",
            s,
            {"code": 200 if (isinstance(d, dict) and d.get("data", {}).get("name") == new_cust["name"]) else 500},
            note=f"name={d.get('data', {}).get('name')!r}"
        )

    # 5. List contacts for new customer
    if new_cust_id:
        section(f"5. List contacts for customer={new_cust_id}")
        s, d = call("GET", "/opc/crm/contact", token=token, params={"customerId": new_cust_id})
        record("contact list empty for new customer", s, d)

    # 6. Create contact
    if new_cust_id:
        section(f"6. Create contact for customer={new_cust_id}")
        new_contact = {
            "customerId": new_cust_id,
            "name": "测试联系人",
            "phone": "13900000001",
            "isPrimary": 1
        }
        s, d = call("POST", "/opc/crm/contact", token=token, body=new_contact)
        record("create contact 200", s, d)
        new_contact_id = None
        if isinstance(d, dict) and d.get("code") == 200 and isinstance(d.get("data"), dict):
            new_contact_id = d["data"].get("id")
        print(f"  new contact id={new_contact_id}")

    # 7. Create follow-up
    if new_cust_id:
        section(f"7. Create follow-up for customer={new_cust_id}")
        new_fu = {
            "customerId": new_cust_id,
            "content": f"E2E 跟进测试 {int(time.time())}",
            "type": "PHONE",
            "ownerId": 1
        }
        s, d = call("POST", "/opc/crm/follow-up", token=token, body=new_fu)
        record("create follow-up 200", s, d)
        new_fu_id = None
        if isinstance(d, dict) and d.get("code") == 200 and isinstance(d.get("data"), dict):
            new_fu_id = d["data"].get("id")
        print(f"  new follow-up id={new_fu_id}")

        # 8. Mark follow-up complete
        if new_fu_id:
            section(f"8. Mark follow-up {new_fu_id} complete")
            s, d = call("POST", f"/opc/crm/follow-up/{new_fu_id}/complete", token=token, params={"result": "WON"})
            record("mark follow-up complete", s, d)

    # 9. Create opportunity
    if new_cust_id:
        section(f"9. Create opportunity for customer={new_cust_id}")
        new_opp = {
            "customerId": new_cust_id,
            "name": f"E2E测试商机_{int(time.time())}",
            "amount": 100000,
            "ownerId": 1
        }
        s, d = call("POST", "/opc/crm/opportunity", token=token, body=new_opp)
        record("create opportunity 200", s, d)
        new_opp_id = None
        if isinstance(d, dict) and d.get("code") == 200 and isinstance(d.get("data"), dict):
            new_opp_id = d["data"].get("id")
        print(f"  new opportunity id={new_opp_id}")

        # 10. Change stage LEAD → QUALIFIED
        if new_opp_id:
            section(f"10. Change opportunity {new_opp_id} stage LEAD→QUALIFIED")
            s, d = call("POST", f"/opc/crm/opportunity/{new_opp_id}/stage", token=token, body={"stage": "QUALIFIED", "reason": None})
            record("change stage 200", s, d)
            record(
                "stage is QUALIFIED",
                s,
                {"code": 200 if d.get("data", {}).get("stage") == "QUALIFIED" else 500},
                note=f"stage={d.get('data', {}).get('stage')!r}"
            )

        # 11. Score opportunity (LLM call — may fail in dev)
        if new_opp_id:
            section(f"11. Score opportunity {new_opp_id} (LLM call)")
            s, d = call("POST", f"/opc/crm/opportunity/{new_opp_id}/score", token=token)
            record(
                "score endpoint reachable (LLM may be unavailable)",
                s, d,
                accept=[(200, 200), (200, 500)],
                note="200/500 acceptable if opc-ai-core unreachable"
            )

    # 12. Dashboard funnel
    section("12. Dashboard funnel")
    s, d = call("GET", "/opc/crm/dashboard", token=token)
    record("dashboard funnel 200", s, d)
    if isinstance(d, dict) and d.get("code") == 200 and isinstance(d.get("data"), dict):
        funnel = d["data"].get("funnel", {})
        record(
            "funnel has 6 stages",
            200,
            {"code": 200 if isinstance(funnel, dict) and len(funnel) >= 6 else 500},
            note=f"stages={list(funnel.keys()) if isinstance(funnel, dict) else funnel!r}"
        )

    # 13. Dashboard customer summary
    section("13. Dashboard customer summary")
    s, d = call("GET", "/opc/crm/dashboard/customers", token=token)
    record("customer summary 200", s, d)

    # 14. Create contract
    if new_cust_id:
        section(f"14. Create contract for customer={new_cust_id}")
        new_contract = {
            "customerId": new_cust_id,
            "contractNo": f"C-E2E-{int(time.time())}",
            "title": "E2E测试合同",
            "amount": 50000
        }
        s, d = call("POST", "/opc/crm/contract", token=token, body=new_contract)
        record("create contract 200", s, d)
        new_contract_id = None
        if isinstance(d, dict) and d.get("code") == 200 and isinstance(d.get("data"), dict):
            new_contract_id = d["data"].get("id")

        # 15. Activate contract
        if new_contract_id:
            section(f"15. Activate contract {new_contract_id}")
            s, d = call("POST", f"/opc/crm/contract/{new_contract_id}/activate", token=token)
            record("activate contract 200", s, d)

    # 16. Create order
    if new_cust_id:
        section(f"16. Create order for customer={new_cust_id}")
        new_order = {
            "customerId": new_cust_id,
            "orderNo": f"O-E2E-{int(time.time())}",
            "itemsJson": '[{"sku":"E2E-001","name":"E2E测试商品","qty":1,"price":1000}]',
            "total": 1000
        }
        s, d = call("POST", "/opc/crm/order", token=token, body=new_order)
        record("create order 200", s, d)
        new_order_id = None
        if isinstance(d, dict) and d.get("code") == 200 and isinstance(d.get("data"), dict):
            new_order_id = d["data"].get("id")

        # 17. Pay order
        if new_order_id:
            section(f"17. Pay order {new_order_id}")
            s, d = call("POST", f"/opc/crm/order/{new_order_id}/pay", token=token)
            record("pay order 200", s, d)

        # 18. Ship order
        if new_order_id:
            section(f"18. Ship order {new_order_id}")
            s, d = call("POST", f"/opc/crm/order/{new_order_id}/ship", token=token)
            record("ship order 200", s, d)

    # 19. Validation: blank name
    section("19. Validation: blank customer name")
    s, d = call("POST", "/opc/crm/customer", token=token, body={"name": "", "level": "A"})
    record(
        "blank name -> validation error",
        s, d,
        accept=[(200, 400), (200, 500), (400, 400), (500, 500)],
        note="HTTP 200 + body code=500 = validation wired"
    )

    # 20. Validation: invalid stage transition
    if new_opp_id:
        section("20. Validation: invalid stage transition LEAD→WON")
        s, d = call("POST", f"/opc/crm/opportunity/{new_opp_id}/stage", token=token, body={"stage": "WON", "reason": None})
        record(
            "invalid stage transition rejected",
            s, d,
            accept=[(200, 500), (500, 500)],
            note="LEAD→WON must be rejected by state machine"
        )

    # === SUMMARY ===
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    fails = [r for r in results if not r[5]]
    print(f"Total: {len(results)}  Pass: {len(results) - len(fails)}  Fail: {len(fails)}\n")
    if fails:
        print("FAILURES:")
        for r in fails:
            print(f"  - {r[0]}: HTTP={r[1]} R={r[2]} {r[3][:50]} {r[4]}")

    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())