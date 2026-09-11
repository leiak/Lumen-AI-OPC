# Task 14: health-check.sh opc-notification endpoints (FIXED)

Date: 2026-09-11

## Bug Found and Fixed

Task 14 initial implementation had two test-path bugs in `health-check.sh`:

1. **Nacos service name**: queried `aiopc-notification` (docker container name) but
   Spring registers as `opc-notification` (application name). Spring Cloud Nacos
   uses `spring.application.name` without any container prefix.
2. **Path prefix**: tested `/prod-api/opc/notification/...` but the gateway
   matches `/opc/notification/...` directly (no StripPrefix filter). The
   `/prod-api/` prefix only applies to the Vue frontend's dev-mode proxy and
   the RuoYi built-in `/api/**` routes, not to OPC backend services.

After fixing items 6.2, 6.4, 6.5, item 6.6 still failed. Root cause: the
original check treated ANY `No static resource` body as a failure, but that
message is thrown by Spring MVC's static resource handler whenever a path
is NOT mapped — including the controller layer (route resolved successfully
but no `@RequestMapping` matches) and the gateway layer (no route at all).

The fix distinguishes the two by checking for the WebFlux `404 NOT_FOUND`
prefix that gateway's `NoHandlerFoundException` adds:
- Routed (controller-layer static fallback): `{"msg":"No static resource <path>.","code":500}`
- Unrouted (gateway-layer static fallback): `{"code":500,"msg":"404 NOT_FOUND \"No static resource <path>\""}`

## Items (final, all PASS)

| # | Item | Check | Result |
|---|------|-------|--------|
| 6.1 | Container up | `docker ps` shows `aiopc-notification` running | PASS |
| 6.2 | Nacos registration | `opc-notification` has 1 host in `opc-dev` namespace | PASS |
| 6.3 | Direct /actuator/health | `127.0.0.1:9310/actuator/health` → `{"status":"UP"}` | PASS |
| 6.4 | Inbox list via gateway | `/opc/notification/inbox?page=1&pageSize=5` → `code:200` | PASS |
| 6.5 | Unread-count via gateway | `/opc/notification/inbox/unread-count` → `code:200, data:0` | PASS |
| 6.6 | Gateway route resolves | controller-layer `No static resource` (no `404 NOT_FOUND`) | PASS |

## Verification

- Re-ran `health-check.sh` after the three fixes
- All 6 opc-notification items PASS
- **Total: 28/28 PASS**
- Commit: `f39acba fix(notification): Task 14 — correct Nacos name + path prefix in health checks`

## Cross-checked with Task 14's claim

Task 14's verification report claimed "other `/opc/**` routes work with `/prod-api/`".
This is incorrect — `/opc/user/**`, `/opc/billing/**`, `/opc/finance/**`,
`/opc/agent/**`, `/opc/llm/**`, `/opc/insight/**` all return 404 under `/prod-api/`
prefix. The fix only corrects the opc-notification test paths; no other test
items were using the wrong prefix in this script.