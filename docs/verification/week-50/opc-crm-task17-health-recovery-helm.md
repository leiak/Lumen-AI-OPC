# Task 17: opc-crm Health Check + RECOVERY + Helm Chart

Date: 2026-09-11

## Files Created/Modified (5)

- deploy/scripts/health-check.sh (MODIFIED: +8 checks via check_crm function, +PYTHONIOENCODING=utf-8 export)
- deploy/RECOVERY.md (MODIFIED: added §11 opc-crm)
- deploy/helm/opc/values.yaml (MODIFIED: added crm service)
- deploy/helm/opc/templates/deployment-crm.yaml (NEW)
- deploy/helm/opc/templates/service-crm.yaml (NEW)

## Health Checks (8 new)

1. aiopc-crm container running
2. Nacos opc-crm registered (opc-dev namespace)
3. /actuator/health UP (port 9312, direct, bypassing gateway)
4. /opc/crm/customer body.code=200 (via gateway)
5. /opc/crm/opportunity body.code=200 (via gateway)
6. /opc/crm/dashboard body.code=200 (funnel, via gateway)
7. /opc/crm/contract body.code=200 (via gateway)
8. /opc/crm/order body.code=200 (via gateway, validates UTF-8 safe parsing)

## Bug Fixed: UTF-8 pipeline corruption

The `itemsJson` column in `opc_crm_order` contains Chinese characters (CRM核心模块, CRM扩展).
When piped through `echo "${body}" | python` on Windows, the default GBK console code page
corrupted the multi-byte UTF-8 sequence, causing `json.load` to fail with
"Expecting property name enclosed in double quotes".

Fix: added `export PYTHONIOENCODING=utf-8` near top of health-check.sh, so all python
sub-processes decode stdin/stdout as UTF-8. Fix applies to all sections (including existing
notification/inbox checks that may get Chinese text in the future).

## Helm Chart

- Deployment template + Service template + PodDisruptionBudget (auto via range)
- Probes configured (liveness + readiness on /actuator/health/*)
- Image: `opc/opc-crm`
- Resources: 300m/768Mi requests, 1500m/1.5Gi limits
- JASYPT_PASSWORD secret wired from `opc-jasypt-secrets`
- 2 replicas (HPA off by default; can be enabled later via `hpa.enabled: true`)

## Verification

- helm lint: PASS (`0 chart(s) failed`)
- helm template test: 3 resources named `opc-crm` rendered (Deployment + Service + PodDisruptionBudget)
- values.yaml YAML parse: OK (Python yaml.safe_load)
- health-check.sh: 36/36 PASS (28 existing + 8 new crm checks)

## RECOVERY.md §11 Coverage

- 作用/端口/依赖服务/6 张表 描述
- schema 初始化 SQL (12-opc-crm-schema.sql + 13-crm-seed.sql)
- Nacos 配置 (DataID + import-dev.sh 命令)
- 启动命令 (thin jar build + docker compose up)
- 验证命令 + 36/36 PASS 预期
- 快速 smoke test (curl + token)
- 注意事项: JASYPT_PASSWORD / opc-ai-core LLM key / 阶段变更通知开关

## Final Status

DONE — Task 17 complete. Commit 7a88c7d pushed to origin/main.
