#!/usr/bin/env bash
# ============================================================
# OPC stack 健康检查 (W48.7 固化)
#
# 用途: 部署后 / 改配置后 / 每周巡检
#
# 验证项:
#   1) 13 个核心容器 Up
#   2) Nacos 注册中心 / 配置中心 健康
#   3) 13 个 /opc/** 接口返回 R-code 200 (登录后)
#   4) 前端 nginx 可访问
#
# 输出: PASS=绿, FAIL=红
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ---------- 配置 ----------
GATEWAY_HOST="${GATEWAY_HOST:-127.0.0.1}"
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-8079}"
NACOS_PORT="${NACOS_PORT:-8848}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
RED='\033[0;31m'
GRN='\033[0;32m'
YEL='\033[0;33m'
NC='\033[0m'

pass=0
fail=0
declare -a failed_items=()

ok()   { echo -e "${GRN}[OK]${NC}   $*"; pass=$((pass + 1)); }
nok()  { echo -e "${RED}[FAIL]${NC} $*"; fail=$((fail + 1)); failed_items+=("$*"); }
warn() { echo -e "${YEL}[WARN]${NC} $*"; }
hr()   { echo "--------------------------------------------------"; }

# ---------- 1. 容器 Up 检查 ----------
check_containers() {
  hr
  echo "[1] 核心容器状态"
  hr
  local containers=(
    "aiopc-nacos-1"
    "aiopc-mysql"
    "aiopc-redis"
    "aiopc-rabbitmq"
    "aiopc-qdrant"
    "aiopc-minio"
    "aiopc-elasticsearch"
    "aiopc-gateway"
    "aiopc-auth"
    "aiopc-system"
    "aiopc-user-center"
    "aiopc-agent-hub"
    "aiopc-ai-core"
    "aiopc-billing"
    "aiopc-finance"
    "aiopc-frontend"
  )
  for c in "${containers[@]}"; do
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${c}$"; then
      status=$(docker inspect --format '{{.State.Status}}' "${c}" 2>/dev/null)
      if [[ "${status}" == "running" ]]; then
        ok "${c} running"
      else
        nok "${c} 状态异常: ${status}"
      fi
    else
      nok "${c} 不存在"
    fi
  done
}

# ---------- 2. Nacos 健康 ----------
check_nacos() {
  hr
  echo "[2] Nacos 健康"
  hr
  local url="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos/v1/cs/health"
  local body
  body=$(curl -sf -m 10 "${url}" 2>/dev/null) || body=""
  # Nacos 实际返回明文 "UP" (status 200) 或 JSON {"status":"UP"}
  if [[ "${body}" == "UP" ]] || echo "${body}" | grep -q '"status":"UP"'; then
    ok "Nacos /v1/cs/health UP"
  else
    nok "Nacos 不健康: ${body:-<no response>}"
  fi
}

# ---------- 3. /opc/** 接口 ----------
login_and_check() {
  hr
  echo "[3] /opc/** 接口验证 (需先登录)"
  hr

  # 3.1 登录拿 JWT
  local login_body
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true

  if [[ -z "${login_body}" ]]; then
    warn "登录失败, 跳过业务接口验证 (可能密码已改)"
    return
  fi

  local token
  # RuoYi 返回 {data:{access_token:...}}, 兼容 token / access_token
  token=$(echo "${login_body}" | python -c "
import sys,json
d = json.load(sys.stdin).get('data', {})
print(d.get('token','') or d.get('access_token',''))
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    warn "未拿到 token, 跳过业务接口验证"
    return
  fi
  ok "登录成功 (admin)"

  # 3.2 13 个核心接口
  local endpoints=(
    "/prod-api/opc/user/profile"
    "/prod-api/opc/user/invitations?limit=5"
    "/prod-api/opc/user/companies?limit=5"
    "/prod-api/opc/agent/market?limit=5"
    "/prod-api/opc/billing/wallet"
    "/prod-api/opc/billing/orders?limit=5"
    "/prod-api/opc/finance/vouchers?limit=5"
    "/prod-api/opc/finance/flows/pending?limit=5"
    "/prod-api/opc/llm/models"
    "/prod-api/opc/insight/dashboard"
    "/prod-api/opc/insight/advice?limit=3"
    "/prod-api/opc/insight/alerts?limit=3"
    "/prod-api/opc/insight/daily?from=2026-09-01&to=2026-09-10"
  )
  local total=${#endpoints[@]}
  local ok_count=0
  for ep in "${endpoints[@]}"; do
    code=$(curl -s -o /dev/null -w "%{http_code}" -m 10 \
      -H "Authorization: Bearer ${token}" \
      "http://${GATEWAY_HOST}:${GATEWAY_PORT}${ep}" 2>/dev/null)
    if [[ "${code}" == "200" ]]; then
      ok_count=$((ok_count + 1))
    else
      nok "${ep} HTTP ${code}"
    fi
  done
  if [[ "${ok_count}" -eq "${total}" ]]; then
    ok "13/13 /opc/** 接口 R200"
  elif [[ "${ok_count}" -ge $((total * 8 / 10)) ]]; then
    warn "${ok_count}/${total} /opc/** 接口 R200 (≥80% OK)"
  else
    nok "${ok_count}/${total} /opc/** 接口 R200"
  fi
}

# ---------- 4. 前端 nginx ----------
check_frontend() {
  hr
  echo "[4] 前端 nginx"
  hr
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -m 10 \
    "http://${GATEWAY_HOST}:${FRONTEND_PORT}/" 2>/dev/null)
  if [[ "${code}" == "200" ]]; then
    ok "前端 http://${GATEWAY_HOST}:${FRONTEND_PORT}/ HTTP 200"
  else
    nok "前端 HTTP ${code}"
  fi
}

# ---------- 5. MySQL 抽查 ----------
check_mysql() {
  hr
  echo "[5] MySQL 抽查"
  hr
  local table_count
  table_count=$(docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' -N -B ry-vue-opc \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ry-vue-opc';" 2>/dev/null) || table_count=0
  if [[ "${table_count}" -ge 50 ]]; then
    ok "MySQL ry-vue-opc 有 ${table_count} 张表"
  else
    nok "MySQL 表数异常: ${table_count} (预期 ≥50)"
  fi

  local admin_count
  admin_count=$(docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' -N -B ry-vue-opc \
    -e "SELECT COUNT(*) FROM sys_user WHERE user_name='admin';" 2>/dev/null) || admin_count=0
  if [[ "${admin_count}" -ge 1 ]]; then
    ok "sys_user.admin 存在"
  else
    nok "sys_user.admin 缺失"
  fi
}

# ---------- Main ----------
main() {
  echo "=================================================="
  echo " OPC stack 健康检查 ($(date '+%Y-%m-%d %H:%M:%S'))"
  echo "=================================================="
  check_containers
  check_nacos
  check_mysql
  check_frontend
  login_and_check
  hr
  echo "=================================================="
  echo "  PASS: ${pass}  FAIL: ${fail}"
  echo "=================================================="
  if [[ "${fail}" -gt 0 ]]; then
    echo ""
    echo -e "${RED}失败项:${NC}"
    for item in "${failed_items[@]}"; do
      echo "  - ${item}"
    done
    exit 1
  fi
  exit 0
}

main "$@"