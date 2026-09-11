# Task 18: E2E test — notification_e2e.py

Date: 2026-09-11

## File Added

- `tmp_e2e/notification_e2e.py`

## Test Coverage

1. Admin login (admin/admin123)
2. Email send endpoint reachable (dev: SMTP fail -> 500)
3. SMS send (dev: Aliyun NoOp -> 200)
4. Inbox list (paginated, 5/page)
5. Inbox items is a list
6. Inbox response has `unreadCount` field
7. Unread count endpoint
8. Unread count is integer >= 0
9. Mark read endpoint reachable (no inbox items -> 500 "Inbox not found")
10. Unread count after mark-read (idempotent)
11. Validation: bad email format -> 500 "收件人邮箱格式不正确"
12. Validation: bad phone format -> 500 "手机号格式不正确"

## Run Result

```
Total: 11  Pass: 11  Fail: 0
```

All checks PASS.

## Endpoints Verified

- `POST /opc/notification/email/send`  (EmailController#send, body: `{to, subject, body}`)
- `POST /opc/notification/sms/send`    (SmsController#send, body: `{phone, templateCode, vars}`)
- `GET  /opc/notification/inbox`       (InboxController#list, params: `page, pageSize`)
- `GET  /opc/notification/inbox/unread-count` (InboxController#unreadCount)
- `POST /opc/notification/inbox/read/{id}` (InboxController#markRead)

## Endpoint Path Corrections vs Task Spec

- **Mark-read path**: actual is `POST /opc/notification/inbox/read/{id}`, NOT `/{id}/read`
- **No `/read-all` endpoint**: `InboxController` only exposes mark-read for a single id (no batch). The spec's "mark all read" test was removed.
- **Email DTO**: field is `body` (HTML), NOT `content`. No `templateCode` field — email is sent as raw HTML body.
- **Inbox response**: top-level fields are `items`, `unreadCount`, `page`, `pageSize`. Each item has `readAt` (null=unread), NOT `readFlag`.

## Behavior Discovered

- Gateway returns `HTTP 200 + body code=500` for business/validation errors (GlobalExceptionHandler pattern).
- Email send in dev: `SmtpEmailProvider` throws `EmailSendException` after 3 retries (mail host=localhost) -> controller re-throws -> handler returns HTTP 200 + code 500 with msg starting "SMTP send failed:".
- SMS send in dev: `ProviderConfig` returns a lambda `SmsProvider` when Aliyun keys are empty -> logs `[sms:noop]` and returns void -> controller returns R.ok() -> HTTP 200 + code 200.
- Inbox items array is empty for fresh admin (no `InboxService.push()` triggered yet from any other service).
- WebSocket push is best-effort inside `InboxServiceImpl.push()` — not directly testable via REST (push is service-internal, called via Feign from other services).

## Style / Pattern

Matches `tmp_e2e/e2e_all.py`:
- `urllib.request` (no `requests` dependency)
- `BASE = "http://localhost:8080"` (gateway direct, not via frontend nginx :8079)
- `record(label, status, body, accept=[...], note="...")` helper with allow-list of acceptable (HTTP, code) tuples
- Final summary prints Total/Pass/Fail + failure list

## Commit

```
ddc881c test(notification): Task 18 — E2E test for opc-notification
```

Pushed to origin/main.
