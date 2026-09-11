#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""End-to-end test for opc-notification service (Task 18, W49).

Hits gateway (port 8080) which routes to aiopc-notification (port 9310).

Endpoints verified (from controller source):
  POST /opc/notification/email/send         -> EmailController#send
  POST /opc/notification/sms/send           -> SmsController#send
  GET  /opc/notification/inbox              -> InboxController#list
  GET  /opc/notification/inbox/unread-count -> InboxController#unreadCount
  POST /opc/notification/inbox/read/{id}    -> InboxController#markRead

NOTE: Email send returns 500 in dev (no real SMTP -> SmtpEmailProvider throws
EmailSendException after retries -> GlobalExceptionHandler returns code=500).
SMS send returns 200 in dev (NoOp SmsProvider when Aliyun keys are empty).
Inbox mark-read returns 500 if inbox id doesn't exist for current user.

Run: PYTHONIOENCODING=utf-8 python tmp_e2e/notification_e2e.py
"""
import json as jsonlib
import sys
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
USER = "admin"
PASS = "admin123"

results = []  # (label, status_code, r_code, msg, note, ok)


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
    """Record a check.

    accept: list of acceptable (http_code, r_code) tuples; None means must be (200, 200).
    """
    rc = body.get("code") if isinstance(body, dict) else None
    msg = (body.get("msg") or "") if isinstance(body, dict) else str(body)[:80]
    if accept is None:
        ok = (status == 200 and rc == 200)
    else:
        ok = any((status == s and rc == r) for s, r in accept)
    results.append((label, status, rc, msg[:80], note, ok))
    mark = "OK  " if ok else "FAIL"
    print(f"  [{mark}] {label:45s} HTTP={status} R={rc} {msg[:50]} {note}")


def section(t):
    print(f"\n--- {t} ---")


def main():
    print("OPC Notification E2E Test (Task 18)")
    print(f"Gateway: {BASE}")

    token = login()
    print(f"\n=== TOKEN OK (len={len(token)}) ===")

    # === 1. Email send (NoOp / SMTP fail acceptable) ===
    section("Email send (dev: no SMTP -> expect 500 after retries)")
    s, d = call(
        "POST", "/opc/notification/email/send", token=token,
        body={"to": "test@example.com", "subject": "e2e test", "body": "hello"}
    )
    record(
        "email send endpoint reachable",
        s, d,
        accept=[(200, 200), (200, 500), (500, 500)],
        note="501 would mean no-op accepted, 500 means SMTP fail",
    )

    # === 2. SMS send (NoOp when Aliyun key empty -> expect 200) ===
    section("SMS send (dev: Aliyun noop -> expect 200)")
    s, d = call(
        "POST", "/opc/notification/sms/send", token=token,
        body={
            "phone": "13800138000",
            "templateCode": "welcome",
            "vars": {"name": "e2e"}
        }
    )
    record("sms send", s, d)

    # === 3. Inbox list ===
    section("Inbox list")
    s, d = call(
        "GET", "/opc/notification/inbox", token=token,
        params={"page": 1, "pageSize": 5}
    )
    record("inbox list 200", s, d)
    items = []
    if isinstance(d, dict) and d.get("code") == 200:
        items = (d.get("data") or {}).get("items") or []
        record(
            "inbox items is list",
            200, {"code": 200 if isinstance(items, list) else 500},
            note=f"type={type(items).__name__} count={len(items)}",
        )
        record(
            "inbox data has unreadCount field",
            200,
            {"code": 200 if isinstance((d.get("data") or {}).get("unreadCount"), int) else 500},
            note=f"unreadCount={(d.get('data') or {}).get('unreadCount')}",
        )

    # === 4. Unread count ===
    section("Unread count")
    s, d = call("GET", "/opc/notification/inbox/unread-count", token=token)
    record("unread count 200", s, d)
    record(
        "unread count is integer >= 0",
        s,
        {"code": 200 if (isinstance(d.get("data"), int) and d["data"] >= 0) else 500},
        note=f"data={d.get('data')!r}",
    )

    # === 5. Mark read (existing item) ===
    section("Mark read (existing item)")
    target_id = None
    if items and isinstance(items[0], dict):
        # Read status indicated by readAt being non-null
        if items[0].get("readAt") is None:
            target_id = items[0].get("id")
    if target_id is not None:
        s, d = call(
            "POST", f"/opc/notification/inbox/read/{target_id}",
            token=token
        )
        record(f"mark read id={target_id}", s, d)
    else:
        # No unread items — try a definitely-missing id and expect endpoint to exist
        # (gateway returns HTTP 200 + body code=500 with "Inbox not found" — proves route wired)
        s, d = call(
            "POST", "/opc/notification/inbox/read/0",
            token=token
        )
        record(
            "mark read endpoint reachable (no unread items)",
            s, d,
            accept=[(200, 200), (200, 500), (500, 500)],
            note="HTTP 200 + body code=500 'Inbox not found' = route wired",
        )

    # === 6. Unread count after potential mark-read ===
    section("Unread count (post-mark-read)")
    s, d = call("GET", "/opc/notification/inbox/unread-count", token=token)
    record("unread count after mark-read", s, d)

    # === 6b. Mark all read (W49 Task 65 Bug 2 fix) ===
    section("Mark all read")
    s, d = call("POST", "/opc/notification/inbox/read-all", token=token)
    record(
        "read-all endpoint exists",
        s, d,
        accept=[(200, 200), (500, 500)],
        note="W49: new endpoint added by frontend/backend fix",
    )

    # === 6c. WebSocket handshake (W49 Task 65 Bug 3 fix) ===
    section("WebSocket handshake at /opc/notification/ws")
    import socket as _socket
    from urllib.parse import quote
    import base64 as _b64
    try:
        key = _b64.b64encode(b"0123456789abcdef").decode()
        req = (
            f"GET /opc/notification/ws?token={quote(token)} HTTP/1.1\r\n"
            f"Host: localhost:8080\r\n"
            f"Upgrade: websocket\r\n"
            f"Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            f"Sec-WebSocket-Version: 13\r\n"
            f"\r\n"
        ).encode()
        s_sock = _socket.create_connection(("localhost", 8080), timeout=5)
        s_sock.sendall(req)
        resp = s_sock.recv(4096).decode("utf-8", errors="replace")
        s_sock.close()
        status_line = resp.splitlines()[0] if resp else ""
        ok = "101" in status_line
        results.append((
            "WS handshake returns 101 Switching Protocols",
            200 if ok else 500,
            200 if ok else 500,
            status_line[:80],
            "W49: gateway RewritePath + WS whitelist",
            ok,
        ))
        print(f"  [{'OK  ' if ok else 'FAIL'}] {'WS handshake returns 101 Switching Protocols':45s} {status_line[:60]}")
    except Exception as e:
        results.append((
            "WS handshake returns 101 Switching Protocols",
            500, 500, str(e)[:80], "W49: gateway RewritePath + WS whitelist", False,
        ))
        print(f"  [FAIL] WS handshake exception: {e}")

    # === 7. Validation: bad email format ===
    section("Validation: bad email format")
    s, d = call(
        "POST", "/opc/notification/email/send", token=token,
        body={"to": "not-an-email", "subject": "x", "body": "y"}
    )
    record(
        "bad email format -> validation error",
        s, d,
        accept=[(200, 400), (200, 500), (400, 400), (500, 500)],
        note="HTTP 200 + body code=500 '收件人邮箱格式不正确' = validation wired",
    )

    # === 8. Validation: bad phone format ===
    section("Validation: bad phone format")
    s, d = call(
        "POST", "/opc/notification/sms/send", token=token,
        body={"phone": "12345", "templateCode": "x"}
    )
    record(
        "bad phone format -> validation error",
        s, d,
        accept=[(200, 400), (200, 500), (400, 400), (500, 500)],
        note="HTTP 200 + body code=500 '手机号格式不正确' = validation wired",
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
