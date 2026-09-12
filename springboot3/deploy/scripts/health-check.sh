#!/usr/bin/env bash
# ============================================================
# OPC stack 健康检查 (W48.7 固化)
#
# 用途: 部署后 / 改配置后 / 每周巡检
#
# 验证项:
#   1) 16 个核心容器 Up
#   2) Nacos 注册中心 / 配置中心 健康
#   3) MySQL 抽查
#   4) 前端 nginx 可访问
#   5) 13 个 /opc/** 接口返回 R-code 200 (登录后)
#   6) opc-notification 6 项健康检查 (W49 Task 14)
#   7) opc-crm 8 项健康检查 (W50 Task 17)
#   8) opc-hr 8 项健康检查 (W71 Task 12)
#   9) opc-erp 9 项健康检查 (W72 Task 12)
#
# 输出: PASS=绿, FAIL=红
# ============================================================
set -uo pipefail

# Windows console 默认 GBK, 经 echo | python 管道时多字节 UTF-8 字符会被破坏。
# 强制所有 python 子进程按 UTF-8 解码 stdin (避免 dashboard 等返回中文的接口假阴性)
export PYTHONIOENCODING=utf-8

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

# ---------- 6. opc-notification 健康 ----------
check_notification() {
  hr
  echo "[6] opc-notification 健康检查 (W49 Task 14)"
  hr

  # 6.1 容器 Up
  local c="aiopc-notification"
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

  # 6.2 Nacos 注册 (opc-dev namespace) — Spring 应用名是 opc-notification (不带 aiopc- 前缀)
  local nacos_url="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=opc-notification&namespaceId=opc-dev"
  local nacos_body
  nacos_body=$(curl -sf -m 10 "${nacos_url}" 2>/dev/null) || nacos_body=""
  local healthy_count
  healthy_count=$(echo "${nacos_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    hosts = d.get('hosts', [])
    print(len(hosts) if hosts else 0)
except Exception:
    print(0)
" 2>/dev/null)
  if [[ "${healthy_count}" -ge 1 ]]; then
    ok "Nacos opc-notification 注册 ${healthy_count} 实例 (opc-dev)"
  else
    nok "Nacos opc-notification 未注册: ${nacos_body:-<no response>}"
  fi

  # 6.3 直接 /actuator/health (绕过网关直连 9310)
  local health_body
  health_body=$(curl -sf -m 10 "http://127.0.0.1:9310/actuator/health" 2>/dev/null) || health_body=""
  if [[ "${health_body}" == "UP" ]] || echo "${health_body}" | grep -q '"status":"UP"'; then
    ok "opc-notification /actuator/health UP"
  else
    nok "opc-notification /actuator/health: ${health_body:-<no response>}"
  fi

  # 6.4 业务接口 (需登录)
  local login_body
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  if [[ -z "${login_body}" ]]; then
    warn "登录失败, 跳过 opc-notification 业务接口"
    return
  fi
  local token
  token=$(echo "${login_body}" | python -c "
import sys,json
d = json.load(sys.stdin).get('data', {})
print(d.get('token','') or d.get('access_token',''))
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    warn "未拿到 token, 跳过 opc-notification 业务接口"
    return
  fi

  # 6.4 inbox 列表 (校验 body.code==200, 避免网关静态回退假阳性)
  # 注意: gateway 路由 /opc/notification/** 不带 /prod-api 前缀 (无 StripPrefix 过滤器)
  local inbox_body
  inbox_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/notification/inbox?page=1&pageSize=5" 2>/dev/null) || inbox_body=""
  local inbox_code
  inbox_code=$(echo "${inbox_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${inbox_code}" == "200" ]]; then
    ok "/opc/notification/inbox body.code=200"
  elif echo "${inbox_body}" | grep -q "No static resource"; then
    nok "/opc/notification/inbox 网关未路由 (静态回退): ${inbox_body:0:120}"
  else
    nok "/opc/notification/inbox body.code=${inbox_code}: ${inbox_body:0:120}"
  fi

  # 6.5 unread-count (校验 body.code=200 且 data:<int>)
  local unread_body
  unread_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/notification/inbox/unread-count" 2>/dev/null) || unread_body=""
  if echo "${unread_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    sys.exit(0 if d.get('code')==200 and isinstance(d.get('data'), int) else 1)
except Exception:
    sys.exit(1)
" 2>/dev/null; then
    ok "/opc/notification/inbox/unread-count body.code=200 data:<int> (${unread_body})"
  elif echo "${unread_body}" | grep -q "No static resource"; then
    nok "/opc/notification/inbox/unread-count 网关未路由 (静态回退): ${unread_body:0:120}"
  else
    nok "/opc/notification/inbox/unread-count: ${unread_body:-<no response>}"
  fi

  # 6.6 Gateway 路由解析 — 区分 controller 层静态回退 vs gateway 层静态回退
  #   - 路由解析成功 (controller 层 NoResourceFoundException): JSON, 含 "No static resource", 不含 "404 NOT_FOUND"
  #   - 路由未解析 (gateway 层 NoHandlerFoundException):         JSON, 含 "No static resource", 且含 "404 NOT_FOUND"
  local route_body
  route_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/notification/inbox/__no_such_path__" 2>/dev/null) || route_body=""
  if [[ -n "${route_body}" ]] && echo "${route_body}" | grep -q '^{' \
     && echo "${route_body}" | grep -q "No static resource" \
     && ! echo "${route_body}" | grep -q "404 NOT_FOUND"; then
    ok "Gateway /opc/notification/** 路由到 aiopc-notification (controller JSON 404)"
  elif echo "${route_body}" | grep -q "404 NOT_FOUND"; then
    nok "Gateway /opc/notification/** 未路由 (gateway 静态回退): ${route_body:0:120}"
  else
    nok "Gateway /opc/notification/** 异常响应: ${route_body:0:120}"
  fi
}

# ---------- 7. opc-crm 健康 ----------
check_crm() {
  hr
  echo "[7] opc-crm 健康检查 (W50 Task 17)"
  hr

  # 7.1 容器 Up
  local c="aiopc-crm"
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

  # 7.2 Nacos 注册 (opc-dev namespace)
  local nacos_url="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=opc-crm&namespaceId=opc-dev"
  local nacos_body
  nacos_body=$(curl -sf -m 10 "${nacos_url}" 2>/dev/null) || nacos_body=""
  local healthy_count
  healthy_count=$(echo "${nacos_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    hosts = d.get('hosts', [])
    print(len(hosts) if hosts else 0)
except Exception:
    print(0)
" 2>/dev/null)
  if [[ "${healthy_count}" -ge 1 ]]; then
    ok "Nacos opc-crm 注册 ${healthy_count} 实例 (opc-dev)"
  else
    nok "Nacos opc-crm 未注册: ${nacos_body:-<no response>}"
  fi

  # 7.3 直接 /actuator/health (绕过网关直连 9312)
  local health_body
  health_body=$(curl -sf -m 10 "http://127.0.0.1:9312/actuator/health" 2>/dev/null) || health_body=""
  if [[ "${health_body}" == "UP" ]] || echo "${health_body}" | grep -q '"status":"UP"'; then
    ok "opc-crm /actuator/health UP"
  else
    nok "opc-crm /actuator/health: ${health_body:-<no response>}"
  fi

  # 7.4 业务接口 (需登录)
  local login_body
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  if [[ -z "${login_body}" ]]; then
    warn "登录失败, 跳过 opc-crm 业务接口"
    return
  fi
  local token
  token=$(echo "${login_body}" | python -c "
import sys,json
d = json.load(sys.stdin).get('data', {})
print(d.get('token','') or d.get('access_token',''))
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    warn "未拿到 token, 跳过 opc-crm 业务接口"
    return
  fi

  # 7.4 网关路由 customer list
  # gateway 路由 /opc/crm/** 不带 /prod-api 前缀 (无 StripPrefix 过滤器), 校验 body.code==200
  local customer_body
  customer_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/crm/customer?page=1&pageSize=5" 2>/dev/null) || customer_body=""
  local customer_code
  customer_code=$(echo "${customer_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${customer_code}" == "200" ]]; then
    ok "/opc/crm/customer body.code=200"
  elif echo "${customer_body}" | grep -q "No static resource"; then
    nok "/opc/crm/customer 网关未路由 (静态回退): ${customer_body:0:120}"
  else
    nok "/opc/crm/customer body.code=${customer_code}: ${customer_body:0:120}"
  fi

  # 7.5 网关路由 opportunity list
  local opp_body
  opp_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/crm/opportunity?page=1&pageSize=5" 2>/dev/null) || opp_body=""
  local opp_code
  opp_code=$(echo "${opp_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${opp_code}" == "200" ]]; then
    ok "/opc/crm/opportunity body.code=200"
  elif echo "${opp_body}" | grep -q "No static resource"; then
    nok "/opc/crm/opportunity 网关未路由 (静态回退): ${opp_body:0:120}"
  else
    nok "/opc/crm/opportunity body.code=${opp_code}: ${opp_body:0:120}"
  fi

  # 7.6 网关路由 dashboard (funnel)
  local dash_body
  dash_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/crm/dashboard" 2>/dev/null) || dash_body=""
  local dash_code
  dash_code=$(echo "${dash_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${dash_code}" == "200" ]]; then
    ok "/opc/crm/dashboard body.code=200 (funnel)"
  elif echo "${dash_body}" | grep -q "No static resource"; then
    nok "/opc/crm/dashboard 网关未路由 (静态回退): ${dash_body:0:120}"
  else
    nok "/opc/crm/dashboard body.code=${dash_code}: ${dash_body:0:120}"
  fi

  # 7.7 contract list (admin customerId=1 should be valid)
  local contract_body
  contract_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/crm/contract?customerId=1&page=1&pageSize=5" 2>/dev/null) || contract_body=""
  local contract_code
  contract_code=$(echo "${contract_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${contract_code}" == "200" ]]; then
    ok "/opc/crm/contract body.code=200"
  elif echo "${contract_body}" | grep -q "No static resource"; then
    nok "/opc/crm/contract 网关未路由 (静态回退): ${contract_body:0:120}"
  else
    nok "/opc/crm/contract body.code=${contract_code}: ${contract_body:0:120}"
  fi

  # 7.8 order list
  local order_body
  order_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/crm/order?customerId=1&page=1&pageSize=5" 2>/dev/null) || order_body=""
  local order_code
  order_code=$(echo "${order_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${order_code}" == "200" ]]; then
    ok "/opc/crm/order body.code=200"
  elif echo "${order_body}" | grep -q "No static resource"; then
    nok "/opc/crm/order 网关未路由 (静态回退): ${order_body:0:120}"
  else
    nok "/opc/crm/order body.code=${order_code}: ${order_body:0:120}"
  fi
}

# ---------- 8. opc-hr 健康 ----------
check_hr() {
  hr
  echo "[8] opc-hr 健康检查 (W71 Task 12)"
  hr

  # 8.1 容器 Up
  local c="aiopc-hr"
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

  # 8.2 Nacos 注册 (opc-dev namespace)
  local nacos_url="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=opc-hr&namespaceId=opc-dev"
  local nacos_body
  nacos_body=$(curl -sf -m 10 "${nacos_url}" 2>/dev/null) || nacos_body=""
  local healthy_count
  healthy_count=$(echo "${nacos_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    hosts = d.get('hosts', [])
    print(len(hosts) if hosts else 0)
except Exception:
    print(0)
" 2>/dev/null)
  if [[ "${healthy_count}" -ge 1 ]]; then
    ok "Nacos opc-hr 注册 ${healthy_count} 实例 (opc-dev)"
  else
    nok "Nacos opc-hr 未注册: ${nacos_body:-<no response>}"
  fi

  # 8.3 直接 /actuator/health (绕过网关直连 9322)
  local health_body
  health_body=$(curl -sf -m 10 "http://127.0.0.1:9322/actuator/health" 2>/dev/null) || health_body=""
  if [[ "${health_body}" == "UP" ]] || echo "${health_body}" | grep -q '"status":"UP"'; then
    ok "opc-hr /actuator/health UP"
  else
    nok "opc-hr /actuator/health: ${health_body:-<no response>}"
  fi

  # 8.4 业务接口 (需登录,复用 admin)
  local login_body
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  if [[ -z "${login_body}" ]]; then
    warn "登录失败, 跳过 opc-hr 业务接口"
    return
  fi
  local token
  token=$(echo "${login_body}" | python -c "
import sys,json
d = json.load(sys.stdin).get('data', {})
print(d.get('token','') or d.get('access_token',''))
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    warn "未拿到 token, 跳过 opc-hr 业务接口"
    return
  fi

  # 8.5 gateway /opc/hr/dashboard (funnel)
  local dash_body
  dash_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/hr/dashboard?companyId=1" 2>/dev/null) || dash_body=""
  local dash_code
  dash_code=$(echo "${dash_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${dash_code}" == "200" ]]; then
    ok "/opc/hr/dashboard body.code=200 (funnel/conversion/avg/job_status)"
  elif echo "${dash_body}" | grep -q "No static resource"; then
    nok "/opc/hr/dashboard 网关未路由 (静态回退): ${dash_body:0:120}"
  else
    nok "/opc/hr/dashboard body.code=${dash_code}: ${dash_body:0:120}"
  fi

  # 8.6 gateway /opc/hr/job/list
  local job_body
  job_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/hr/job/list?companyId=1" 2>/dev/null) || job_body=""
  local job_code
  job_code=$(echo "${job_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${job_code}" == "200" ]]; then
    ok "/opc/hr/job/list body.code=200"
  elif echo "${job_body}" | grep -q "No static resource"; then
    nok "/opc/hr/job/list 网关未路由 (静态回退): ${job_body:0:120}"
  else
    nok "/opc/hr/job/list body.code=${job_code}: ${job_body:0:120}"
  fi

  # 8.7 gateway /opc/hr/candidate/list
  local cand_body
  cand_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/hr/candidate/list?companyId=1" 2>/dev/null) || cand_body=""
  local cand_code
  cand_code=$(echo "${cand_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${cand_code}" == "200" ]]; then
    ok "/opc/hr/candidate/list body.code=200"
  elif echo "${cand_body}" | grep -q "No static resource"; then
    nok "/opc/hr/candidate/list 网关未路由 (静态回退): ${cand_body:0:120}"
  else
    nok "/opc/hr/candidate/list body.code=${cand_code}: ${cand_body:0:120}"
  fi

  # 8.8 gateway /opc/hr/application/list
  local app_body
  app_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/hr/application/list?companyId=1" 2>/dev/null) || app_body=""
  local app_code
  app_code=$(echo "${app_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${app_code}" == "200" ]]; then
    ok "/opc/hr/application/list body.code=200"
  elif echo "${app_body}" | grep -q "No static resource"; then
    nok "/opc/hr/application/list 网关未路由 (静态回退): ${app_body:0:120}"
  else
    nok "/opc/hr/application/list body.code=${app_code}: ${app_body:0:120}"
  fi
}

# ---------- 9. opc-erp 健康 ----------
check_erp() {
  hr
  echo "[9] opc-erp 健康检查 (W72 Task 12)"
  hr

  # 9.1 容器 Up
  local c="aiopc-erp"
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

  # 9.2 Nacos 注册 (opc-dev namespace)
  local nacos_url="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=opc-erp&namespaceId=opc-dev"
  local nacos_body
  nacos_body=$(curl -sf -m 10 "${nacos_url}" 2>/dev/null) || nacos_body=""
  local healthy_count
  healthy_count=$(echo "${nacos_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    hosts = d.get('hosts', [])
    print(len(hosts) if hosts else 0)
except Exception:
    print(0)
" 2>/dev/null)
  if [[ "${healthy_count}" -ge 1 ]]; then
    ok "Nacos opc-erp 注册 ${healthy_count} 实例 (opc-dev)"
  else
    nok "Nacos opc-erp 未注册: ${nacos_body:-<no response>}"
  fi

  # 9.3 直接 /actuator/health (绕过网关直连 9311)
  local health_body
  health_body=$(curl -sf -m 10 "http://127.0.0.1:9311/actuator/health" 2>/dev/null) || health_body=""
  if [[ "${health_body}" == "UP" ]] || echo "${health_body}" | grep -q '"status":"UP"'; then
    ok "opc-erp /actuator/health UP"
  else
    nok "opc-erp /actuator/health: ${health_body:-<no response>}"
  fi

  # 9.4 业务接口 (需登录,复用 admin)
  local login_body
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  if [[ -z "${login_body}" ]]; then
    warn "登录失败, 跳过 opc-erp 业务接口"
    return
  fi
  local token
  token=$(echo "${login_body}" | python -c "
import sys,json
d = json.load(sys.stdin).get('data', {})
print(d.get('token','') or d.get('access_token',''))
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    warn "未拿到 token, 跳过 opc-erp 业务接口"
    return
  fi

  # 9.5 gateway /opc/erp/product/list
  local product_body
  product_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/erp/product/list?companyId=1" 2>/dev/null) || product_body=""
  local product_code
  product_code=$(echo "${product_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${product_code}" == "200" ]]; then
    ok "/opc/erp/product/list body.code=200"
  elif echo "${product_body}" | grep -q "No static resource"; then
    nok "/opc/erp/product/list 网关未路由 (静态回退): ${product_body:0:120}"
  else
    nok "/opc/erp/product/list body.code=${product_code}: ${product_body:0:120}"
  fi

  # 9.6 gateway /opc/erp/product/product-sku/list (公开白名单)
  local sku_body
  sku_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/erp/product/product-sku/list?companyId=1" 2>/dev/null) || sku_body=""
  local sku_code
  sku_code=$(echo "${sku_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${sku_code}" == "200" ]]; then
    ok "/opc/erp/product/product-sku/list body.code=200 (公开)"
  elif echo "${sku_body}" | grep -q "No static resource"; then
    nok "/opc/erp/product/product-sku/list 网关未路由 (静态回退): ${sku_body:0:120}"
  else
    nok "/opc/erp/product/product-sku/list body.code=${sku_code}: ${sku_body:0:120}"
  fi

  # 9.7 gateway /opc/erp/purchase/list
  local purchase_body
  purchase_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/erp/purchase/list?companyId=1" 2>/dev/null) || purchase_body=""
  local purchase_code
  purchase_code=$(echo "${purchase_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${purchase_code}" == "200" ]]; then
    ok "/opc/erp/purchase/list body.code=200"
  elif echo "${purchase_body}" | grep -q "No static resource"; then
    nok "/opc/erp/purchase/list 网关未路由 (静态回退): ${purchase_body:0:120}"
  else
    nok "/opc/erp/purchase/list body.code=${purchase_code}: ${purchase_body:0:120}"
  fi

  # 9.8 gateway /opc/erp/sale/list
  local sale_body
  sale_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/erp/sale/list?companyId=1" 2>/dev/null) || sale_body=""
  local sale_code
  sale_code=$(echo "${sale_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${sale_code}" == "200" ]]; then
    ok "/opc/erp/sale/list body.code=200"
  elif echo "${sale_body}" | grep -q "No static resource"; then
    nok "/opc/erp/sale/list 网关未路由 (静态回退): ${sale_body:0:120}"
  else
    nok "/opc/erp/sale/list body.code=${sale_code}: ${sale_body:0:120}"
  fi

  # 9.9 gateway /opc/erp/supplier/list
  local supplier_body
  supplier_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/erp/supplier/list?companyId=1" 2>/dev/null) || supplier_body=""
  local supplier_code
  supplier_code=$(echo "${supplier_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${supplier_code}" == "200" ]]; then
    ok "/opc/erp/supplier/list body.code=200"
  elif echo "${supplier_body}" | grep -q "No static resource"; then
    nok "/opc/erp/supplier/list 网关未路由 (静态回退): ${supplier_body:0:120}"
  else
    nok "/opc/erp/supplier/list body.code=${supplier_code}: ${supplier_body:0:120}"
  fi

  # 9.10 gateway /opc/erp/inventory/low-stock (公开白名单)
  local low_body
  low_body=$(curl -s -m 10 \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/erp/inventory/low-stock?companyId=1" 2>/dev/null) || low_body=""
  local low_code
  low_code=$(echo "${low_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${low_code}" == "200" ]]; then
    ok "/opc/erp/inventory/low-stock body.code=200 (公开)"
  elif echo "${low_body}" | grep -q "No static resource"; then
    nok "/opc/erp/inventory/low-stock 网关未路由 (静态回退): ${low_body:0:120}"
  else
    nok "/opc/erp/inventory/low-stock body.code=${low_code}: ${low_body:0:120}"
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
  check_notification
  check_crm
  check_hr
  check_erp
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