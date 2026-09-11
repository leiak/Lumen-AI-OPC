# Task 15: opc-crm Nacos + Deployment Files

Date: 2026-09-11

## Files Created/Modified (5)

- deploy/nacos/opc-crm-dev.yml (NEW)
- deploy/nacos/opc-crm-prod.yml (NEW)
- deploy/docker-compose.yml (MODIFIED: added aiopc-crm service)
- ruoyi-gateway/src/main/resources/application.yml (MODIFIED: added /opc/crm/** route)
- deploy/nacos/import-dev.sh (MODIFIED: added opc-crm-dev.yml to NAMES)

## Dockerfile

Already exists at `ruoyi-modules/opc-crm/Dockerfile` with port 9312, thin-jar pattern. NOT TOUCHED.

## Verification

- docker-compose yml: valid
- gateway yml: valid
- import-dev.sh: updated