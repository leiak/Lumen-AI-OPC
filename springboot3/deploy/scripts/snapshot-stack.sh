#!/usr/bin/env bash
# ============================================================
# AIOPC 全栈状态快照 (W50 固化)
#
# 一次性导出当前 deploy 状态 (容器 / MySQL / Redis / Nacos / .env)
# 到 deploy/{redis,mysql,nacos}/snapshot-<date>.* 用于:
#   - 团队成员 onboarding 时知道系统有什么
#   - 部署异常时回滚对账
#   - 容量规划基线
#
# 用法：
#   cd springboot3/deploy/scripts
#   ./snapshot-stack.sh                   # 写到今天日期
#   ./snapshot-stack.sh 2026-09-11       # 指定日期
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

DATE="${1:-$(date +%Y-%m-%d)}"
TS="$(date +%Y-%m-%dT%H-%M-%S)"

REDIS_PWD="Opc@2026!"
MYSQL_PWD="Opc@2026!"
MYSQL_CONTAINER="aiopc-mysql"
MYSQL_DB="ry-vue-opc"
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NACOS_NS="${NACOS_NS:-opc-dev}"

OUT_CONTAINERS="${DEPLOY_DIR}/snapshot-containers-${DATE}.txt"
OUT_MYSQL_INFO="${DEPLOY_DIR}/mysql/snapshot-info-${DATE}.json"
OUT_MYSQL_TABLES="${DEPLOY_DIR}/mysql/snapshot-tables-${DATE}.txt"
OUT_REDIS="${DEPLOY_DIR}/redis/snapshot-${DATE}.txt"
OUT_NACOS_LIST="${DEPLOY_DIR}/nacos/configs-list-${DATE}.txt"

mkdir -p "${DEPLOY_DIR}/redis" "${DEPLOY_DIR}/mysql" "${DEPLOY_DIR}/nacos"

echo "=================================================="
echo " AIOPC 全栈快照 ${DATE}"
echo "=================================================="

# ---------- 1. 容器清单 ----------
echo ""
echo "[1/5] 容器清单 -> ${OUT_CONTAINERS}"
{
  echo "# AIOPC 容器清单 - ${TS}"
  echo ""
  echo "## Running"
  docker ps --filter "name=aiopc-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
  echo ""
  echo "## All (含 stopped)"
  docker ps -a --filter "name=aiopc-" --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
} > "${OUT_CONTAINERS}"
echo "  OK"

# ---------- 2. MySQL 表清单 ----------
echo ""
echo "[2/5] MySQL snapshot"
RAW="$(docker exec "${MYSQL_CONTAINER}" mysql -uroot -p"${MYSQL_PWD}" -B -N \
  -e "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='${MYSQL_DB}' ORDER BY TABLE_ROWS DESC, TABLE_NAME" 2>/dev/null || echo "")"
TABLE_COUNT=0
if [[ -n "${RAW}" ]]; then
  TABLE_COUNT=$(echo "${RAW}" | wc -l | tr -d ' ')
fi

{
  echo "# MySQL ${MYSQL_DB} 表清单 + 行数 - ${TS}"
  echo "# 共 ${TABLE_COUNT} 张表"
  echo ""
  printf "%-45s %10s\n" "Table" "Rows"
  printf "%-45s %10s\n" "-----------------------------------------------" "----------"
  while IFS=$'\t' read -r tbl rows; do
    [[ -z "${tbl}" ]] && continue
    printf "%-45s %10s\n" "${tbl}" "${rows}"
  done <<< "${RAW}"
} > "${OUT_MYSQL_TABLES}"
echo "  -> ${OUT_MYSQL_TABLES}"

# Persist RAW to temp file so the JSON helper can read it (Windows path-safe)
RAW_FILE="${DEPLOY_DIR}/mysql/.tmp-rows-${DATE}.tsv"
printf "%s\n" "${RAW}" > "${RAW_FILE}"
python "${SCRIPT_DIR}/_snapshot_mysql_to_json.py" \
  "${OUT_MYSQL_INFO}" "${DATE}" "${TS}" "${MYSQL_DB}" "${TABLE_COUNT}" "${RAW_FILE}" || true
rm -f "${RAW_FILE}"
echo "  -> ${OUT_MYSQL_INFO}"

# ---------- 3. Redis 快照 ----------
echo ""
echo "[3/5] Redis snapshot -> ${OUT_REDIS}"
REDIS_KEYS="$(docker exec aiopc-redis redis-cli -a "${REDIS_PWD}" --no-auth-warning --scan 2>/dev/null | sort)"
REDIS_DBSIZE="$(docker exec aiopc-redis redis-cli -a "${REDIS_PWD}" --no-auth-warning DBSIZE 2>/dev/null | tr -d '[:space:]')"
REDIS_KEY_COUNT="$(echo "${REDIS_KEYS}" | grep -c '.' || echo 0)"
{
  echo "# Redis 7 snapshot - ${TS}"
  echo "# Password: dev only (rotate for prod)"
  echo "# DBSIZE : ${REDIS_DBSIZE}"
  echo "# Keys   : ${REDIS_KEY_COUNT}"
  echo ""
  echo "## Keys by prefix"
  if [[ -n "${REDIS_KEYS}" ]]; then
    echo "${REDIS_KEYS}" | awk -F: '{print $1}' | sort | uniq -c | sort -rn
  else
    echo "  (无 key)"
  fi
  echo ""
  echo "## sys_config (字典/常量缓存, max 20)"
  echo "${REDIS_KEYS}" | grep '^sys_config:' | head -20
  echo ""
  echo "## sys_dict (字典类型缓存, max 20)"
  echo "${REDIS_KEYS}" | grep '^sys_dict:' | head -20
  echo ""
  echo "## login_tokens count"
  echo "${REDIS_KEYS}" | grep '^login_tokens:' | wc -l | tr -d ' '
} > "${OUT_REDIS}"
echo "  OK"

# ---------- 4. Nacos 配置清单 ----------
echo ""
echo "[4/5] Nacos 配置清单 -> ${OUT_NACOS_LIST}"
NACOS_OUT="$(PYTHONIOENCODING=utf-8 python "${SCRIPT_DIR}/_snapshot_nacos_list.py" "${NACOS_ADDR}" "${NACOS_NS}" "${TS}" 2>&1)" || NACOS_OUT="[WARN] Nacos 不可达"
echo "${NACOS_OUT}" > "${OUT_NACOS_LIST}"
echo "  OK"

# ---------- 5. .env 摘要 ----------
echo ""
echo "[5/5] .env 摘要"
if [[ -f "${DEPLOY_DIR}/.env" ]]; then
  echo "  .env 存在 ($(wc -l < "${DEPLOY_DIR}/.env") 行, $(stat -c %s "${DEPLOY_DIR}/.env" 2>/dev/null || stat -f %z "${DEPLOY_DIR}/.env") B)"
  echo "  包含的 KEY (不含明文):"
  grep -E '^[A-Z_][A-Z0-9_]+=' "${DEPLOY_DIR}/.env" 2>/dev/null | cut -d= -f1 | sort | sed 's/^/    - /' || true
else
  echo "  [WARN] .env 不存在 (W48.7 必备!)"
fi

echo ""
echo "=================================================="
echo " 快照完成"
echo "=================================================="
echo "  - ${OUT_CONTAINERS}"
echo "  - ${OUT_MYSQL_INFO}"
echo "  - ${OUT_MYSQL_TABLES}"
echo "  - ${OUT_REDIS}"
echo "  - ${OUT_NACOS_LIST}"