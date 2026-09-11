# Task 16: opc-crm Deploy to Docker

Date: 2026-09-11

## Steps Completed

1. ✓ Verified prereq infrastructure (aiopc-nacos-1 + aiopc-mysql + aiopc-redis + aiopc-notification + aiopc-gateway = 5)
2. ✓ Built thin jar (BUILD SUCCESS, opc-crm.jar = 94K, 13 modules reactor)
3. ✓ Copied dependencies (204 jars to target/dependency/)
4. ✓ Pushed Nacos config (3/3 OK — application-dev, opc-notification-dev, opc-crm-dev)
5. ✓ Rebuilt gateway (BUILD SUCCESS)
6. ✓ Built Docker images (aiopc-crm:latest + aiopc-gateway:latest)
7. ✓ Started containers (aiopc-crm Started, aiopc-gateway Recreated+Started)
8. ✓ Service started: `Started OpcCrmApplication in 10.546 seconds`

## Service Status

- aiopc-crm:    Up ~1 minute, port 9312 exposed
- aiopc-gateway: Up ~1 minute, port 8080 exposed
- Direct health (port 9312 /actuator/health): `{"status":"UP"}` (HTTP 200)
- Gateway route /opc/crm/dashboard: returns seeded data (funnel + top opportunities)
- Gateway route /opc/crm/customer: returns `code:200` (empty data — possible tenant scoping)
- Gateway route /opc/crm/opportunity: returns `code:200` (empty data — possible tenant scoping)

## Endpoint Tests

| Endpoint | HTTP | Body |
|---|---|---|
| GET /opc/crm/customer?keyword=北京 | 200 | `{"code":200,"msg":null,"data":[]}` |
| GET /opc/crm/opportunity | 200 | `{"code":200,"msg":null,"data":[]}` |
| GET /opc/crm/dashboard | 200 | funnel: {LEAD:4,QUALIFIED:3,PROPOSAL:5,NEGOTIATION:4,WON:2,LOST:2}, topOpportunities populated |
| GET /opc/crm/lead | 500 | `{"msg":"No static resource opc/crm/lead.","code":500}` |
| GET aiopc-crm:9312/actuator/health (direct) | 200 | `{"status":"UP"}` |

## Concerns

1. `/opc/crm/lead` returns 500 because no `LeadController` exists — the crm module only has customer/contact/contract/dashboard/follow-up/opportunity controllers. This is a source-level omission, not a deploy issue. Follow-up: either add LeadController or remove the front-end reference.
2. `/opc/crm/customer` and `/opc/crm/opportunity` return empty `data:[]` even though the DB has dashboard seed data (WON customers, opportunities with names + amounts). The controllers may be applying a user-scoped filter that admin user doesn't match. Worth investigating in W51 but does not block deploy.
3. DB seed data IS present (dashboard returns real funnel counts + top opportunities with names like "战略合作" amount 2000000 score 95). The MySQL init container ran the crm_seed.sql successfully.
4. docker-compose warning: `version` attribute obsolete (cosmetic).
5. docker-compose warning: orphan `deploy-postgres-1` (cosmetic — not part of this deploy).
