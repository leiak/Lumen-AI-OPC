#!/usr/bin/env bash
# ============================================================
# 确保 opc-erp schema 在 MySQL 中存在 (W72 固化)
#
# 问题：mysql-initdb.d/ 里的 SQL 只在 MySQL data 卷为空时跑一次。
# 如果容器崩溃重启时 data 卷还在,initdb.d 不会再跑,新加的 schema
# (如 opc-erp 11 张表) 不会自动建出来。
#
# 本脚本检查每张 opc_erp_* 表是否存在,缺失则执行 15-opc-erp-schema.sql。
# 幂等: CREATE TABLE IF NOT EXISTS 不会重建已存在的表。
#
# 用法:
#   bash deploy/scripts/ensure-opc-erp-schema.sh
#   # 自动连接 docker 容器 aiopc-mysql
#
# 集成: deploy.sh 在 docker compose up 业务容器前会先跑这个
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
INITDB_DIR="${DEPLOY_DIR}/mysql-initdb.d"
SCHEMA_FILE="${INITDB_DIR}/15-opc-erp-schema.sql"
SEED_FILE="${INITDB_DIR}/96-opc-erp-seed.sql"

MYSQL_PWD="${MYSQL_PWD:-Opc@2026!}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-aiopc-mysql}"
MYSQL_DB="${MYSQL_DB:-ry-vue-opc}"

# 11 张 opc_erp_* 表
EXPECTED_TABLES=(
  opc_erp_supplier
  opc_erp_product
  opc_erp_product_sku
  opc_erp_batch
  opc_erp_inventory_log
  opc_erp_purchase
  opc_erp_purchase_item
  opc_erp_sale
  opc_erp_sale_item
  opc_erp_return
  opc_erp_daily_snapshot
)

# 1) 检查容器
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
  echo "[FAIL] MySQL 容器 ${MYSQL_CONTAINER} 未运行。请先: docker compose up -d mysql"
  exit 1
fi

# 2) 检查缺哪些表
MISSING=()
for tbl in "${EXPECTED_TABLES[@]}"; do
  exists=$(docker exec "${MYSQL_CONTAINER}" \
    mysql -u"${MYSQL_USER}" -p"${MYSQL_PWD}" -N -B -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}' AND table_name='${tbl}';" 2>/dev/null || echo "0")
  if [[ "${exists}" != "1" ]]; then
    MISSING+=("${tbl}")
  fi
done

if [[ ${#MISSING[@]} -eq 0 ]]; then
  echo "[OK] 11/11 opc_erp_* 表全部存在 (${MYSQL_DB})"
  echo "[OK] seed 5 个供应商:"
  docker exec "${MYSQL_CONTAINER}" \
    mysql -u"${MYSQL_USER}" -p"${MYSQL_PWD}" -N -B -e \
    "SELECT id, name, level FROM ${MYSQL_DB}.opc_erp_supplier ORDER BY id;" 2>/dev/null
  exit 0
fi

# 3) 缺表 — 跑 schema + seed
echo "[WARN] 缺 ${#MISSING[@]} 张表: ${MISSING[*]}"
echo "[INFO] 跑 schema: ${SCHEMA_FILE}"
docker exec -i "${MYSQL_CONTAINER}" \
  mysql -u"${MYSQL_USER}" -p"${MYSQL_PWD}" "${MYSQL_DB}" \
  < "${SCHEMA_FILE}" 2>&1 | grep -v "Using a password" || true
echo "[INFO] 跑 seed: ${SEED_FILE}"
docker exec -i "${MYSQL_CONTAINER}" \
  mysql -u"${MYSQL_USER}" -p"${MYSQL_PWD}" "${MYSQL_DB}" \
  < "${SEED_FILE}" 2>&1 | grep -v "Using a password" || true

# 4) 再次验证
echo "[VERIFY] 重新检查..."
STILL_MISSING=()
for tbl in "${EXPECTED_TABLES[@]}"; do
  exists=$(docker exec "${MYSQL_CONTAINER}" \
    mysql -u"${MYSQL_USER}" -p"${MYSQL_PWD}" -N -B -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}' AND table_name='${tbl}';" 2>/dev/null || echo "0")
  if [[ "${exists}" != "1" ]]; then
    STILL_MISSING+=("${tbl}")
  fi
done

if [[ ${#STILL_MISSING[@]} -eq 0 ]]; then
  echo "[OK] 全部 11 张表已建好"
  exit 0
else
  echo "[FAIL] 仍有 ${#STILL_MISSING[@]} 张表缺失: ${STILL_MISSING[*]}"
  exit 1
fi
