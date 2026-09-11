# Task 14: health-check.sh opc-notification endpoints

Date: 2026-09-11

## Changes

- Added section `[6] opc-notification 健康检查 (W49 Task 14)` with 6 new items to
  `springboot3/deploy/scripts/health-check.sh`
- Updated header comment to list the new section (was 5 sections, now 6)
- Total check items: 22 -> 28 (in practice: 22 + 6 = 28 if all notification
  items pass; the 22 prior items remain unchanged)

## Items Added

1. **aiopc-notification running** — `docker ps | grep aiopc-notification`
2. **Nacos aiopc-notification registered** — `GET /nacos/v1/ns/instance/list?serviceName=aiopc-notification&namespaceId=opc-dev`, parse `hosts[]` length >= 1
3. **Direct /actuator/health** — `GET http://127.0.0.1:9310/actuator/health`, body contains `"status":"UP"`
4. **Inbox list via gateway** — `GET /prod-api/opc/notification/inbox?page=1&pageSize=5`, validate `body.code==200` (not just HTTP 200, to avoid static-fallback false positive)
5. **Unread-count via gateway** — `GET /prod-api/opc/notification/inbox/unread-count`, validate `body.code==200` AND `data` is int
6. **Gateway route resolves** — probe `/prod-api/opc/notification/inbox/__no_such_path__`, validate response is JSON without `"No static resource"` substring (controller-level 404 has different msg than gateway static fallback)

Login reused the admin/admin123 pattern from `login_and_check()` to obtain a JWT.

## Verification

Ran `bash springboot3/deploy/scripts/health-check.sh` on 2026-09-11.

**Result: 24 PASS / 4 FAIL**

| # | Item | Status |
|---|------|--------|
| 6.1 | aiopc-notification running | PASS |
| 6.2 | Nacos aiopc-notification registered | FAIL |
| 6.3 | /actuator/health UP | PASS |
| 6.4 | /inbox via gateway (code=200) | FAIL |
| 6.5 | /inbox/unread-count (code=200 + data:int) | FAIL |
| 6.6 | Gateway /opc/notification/** routes | FAIL |

### Root cause analysis of the 4 FAILs

All 4 FAILs are genuine runtime state issues, NOT script bugs:

1. **6.2 (Nacos registration)**: `GET /nacos/v1/ns/instance/list?serviceName=aiopc-notification&namespaceId=opc-dev` returns `{"hosts":[],"valid":true}`. The aiopc-notification container is running but did not register itself with Nacos in the `opc-dev` namespace. Verified same empty result for `opc-prod` namespace too. Likely missing `spring.cloud.nacos.discovery.register-enabled: true` or wrong namespace in aiopc-notification bootstrap.yml.

2. **6.4 / 6.5 / 6.6 (gateway routing)**: All three requests return `{"code":500,"msg":"404 NOT_FOUND \"No static resource prod-api/opc/notification/inbox...\""}`. This is the Spring WebFlux static resource fallback - the gateway has no route matching `/opc/notification/**`, so the path falls through to the static resource handler which returns 404 wrapped in R-style JSON. The other 13 `/opc/**` endpoints (user/billing/finance/agent/llm/insight) all pass, confirming the gateway has routes for them but NOT for `/opc/notification/**`. Missing route definition in `aiopc-gateway` bootstrap.yml.

### Action items (separate task)

- Add `/opc/notification/**` route to `aiopc-gateway` bootstrap.yml (likely in `spring.cloud.gateway.server.webflux.routes`)
- Verify aiopc-notification registers with Nacos on `opc-dev` (check `spring.application.name` and `spring.cloud.nacos.discovery.namespace`)
- After routing fix, all 6 notification items should turn green, bringing total to 28/28 PASS

### Files changed

- `springboot3/deploy/scripts/health-check.sh` (+104 lines: new `check_notification()` function + header comment update)
- `docs/verification/week-49/opc-notification-task14-health-check.md` (this log)

## Self-Review Checklist

- [x] 6 new items added (matches user's plan)
- [x] Items follow existing style (`ok()`/`nok()` with `[OK]`/`[FAIL]` prefixes, `[GRN]`/`[RED]` colors)
- [x] Login reuses admin/admin123 pattern
- [x] Nacos namespace uses `opc-dev` (matches other services in script)
- [x] Gateway paths use `/prod-api/opc/...` prefix (matches existing endpoints in `login_and_check`)
- [x] Checks are discerning — use `body.code` not just HTTP status to avoid static-fallback false positives
- [x] Bash syntax validated (`bash -n` passes)
- [x] Verification log created
