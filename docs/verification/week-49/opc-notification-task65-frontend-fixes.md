# Task 65: Frontend<->Backend Path Alignment Fixes

Date: 2026-09-11
Branch: main
Commit: d280201 — fix(notification): align frontend<->backend paths (markRead, read-all, WS)

## Context

W49 final acceptance discovered 3 frontend<->backend integration bugs,
all 404-class wiring issues. All fixed in this commit.

## Bug 1: markRead path mismatch

| Side | Path |
|------|------|
| Frontend (before) | `/opc/notification/inbox/${id}/read` |
| Backend (existing) | `@RequestMapping("/opc/notification/inbox")` + `@PostMapping("/read/{id}")` -> `/opc/notification/inbox/read/{id}` |

Fix: changed frontend api path to match backend.
File: `vue3-typescript/src/api/opc/notification.ts`

```typescript
export function markRead(id: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/notification/inbox/read/${id}`, method: 'post' })
}
```

Verify:
```bash
$ curl -s -X POST -H "Authorization: Bearer $TOKEN" \
    http://127.0.0.1:8080/opc/notification/inbox/read/0
{"msg":"Inbox not found: 0","code":500}   # route wired; 404 = static fallback would say "No static resource"
```

## Bug 2: missing markAllRead endpoint

Frontend `markAllRead()` calls `/opc/notification/inbox/read-all` which
didn't exist. Added full stack: Controller -> Service interface ->
Service impl -> Mapper interface -> Mapper XML.

Files changed:
- `springboot3/ruoyi-modules/opc-notification/src/main/java/com/ruoyi/opc/notification/controller/InboxController.java`
  - Added `@PostMapping("/read-all") public R<Void> markAllRead()`
  - Returns `R.fail("未登录")` if `SecurityUtils.getUserId() == null`
- `springboot3/ruoyi-modules/opc-notification/src/main/java/com/ruoyi/opc/notification/service/InboxService.java`
  - Added `int markAllRead(Long userId)` to interface
- `springboot3/ruoyi-modules/opc-notification/src/main/java/com/ruoyi/opc/notification/service/impl/InboxServiceImpl.java`
  - Implements `markAllRead(Long)` by delegating to mapper
- `springboot3/ruoyi-modules/opc-notification/src/main/java/com/ruoyi/opc/notification/mapper/NotificationInboxMapper.java`
  - Added `int markAllRead(@Param("userId") Long userId)`
- `springboot3/ruoyi-modules/opc-notification/src/main/resources/mapper/NotificationInboxMapper.xml`
  - Added bulk update (uses `read_at` per W49 schema, NOT `read_flag`):

```xml
<update id="markAllRead">
    UPDATE opc_notification_inbox
    SET read_at = NOW()
    WHERE user_id = #{userId} AND read_at IS NULL
</update>
```

Verify:
```bash
$ curl -s -X POST -H "Authorization: Bearer $TOKEN" \
    http://127.0.0.1:8080/opc/notification/inbox/read-all
{"code":200,"msg":null,"data":null}
```

## Bug 3: WebSocket path mismatch

| Side | Path |
|------|------|
| Frontend (useNotificationSocket) | `ws://host/opc/notification/ws?token=<jwt>` |
| Backend (WebSocketConfig) | `registry.addHandler(handler, "/ws/notification")` |
| Backend REST controllers | `@RequestMapping("/opc/notification/inbox")` (full path) |
| Gateway route (existing) | `Path=/opc/notification/**` with NO StripPrefix |

Issue: gateway forwards `/opc/notification/ws` to backend, but
`WebSocketHandlerRegistry` only matches `/ws/notification`. Also, the
REST controllers use full `/opc/notification/inbox` path so a global
`StripPrefix` would break them.

Fix:
1. Added a SPECIFIC gateway route BEFORE the broader `opc-notification`
   route with `RewritePath` that maps `/opc/notification/ws` ->
   `/ws/notification`:
   ```yaml
   - id: opc-notification-ws
     uri: http://aiopc-notification:9310
     predicates:
       - Path=/opc/notification/ws,/opc/notification/ws/**
     filters:
       - RewritePath=/opc/notification/ws(?<segment>/?.*), /ws/notification$\{segment}
   ```
2. Added `/opc/notification/ws` and `/opc/notification/ws/**` to the
   gateway security whitelist because WS auth uses `?token=` query
   param, NOT the `Authorization: Bearer` header (which is what
   `AuthFilter` checks).

Files changed:
- `springboot3/ruoyi-gateway/src/main/resources/application.yml`

Verify:
```bash
$ curl -s -i --max-time 4 \
    -H "Upgrade: websocket" \
    -H "Connection: Upgrade" \
    -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
    -H "Sec-WebSocket-Version: 13" \
    "http://127.0.0.1:8080/opc/notification/ws?token=$ENCODED_TOKEN"

HTTP/1.1 101 Switching Protocols
upgrade: websocket
connection: upgrade
sec-websocket-accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

## Regression Coverage

Added 2 new test cases to `tmp_e2e/notification_e2e.py`:
- `read-all endpoint exists` -> expects R=200
- `WS handshake returns 101 Switching Protocols` -> raw socket HTTP/1.1

## Verification Results

| Check | Result |
|-------|--------|
| markRead path wired | OK (R=500 "Inbox not found: 0") |
| markAllRead endpoint exists | OK (R=200) |
| WS handshake | OK (HTTP/1.1 101 Switching Protocols) |
| REST inbox list still works | OK (R=200) |
| REST unread-count still works | OK (R=200) |
| Health check `health-check.sh` | **28/28 PASS** |
| Notification e2e `notification_e2e.py` | **13/13 PASS** (was 11/11) |

## Build & Deploy

```bash
# opc-notification (thin jar)
mvn -pl ruoyi-modules/opc-notification -am clean package \
    -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true
mvn -pl ruoyi-modules/opc-notification dependency:copy-dependencies \
    -DoutputDirectory=target/dependency

# gateway (fat jar)
mvn -pl ruoyi-gateway clean package -Dmaven.test.skip=true

# Docker
docker compose build aiopc-gateway aiopc-notification
docker compose up -d aiopc-gateway aiopc-notification
```

## Lessons Learned

1. **Gateway RewritePath before broader route**: Spring Cloud Gateway
   evaluates routes in declaration order. A WS-specific route MUST
   come before the broader `/opc/notification/**` route or it will
   be shadowed.
2. **WS auth model differs from REST**: WS uses `?token=` query param
   (browser WebSocket API doesn't allow custom headers). Any new WS
   endpoint must be added to the security whitelist or AuthFilter will
   reject with `令牌不能为空`.
3. **AntPathMatcher for WS whitelist**: `/opc/notification/ws/**`
   matches `/opc/notification/ws`, `/opc/notification/ws/`, and any
   sub-path. Just need the wildcard trailing segment.
4. **Notification schema uses `read_at`**: The W49 inbox schema
   uses `read_at DATETIME` (with NULL = unread) rather than a
   `read_flag` TINYINT. Bulk mark-read must use `read_at = NOW()`
   with WHERE `read_at IS NULL`.
5. **`$\{segment}` escaping in YAML**: Spring's `$` placeholder
   resolution must be escaped in YAML for RewritePath replacement
   strings using named groups.

## Files Changed

| File | Lines | Type |
|------|-------|------|
| vue3-typescript/src/api/opc/notification.ts | 2 | Bug 1 |
| opc-notification/.../InboxController.java | +13 | Bug 2 |
| opc-notification/.../InboxService.java | +5 | Bug 2 |
| opc-notification/.../InboxServiceImpl.java | +5 | Bug 2 |
| opc-notification/.../NotificationInboxMapper.java | +2 | Bug 2 |
| opc-notification/.../NotificationInboxMapper.xml | +6 | Bug 2 |
| ruoyi-gateway/src/main/resources/application.yml | +14 | Bug 3 (route + whitelist) |
| tmp_e2e/notification_e2e.py | +52 | Regression test |

**Total: 8 files changed, 95 insertions(+), 1 deletion(-)**
