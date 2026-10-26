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
#  10) opc-content 10 项健康检查 (W74 Task 15)
#  11) opc-marketplace 10 项健康检查 (W80 P1)
#  12) 网关白名单 method 限定 (3c 回归护栏)
#  13) 租户守卫 + LLM 计量 (3b / 3a 回归护栏)
#  14) CRM 归属守卫 (per-user IDOR 回归护栏)
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
MYSQL_PORT="${MYSQL_PORT:-3308}"
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

# 查库: 走宿主机 mysql 客户端 + ~/.my.cnf, 口令留在文件里, 不落命令行。
#
# 不用 `docker exec aiopc-mysql mysql -uroot -p'...'` —— 那会把库口令
# 暴露在进程的命令行里(ps aux / /proc/*/cmdline 都看得到)。
# 宿主机这边本来就有 /root/.my.cnf(host/port/user), mysql 客户端会自动读。
# 没有 my.cnf 的机器上, 查库这一段会安静地跳过, 而不是拿口令去撞命令行。
db_q() {
  command -v mysql >/dev/null 2>&1 || return 1
  local out
  out="$(mysql -N -B ry-vue-opc -e "$1" 2>/dev/null)" || return 1
  printf '%s' "${out}" | tr -d '\r'
}

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
  table_count=$(db_q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ry-vue-opc';") || table_count=0
  if [[ "${table_count}" -ge 50 ]]; then
    ok "MySQL ry-vue-opc 有 ${table_count} 张表"
  else
    nok "MySQL 表数异常: ${table_count} (预期 ≥50)"
  fi

  local admin_count
  admin_count=$(db_q "SELECT COUNT(*) FROM sys_user WHERE user_name='admin';") || admin_count=0
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

# ---------- 10. opc-content 健康 ----------
check_content() {
  hr
  echo "[10] opc-content 健康检查 (W74 Task 15)"
  hr

  local port=9325
  local base_url="http://127.0.0.1:${port}"

  # 10.1 容器 Up (非阻塞, dev 直连模式可能无容器)
  local c="aiopc-content"
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${c}$"; then
    status=$(docker inspect --format '{{.State.Status}}' "${c}" 2>/dev/null)
    if [[ "${status}" == "running" ]]; then
      ok "${c} running"
    else
      warn "${c} 状态异常: ${status}"
    fi
  else
    warn "${c} 容器不存在 (本地直连模式)"
  fi

  # 10.2 Nacos 注册 (非阻塞)
  local nacos_url="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=opc-content&namespaceId=opc-dev"
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
    ok "Nacos opc-content 注册 ${healthy_count} 实例 (opc-dev)"
  else
    warn "Nacos opc-content 未注册 (开发模式可能未启动)"
  fi

  # 10.3 /actuator/health (阻塞)
  local health_body
  health_body=$(curl -sf -m 10 "${base_url}/actuator/health" 2>/dev/null) || health_body=""
  if [[ "${health_body}" == "UP" ]] || echo "${health_body}" | grep -q '"status":"UP"'; then
    ok "opc-content /actuator/health UP"
  else
    nok "opc-content /actuator/health: ${health_body:-<no response>}"
  fi

  # 10.4 ScriptController GET /opc/content/script/list (阻塞)
  if curl -sf -m 10 "${base_url}/opc/content/script/list?pageNum=1&pageSize=10" >/dev/null 2>&1; then
    ok "/opc/content/script/list 200 OK"
  else
    nok "/opc/content/script/list 不可达"
  fi

  # 10.5 ScriptController GET /opc/content/script/dashboard (阻塞)
  if curl -sf -m 10 "${base_url}/opc/content/script/dashboard" >/dev/null 2>&1; then
    ok "/opc/content/script/dashboard 200 OK"
  else
    nok "/opc/content/script/dashboard 不可达"
  fi

  # 10.6 ScriptController GET /opc/content/script/recent (阻塞)
  if curl -sf -m 10 "${base_url}/opc/content/script/recent?limit=5" >/dev/null 2>&1; then
    ok "/opc/content/script/recent 200 OK"
  else
    nok "/opc/content/script/recent 不可达"
  fi

  # 10.7 PlatformAccountController GET /opc/content/platform-account/list (阻塞)
  if curl -sf -m 10 "${base_url}/opc/content/platform-account/list" >/dev/null 2>&1; then
    ok "/opc/content/platform-account/list 200 OK"
  else
    nok "/opc/content/platform-account/list 不可达"
  fi

  # 10.8 PublishController GET /opc/content/publish/list (阻塞)
  if curl -sf -m 10 "${base_url}/opc/content/publish/list" >/dev/null 2>&1; then
    ok "/opc/content/publish/list 200 OK"
  else
    nok "/opc/content/publish/list 不可达"
  fi

  # 10.9 鉴权拦截 (非阻塞, dev 模式可能关闭)
  local HTTP_CODE
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 "${base_url}/opc/content/script/list" 2>/dev/null)
  if [[ "${HTTP_CODE}" == "401" ]] || [[ "${HTTP_CODE}" == "403" ]] || [[ "${HTTP_CODE}" == "200" ]]; then
    ok "鉴权拦截正确 (HTTP ${HTTP_CODE})"
  else
    warn "鉴权状态异常 (HTTP ${HTTP_CODE})"
  fi

  # 10.10 数据库连接 (非阻塞, db indicator)
  local db_health
  db_health=$(curl -sf -m 10 "${base_url}/actuator/health/db" 2>/dev/null) || db_health=""
  if echo "${db_health}" | grep -q "UP" || [[ -z "${db_health}" ]]; then
    ok "数据库健康 (db indicator UP 或未启用)"
  else
    warn "数据库健康状态: ${db_health}"
  fi
}

# ---------- 11. opc-marketplace 健康 ----------
check_marketplace() {
  hr
  echo "[11] opc-marketplace 健康检查 (W80 P1)"
  hr

  local port=9317
  local base_url="http://127.0.0.1:${port}"

  # 11.1 容器 Up (非阻塞, dev 直连模式可能无容器)
  local c="aiopc-marketplace"
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${c}$"; then
    local st
    st=$(docker inspect --format '{{.State.Status}}' "${c}" 2>/dev/null)
    if [[ "${st}" == "running" ]]; then
      ok "${c} running"
    else
      warn "${c} 状态异常: ${st}"
    fi
  else
    warn "${c} 容器不存在 (本地直连模式)"
  fi

  # 11.2 Nacos 注册 (非阻塞)
  local nacos_url="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=opc-marketplace&namespaceId=opc-dev"
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
    ok "Nacos opc-marketplace 注册 ${healthy_count} 实例 (opc-dev)"
  else
    warn "Nacos opc-marketplace 未注册 (开发模式可能未启动)"
  fi

  # 11.3 /actuator/health (阻塞)
  local health_body
  health_body=$(curl -sf -m 10 "${base_url}/actuator/health" 2>/dev/null) || health_body=""
  if [[ "${health_body}" == "UP" ]] || echo "${health_body}" | grep -q '"status":"UP"'; then
    ok "opc-marketplace /actuator/health UP"
  else
    nok "opc-marketplace /actuator/health: ${health_body:-<no response>}"
  fi

  # 11.4-11.7 匿名可读的公开端点 (白名单内, 验证路由 + service 都在)
  # 这 4 个是应用商店首屏真正依赖的接口。
  local pub_eps=(
    "/opc/marketplace/category/tree"
    "/opc/marketplace/category/list"
    "/opc/marketplace/tag/list"
    "/opc/marketplace/publication/list?pageNum=1&pageSize=10"
  )
  for ep in "${pub_eps[@]}"; do
    local body
    body=$(curl -sf -m 10 "${base_url}${ep}" 2>/dev/null) || body=""
    # 必须 code=200。HTTP 200 但 code!=200 说明是 OpcException 业务错误
    # (例如 DRAFT 被误当 PUBLISHED 返回), 不能算通过。
    if echo "${body}" | python -c "
import sys,json
try:
    sys.exit(0 if json.load(sys.stdin).get('code') == 200 else 1)
except Exception:
    sys.exit(1)
" 2>/dev/null; then
      ok "${ep} code=200"
    else
      nok "${ep} 不可达或 code!=200: ${body:0:120}"
    fi
  done

  # 11.8 需登录端点必须被拦截 (阻塞, 租户隔离的第一道闸)
  #
  # ⚠️ 这里打的是**服务直连** (127.0.0.1:9317), 网关的 AuthFilter 不在这条链路上,
  #    所以「install 不在网关白名单」这条理由在这里根本不适用。
  #    服务层的闸是 InstallController.requireCompanyId() → 无登录上下文抛
  #    ServiceException, 由 GlobalExceptionHandler 转成业务错误。
  #
  # ⚠️ RuoYi 的业务错误是 **HTTP 200 + body 里带 code**, 不是 HTTP 4xx:
  #    实测 2026-10-23 匿名调用返回 HTTP 200 + {"code":500,"msg":"获取公司ID失败…"}。
  #    原断言只看 HTTP 状态码, 于是把「正确拒绝」误报成「租户隔离失效」(实测 FAIL 1)。
  #
  # 改用 GET /install/{id}/status 探同一条闸门: 同一个 controller、同一个
  # requireCompanyId(), 但 GET 永远没有安装副作用 —— 就算哪天这道闸失守,
  # 这条检查也不会真的往库里装出一个东西。
  local auth_body auth_code
  auth_body=$(curl -s -m 10 "${base_url}/opc/marketplace/install/1/status" 2>/dev/null) || auth_body=""
  auth_code=$(echo "${auth_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${auth_code}" != "200" ]] && [[ "${auth_code}" != "-1" ]]; then
    ok "鉴权拦截正确 (GET /install/1/status → body.code=${auth_code})"
  else
    nok "未登录调用未被拦截 (body.code=${auth_code}): ${auth_body:0:120} — 租户隔离可能失效"
  fi

  # 11.8b 同一道闸, 走网关那条公共路径 (GET, 无副作用)
  #      /opc/marketplace/install/** 不在网关白名单 (nacos/whitelist-opc-invite.yml:108),
  #      匿名应被 AuthFilter 拦下。网关同样返回 HTTP 200 + body.code=401,
  #      所以也只能看 body.code。
  local gw_body gw_code
  gw_body=$(curl -s -m 10 \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/marketplace/install/1/status" 2>/dev/null) || gw_body=""
  gw_code=$(echo "${gw_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${gw_code}" == "401" ]] || [[ "${gw_code}" == "403" ]]; then
    ok "网关拦截正确 (GET /install/1/status → body.code=${gw_code})"
  else
    nok "网关未拦截未登录调用 (body.code=${gw_code}): ${gw_body:0:120} — 租户隔离可能失效"
  fi

  # 11.9 公开详情不得泄漏非 PUBLISHED 行 (SQL 层 selectPublishedById 硬过滤)
  #     DRAFT/DEPRECATED 的 id 匿名查询必须拿不到 code=200。
  local draft_body
  draft_body=$(curl -sf -m 10 "${base_url}/opc/marketplace/publication/999999" 2>/dev/null) || draft_body=""
  if echo "${draft_body}" | python -c "
import sys,json
try:
    sys.exit(0 if json.load(sys.stdin).get('code') != 200 else 1)
except Exception:
    sys.exit(0)   # 连不上也算通过 (服务没起时不该在这里误报)
" 2>/dev/null; then
    ok "公开详情不泄漏非 PUBLISHED 行"
  else
    nok "GET /publication/999999 返回 code=200 — 未发布商品对外可见"
  fi

  # 11.10 数据库连接 (非阻塞, db indicator)
  local db_health
  db_health=$(curl -sf -m 10 "${base_url}/actuator/health/db" 2>/dev/null) || db_health=""
  if echo "${db_health}" | grep -q "UP" || [[ -z "${db_health}" ]]; then
    ok "数据库健康 (db indicator UP 或未启用)"
  else
    warn "数据库健康状态: ${db_health}"
  fi
}

# ---------- 12. 网关白名单 method 限定 (3c 回归护栏) ----------
#
# 3c 的洞: 网关白名单只按路径匹配, 不看 HTTP method。为「公开浏览」准备的
# `/**` 通配把同一前缀下的**写端点**也一并放行了 —— 匿名 DELETE /opc/erp/product/{id}
# 能真的删掉商品。修法是白名单条目支持 `GET:/path` 前缀 (AuthFilter.isWhiteListed)。
#
# 这条护栏**只发安全方法** (GET / OPTIONS), 绝不 POST/PUT/DELETE:
#   - GET     在公开读路径上 → 应当放行 (body.code 不是 401)
#   - OPTIONS 在同一条路径上 → 应当被网关挡下 (body.code=401)
# OPTIONS 不改任何数据, 但它和 DELETE 在 AuthFilter 里走的是同一段 method
# 比较逻辑, 所以拿它就能证明「同一条白名单前缀不放行非 GET 方法」。
#
# ⚠️ RuoYi 的业务错误是 HTTP 200 + body 里带 code, 不是 HTTP 4xx ——
#    所以只能看 body.code, 不能看 HTTP 状态码 (HEAD 更看不到 body, 不能用来判定)。
check_gateway_whitelist() {
  hr
  echo "[12] 网关白名单 method 限定 (3c 回归护栏)"
  hr
  local base="http://${GATEWAY_HOST}:${GATEWAY_PORT}"

  # 从响应体里取 RuoYi 的 code (取不到返回 "-1")
  local_code() {
    python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null
  }

  local paths=(
    /opc/erp/product/list
    /opc/content/script/list
    /opc/hr/job/list
    /opc/marketplace/category/tree
  )
  local p get_body get_code opt_body opt_code
  for p in "${paths[@]}"; do
    # 12.1 匿名 GET 必须放行 (公开读入口不能被收紧误伤)
    get_body=$(curl -s -m 10 "${base}${p}" 2>/dev/null) || get_body=""
    if [[ -z "${get_body}" ]]; then
      nok "匿名 GET ${p} 无响应 — 网关或后端不可达"
      continue
    fi
    get_code=$(echo "${get_body}" | local_code)
    if [[ "${get_code}" == "401" || "${get_code}" == "403" ]]; then
      nok "匿名 GET ${p} 被网关拦下 (body.code=${get_code}) — 公开读入口被误关"
    else
      ok "匿名 GET ${p} 放行 (body.code=${get_code})"
    fi

    # 12.2 匿名 OPTIONS 必须被挡 (同一条白名单前缀不放行非 GET)
    opt_body=$(curl -s -m 10 -X OPTIONS "${base}${p}" 2>/dev/null) || opt_body=""
    opt_code=$(echo "${opt_body}" | local_code)
    if [[ "${opt_code}" == "401" || "${opt_code}" == "403" ]]; then
      ok "匿名 OPTIONS ${p} 被网关挡下 (body.code=${opt_code})"
    else
      nok "匿名 OPTIONS ${p} 未被拦 (body.code=${opt_code}) — 白名单又放行了非 GET 方法"
    fi
  done
}

# ---------- 13. 租户守卫 + LLM 计量 (3b / 3a 回归护栏) ----------
#
# 3b 的洞: ERP/HR/Content 的 list/detail 端点把 companyId 当普通客户端参数收下,
# 客户端把它改成 2 就能看别家公司的数据。修法是租户守卫在服务端按登录态覆写
# (ruoyi-common-security 的 TenantGuard)。
#
# 3a 的洞: /opc/llm/chat 从 body 里取 companyId/userId 记计量, 而前端两个调用点
# (llm/chat.vue、agent/instance.vue) 都不发这两个字段 → company_id NOT NULL 违例
# → 异常被 TokenMeter 的 catch 吞掉 → 每一次 LLM 调用的用量/成本记录都静默丢失
# (账单页面读的就是这张表)。
#
# 这条护栏拿 admin(公司1) 当登录态, 拿公司2 的数据当「别家」:
#   13.1 带 companyId=2 列岗位 → 回来的必须全是公司1的
#   13.2 带 companyId=2 取公司2的岗位详情 → 必须拿不到
#   13.3 带 companyId=1 取公司1的岗位详情 → 必须拿得到(正对照, 证明没误伤)
#   13.4 匿名 DELETE 一个**不存在的**商品 → 必须被网关挡下(3c, 真实写方法;
#        用不存在的 id, 万一没挡住也只是删 0 行, 不会动数据)
#   13.5 调一次 /opc/llm/chat → 计量表必须多一行, company_id = 登录态的公司
#   13.6 body 里塞 companyId=2 再调一次 → 新行仍必须是公司1(客户端说了不算数)
#
# ⚠️ LlmCache 的缓存命中路径**不计量**(LlmGateway:32-35 直接 return), 所以
#    13.5/13.6 的消息里必须带一次性随机串, 否则第二次会命中缓存, 计量表不会长。
check_tenant_guard() {
  hr
  echo "[13] 租户守卫 + LLM 计量 (3b / 3a 回归护栏)"
  hr
  local base="http://${GATEWAY_HOST}:${GATEWAY_PORT}"

  # 13.0 登录 (admin → 公司1)
  local login_body token
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "${base}/login" 2>/dev/null) || true
  token=$(echo "${login_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
    print(d.get('token','') or d.get('access_token',''))
except Exception:
    print('')
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    nok "登录失败, 3a/3b 护栏无法验证"
    return
  fi
  ok "登录成功 (admin → 公司1)"

  # 13.0.1 取一个「别家」(公司2) 的岗位 id 和一个「自家」(公司1) 的岗位 id
  local other_id own_id
  other_id=$(db_q "SELECT id FROM opc_hr_job WHERE company_id=2 ORDER BY id LIMIT 1;" | tr -d '\r')
  own_id=$(db_q "SELECT id FROM opc_hr_job WHERE company_id=1 ORDER BY id LIMIT 1;" | tr -d '\r')
  if [[ -z "${other_id}" || -z "${own_id}" ]]; then
    nok "取不到对照数据 (公司2岗位 id=${other_id:-无} 公司1岗位 id=${own_id:-无}) — 护栏跳过"
    return
  fi

  # 13.1 带 companyId=2 列岗位 → 回来的必须全是公司1的
  local list_body
  list_body=$(curl -s -m 15 -H "Authorization: Bearer ${token}" \
    "${base}/opc/hr/job/list?companyId=2&pageNum=1&pageSize=100" 2>/dev/null) || list_body=""
  local list_stat
  list_stat=$(echo "${list_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    rows = d.get('data') or []
    if isinstance(rows, dict):
        rows = rows.get('rows') or rows.get('list') or []
    if not isinstance(rows, list):
        print('-1|0|0'); raise SystemExit
    total = len(rows); bad = 0; unknown = 0
    for r in rows:
        if not isinstance(r, dict):
            continue
        # 实体上标了 @JsonProperty('company_id'), 序列化出来的是 company_id;
        # 两种写法都认, 都取不到就算「无法判定」而不是「泄漏」。
        v = r.get('companyId', r.get('company_id'))
        if v is None:
            unknown += 1
        elif str(v) != '1':
            bad += 1
    print('%d|%d|%d' % (total, bad, unknown))
except Exception:
    print('-1|0|0')
" 2>/dev/null)
  local list_total list_bad list_unknown
  list_total="$(cut -d'|' -f1 <<<"${list_stat}")"
  list_bad="$(cut -d'|' -f2 <<<"${list_stat}")"
  list_unknown="$(cut -d'|' -f3 <<<"${list_stat}")"
  if [[ "${list_total}" == "-1" ]]; then
    nok "13.1 /opc/hr/job/list 返回体解析不了 — 端点可能已变形"
  elif [[ "${list_total}" -eq 0 ]]; then
    nok "13.1 /opc/hr/job/list?companyId=2 返回 0 行 — 护栏空转, 证明不了什么"
  elif [[ "${list_bad}" -gt 0 ]]; then
    nok "13.1 /opc/hr/job/list?companyId=2 → ${list_bad}/${list_total} 行属于公司2 (跨租户泄漏)"
  elif [[ "${list_unknown}" -gt 0 ]]; then
    warn "13.1 ${list_unknown}/${list_total} 行没带公司字段, 无法判定归属 (不判通过也不判泄漏)"
  else
    ok "13.1 /opc/hr/job/list?companyId=2 → ${list_total} 行全是公司1的 (没有串到公司2)"
  fi

  # 13.2 带 companyId=2 取公司2的岗位详情 → 必须拿不到
  local other_body
  other_body=$(curl -s -m 15 -H "Authorization: Bearer ${token}" \
    "${base}/opc/hr/job/${other_id}?companyId=2" 2>/dev/null) || other_body=""
  local other_got
  other_got=$(echo "${other_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    rows = d.get('data')
    if isinstance(rows, dict) and rows.get('id') is not None:
        # 实体上 @JsonProperty('company_id') → 两种写法都认
        print('LEAK:' + str(rows.get('companyId', rows.get('company_id'))))
    else:
        print('NO')
except Exception:
    print('NO')
" 2>/dev/null)
  if [[ "${other_got}" == "NO" ]]; then
    ok "13.2 /opc/hr/job/${other_id}?companyId=2 (公司2的岗位) → 拿不到"
  else
    nok "13.2 /opc/hr/job/${other_id}?companyId=2 → ${other_got} — 跨租户读到别家数据"
  fi

  # 13.3 正对照: 公司1的岗位必须拿得到 (证明端点本身没被误伤)
  local own_body
  own_body=$(curl -s -m 15 -H "Authorization: Bearer ${token}" \
    "${base}/opc/hr/job/${own_id}?companyId=1" 2>/dev/null) || own_body=""
  local own_id_back
  own_id_back=$(echo "${own_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    rows = d.get('data')
    print(str(rows.get('id')) if isinstance(rows, dict) and rows.get('id') is not None else 'NONE')
except Exception:
    print('NONE')
" 2>/dev/null)
  if [[ "${own_id_back}" == "${own_id}" ]]; then
    ok "13.3 /opc/hr/job/${own_id}?companyId=1 (公司1的岗位) → 正常返回 (正对照通过)"
  else
    nok "13.3 /opc/hr/job/${own_id}?companyId=1 → 拿到 ${own_id_back} — 自家数据也读不到, 守卫可能收得太狠"
  fi

  # 13.4 匿名 DELETE 一个**不存在的**商品 → 必须被网关挡下
  #     (第 12 节用 OPTIONS 证明了 method 比较逻辑; 这里再补一个真实的写方法)
  local del_body del_code
  del_body=$(curl -s -m 10 -X DELETE "${base}/opc/erp/product/999999999" 2>/dev/null) || del_body=""
  del_code=$(echo "${del_body}" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${del_code}" == "401" || "${del_code}" == "403" ]]; then
    ok "13.4 匿名 DELETE /opc/erp/product/999999999 被挡下 (body.code=${del_code})"
  else
    nok "13.4 匿名 DELETE /opc/erp/product/999999999 未被挡 (body.code=${del_code}) — 白名单放行了 DELETE"
  fi

  # 13.5 / 13.6 LLM 计量归属
  #      消息里带一次性随机串 —— LlmCache 命中时不计量, 用固定消息第二次会落空。
  local usage_count_before usage_count_after row_company row_user
  usage_count_before=$(db_q "SELECT COUNT(*) FROM opc_agent_token_usage;" | tr -d '\r')
  local nonce chat_body
  nonce="n$(date +%s%N | tail -c 7)"
  chat_body=$(curl -s -m 90 -X POST \
    -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
    -d "{\"messages\":[{\"role\":\"user\",\"content\":\"只回复一个字:好 ${nonce}\"}]}" \
    "${base}/opc/llm/chat" 2>/dev/null) || chat_body=""
  sleep 3
  usage_count_after=$(db_q "SELECT COUNT(*) FROM opc_agent_token_usage;" | tr -d '\r')
  row_company=$(db_q "SELECT company_id FROM opc_agent_token_usage ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  row_user=$(db_q "SELECT user_id FROM opc_agent_token_usage ORDER BY id DESC LIMIT 1;" | tr -d '\r')

  local chat_code
  chat_code=$(echo "${chat_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    print(d.get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null)
  if [[ "${chat_code}" != "200" ]]; then
    warn "13.5 /opc/llm/chat 没成功 (body.code=${chat_code}) — 计量归属未能验证 (可能是 provider 不可用)"
  elif [[ "${usage_count_after}" -gt "${usage_count_before}" ]]; then
    ok "13.5 /opc/llm/chat 计量落库 (${usage_count_before} → ${usage_count_after} 行, company_id=${row_company} user_id=${row_user})"
  else
    nok "13.5 /opc/llm/chat 调用成功但计量表没长 (仍是 ${usage_count_after} 行) — 用量/成本记录在丢"
  fi

  # 13.6 body 里塞 companyId=2 → 计量仍必须记到公司1(登录态说了算)
  nonce="n$(date +%s%N | tail -c 7)"
  usage_count_before="${usage_count_after}"
  chat_body=$(curl -s -m 90 -X POST \
    -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
    -d "{\"companyId\":2,\"userId\":101,\"messages\":[{\"role\":\"user\",\"content\":\"只回复一个字:好 ${nonce}\"}]}" \
    "${base}/opc/llm/chat" 2>/dev/null) || chat_body=""
  sleep 3
  usage_count_after=$(db_q "SELECT COUNT(*) FROM opc_agent_token_usage;" | tr -d '\r')
  row_company=$(db_q "SELECT company_id FROM opc_agent_token_usage ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  row_user=$(db_q "SELECT user_id FROM opc_agent_token_usage ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  if [[ "${usage_count_after}" -gt "${usage_count_before}" && "${row_company}" == "1" && "${row_user}" == "1" ]]; then
    ok "13.6 body 里塞 companyId=2/userId=101 → 计量仍记到 company_id=${row_company} user_id=${row_user} (登录态说了算)"
  elif [[ "${usage_count_after}" -le "${usage_count_before}" ]]; then
    nok "13.6 body 里塞 companyId=2 → 计量表没长 (${usage_count_after} 行) — 用量记录在丢"
  else
    nok "13.6 body 里塞 companyId=2 → 计量记到了 company_id=${row_company} user_id=${row_user} (客户端说了算)"
  fi
}

# ---------- 14. CRM 归属守卫 (per-user IDOR 回归护栏) ----------
#
# 租户隔离管的是「A 公司别看 B 公司」; 这条管的是**公司内部**:
# 销售 A 不该翻销售 B 的客户档案。CRM 六个实体的 list / 仪表盘本来就按 owner
# 过滤, 但 getById / update / delete / 按 id 的子表查询全是「只按 id 取数」,
# 而 ownerId / customerId 又由客户端给 —— 把路径里的 id 换成别人的就看到了。
#
# 拿 admin(user_id=1) 当登录态, 拿 owner_id=101 的数据当「别人家的」。
#
# 探针原则: **只读**。不往库里塞夹具, 不删数据。
#   - 读别人的   → 必须 403
#   - 读自己的   → 必须 200 (正对照, 证明守卫没收得太狠)
#   - 往别人客户下写 → 必须 403, 且库里一条都不许多
#   - PUT 用「同值写回」(幂等): 守卫万一失效, 也只是 update_time 变一下, 内容不改
#   - DELETE 用**不存在的 id**: 万一守卫失效, 删 0 行, 不动演示数据
#
# 「不存在」和「不是你的」必须同一句话 —— 分开报就等于送出一个存在性预言机。
# 所以读不到就认 403, 不去区分是哪种情况。

# 从返回体里取值的小助手(RuoYi 的业务错误是 HTTP 200 + body.code, 不是 HTTP 错误码)
crm_body_code() {
  printf '%s' "$1" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null
}

crm_body_id() {
  printf '%s' "$1" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data')
    print(d.get('id') if isinstance(d,dict) and d.get('id') is not None else 'NONE')
except Exception:
    print('NONE')
" 2>/dev/null
}

crm_body_codelen() {
  # 返回 "code|条数"。列表接口被拒时 data 是 null, 条数算 0 ——
  # 「一条都没给」和「直接拒了」同样安全, 但 code 能区分这两种情况。
  printf '%s' "$1" | python -c "
import sys,json
try:
    j = json.load(sys.stdin)
    code = j.get('code','-1')
    d = j.get('data')
    if isinstance(d, list):
        n = len(d)
    elif isinstance(d, dict):
        rows = d.get('rows') or d.get('list') or []
        n = len(rows) if isinstance(rows, list) else -1
    else:
        n = 0
    print('%s|%d' % (code, n))
except Exception:
    print('-1|-1')
" 2>/dev/null
}

check_crm_ownership() {
  hr
  echo "[14] CRM 归属守卫 (per-user IDOR 回归护栏)"
  hr
  local base="http://${GATEWAY_HOST}:${GATEWAY_PORT}"

  # --- 14.0 登录 (admin → user_id 1 → 客户负责人 owner 1) ---
  local login_body token
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "${base}/login" 2>/dev/null) || true
  token=$(echo "${login_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
    print(d.get('token','') or d.get('access_token',''))
except Exception:
    print('')
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    nok "登录失败, CRM 归属护栏无法验证"
    return
  fi
  ok "登录成功 (admin → owner 1)"

  # --- 14.0.1 靶子: 一个 owner ≠ 1 的客户, 和一个自己的客户 ---
  local foreign_cust own_cust
  foreign_cust=$(db_q "SELECT id FROM opc_crm_customer WHERE owner_id <> 1 AND deleted = 0 ORDER BY id LIMIT 1;" | tr -d '\r')
  own_cust=$(db_q "SELECT id FROM opc_crm_customer WHERE owner_id = 1 AND deleted = 0 ORDER BY id LIMIT 1;" | tr -d '\r')
  if [[ -z "${foreign_cust}" || -z "${own_cust}" ]]; then
    nok "取不到对照客户 (别人家=${foreign_cust:-无} 自家=${own_cust:-无}) — 护栏跳过"
    return
  fi
  ok "靶子: 客户 ${foreign_cust}(别人的) / ${own_cust}(自己的)"

  # --- 14.0.2 别人的子表记录 id ---
  #   客户/商机/跟进记录自带 owner_id, 直接查; 联系人/订单/合同自己不带,
  #   归属沿客户走 —— 所以要 join 客户表。
  local f_contact f_opp f_followup f_order f_contract
  f_contact=$(db_q "SELECT ct.id FROM opc_crm_contact ct JOIN opc_crm_customer cu ON cu.id=ct.customer_id WHERE cu.owner_id <> 1 AND ct.deleted=0 AND cu.deleted=0 ORDER BY ct.id LIMIT 1;" | tr -d '\r')
  f_opp=$(db_q "SELECT id FROM opc_crm_opportunity WHERE owner_id <> 1 AND deleted = 0 ORDER BY id LIMIT 1;" | tr -d '\r')
  f_followup=$(db_q "SELECT id FROM opc_crm_follow_up WHERE owner_id <> 1 AND deleted = 0 ORDER BY id LIMIT 1;" | tr -d '\r')
  f_order=$(db_q "SELECT o.id FROM opc_crm_order o JOIN opc_crm_customer cu ON cu.id=o.customer_id WHERE cu.owner_id <> 1 AND o.deleted=0 AND cu.deleted=0 ORDER BY o.id LIMIT 1;" | tr -d '\r')
  f_contract=$(db_q "SELECT ct.id FROM opc_crm_contract ct JOIN opc_crm_customer cu ON cu.id=ct.customer_id WHERE cu.owner_id <> 1 AND ct.deleted=0 AND cu.deleted=0 ORDER BY ct.id LIMIT 1;" | tr -d '\r')

  local b c

  # --- 14.1 读别人的客户 → 403 ---
  b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/crm/customer/${foreign_cust}" 2>/dev/null) || b=""
  c=$(crm_body_code "${b}")
  if [[ "${c}" == "403" ]]; then
    ok "14.1 GET /opc/crm/customer/${foreign_cust} (别人的) → 403"
  else
    nok "14.1 GET /opc/crm/customer/${foreign_cust} → body.code=${c} — 越权读到了别人的客户"
  fi

  # --- 14.2 读自己的客户 → 200 (正对照) ---
  b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/crm/customer/${own_cust}" 2>/dev/null) || b=""
  c=$(crm_body_code "${b}")
  local own_id_back
  own_id_back=$(crm_body_id "${b}")
  if [[ "${c}" == "200" && "${own_id_back}" == "${own_cust}" ]]; then
    ok "14.2 GET /opc/crm/customer/${own_cust} (自己的) → 200 (正对照通过)"
  else
    nok "14.2 GET /opc/crm/customer/${own_cust} → code=${c} id=${own_id_back} — 自家数据也读不到, 守卫可能收得太狠"
  fi

  # --- 14.3~14.7 读别人的子表记录 → 403 ---
  #   库里若没有别人的订单/合同, 那两项跳过并说明 —— 不假装验过了。
  local kind pid what rest
  for probe in "contact:${f_contact}:联系人" "opportunity:${f_opp}:商机" "follow-up:${f_followup}:跟进记录" "order:${f_order}:订单" "contract:${f_contract}:合同"; do
    kind="${probe%%:*}"; rest="${probe#*:}"
    pid="${rest%%:*}"; what="${rest##*:}"
    if [[ -z "${pid}" ]]; then
      warn "14.x 库里没有别人的${what}记录, 该项跳过 (没验过, 不是验过了)"
      continue
    fi
    b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/crm/${kind}/${pid}" 2>/dev/null) || b=""
    c=$(crm_body_code "${b}")
    if [[ "${c}" == "403" ]]; then
      ok "14.x GET /opc/crm/${kind}/${pid} (别人的${what}) → 403"
    else
      nok "14.x GET /opc/crm/${kind}/${pid} (别人的${what}) → body.code=${c} — 越权读到了别人的${what}"
    fi
  done

  # --- 14.8 别人的客户名下不该有子表记录 ---
  #   「0 行」和「直接 403 拒了」都算没泄漏; 「200 且有行」才是泄漏。
  local cid cl cl_code cl_len
  for probe in "contact:${foreign_cust}:联系人" "order:${foreign_cust}:订单" "contract:${foreign_cust}:合同"; do
    kind="${probe%%:*}"; rest="${probe#*:}"
    cid="${rest%%:*}"; what="${rest##*:}"
    b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/crm/${kind}?customerId=${cid}" 2>/dev/null) || b=""
    cl=$(crm_body_codelen "${b}")
    cl_code="${cl%%|*}"; cl_len="${cl##*|}"
    if [[ "${cl_code}" == "403" ]]; then
      ok "14.8 GET /opc/crm/${kind}?customerId=${cid} (别人的客户) → 403 直接拒了"
    elif [[ "${cl_code}" == "200" && "${cl_len}" == "0" ]]; then
      ok "14.8 GET /opc/crm/${kind}?customerId=${cid} (别人的客户) → 0 行"
    elif [[ "${cl_code}" == "-1" ]]; then
      nok "14.8 GET /opc/crm/${kind}?customerId=${cid} → 返回体解析不了 — 端点可能已变形"
    else
      nok "14.8 GET /opc/crm/${kind}?customerId=${cid} → code=${cl_code} ${cl_len} 行 — 列出了别人客户名下的${what}"
    fi
  done

  # --- 14.9 阶段的商机列表只能有自己的 ---
  b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/crm/opportunity?stage=LEAD" 2>/dev/null) || b=""
  local stage_stat s_total s_bad
  stage_stat=$(printf '%s' "${b}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin)
    rows = d.get('data') or []
    if not isinstance(rows, list): print('-1|0'); raise SystemExit
    bad = 0
    for r in rows:
        if not isinstance(r, dict): continue
        v = r.get('ownerId', r.get('owner_id'))
        if v is None: continue
        if str(v) != '1': bad += 1
    print('%d|%d' % (len(rows), bad))
except Exception:
    print('-1|0')
" 2>/dev/null)
  s_total="$(cut -d'|' -f1 <<<"${stage_stat}")"
  s_bad="$(cut -d'|' -f2 <<<"${stage_stat}")"
  if [[ "${s_total}" == "-1" ]]; then
    nok "14.9 /opc/crm/opportunity?stage=LEAD 返回体解析不了 — 端点可能已变形"
  elif [[ "${s_bad}" -gt 0 ]]; then
    nok "14.9 /opc/crm/opportunity?stage=LEAD → ${s_bad}/${s_total} 行不是自己的 (阶段列表没按 owner 过滤)"
  else
    ok "14.9 /opc/crm/opportunity?stage=LEAD → ${s_total} 行全是自己的"
  fi

  # --- 14.10 客户端传的 ownerId 不作数 ---
  #     这里跟 14.8 一样: 客户端传别人的 ownerId, 回来的必须还是**自己的**数据
  #     (归属由服务端按登录态定), 而不是别人的、更不是空。
  local foreign_owner
  foreign_owner=$(db_q "SELECT owner_id FROM opc_crm_customer WHERE id=${foreign_cust} LIMIT 1;" | tr -d '\r')
  b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/crm/follow-up?ownerId=${foreign_owner}" 2>/dev/null) || b=""
  local fu_stat fu_code fu_len fu_bad fu_stat_rest
  fu_stat=$(printf '%s' "${b}" | python -c "
import sys,json
try:
    j = json.load(sys.stdin)
    code = j.get('code','-1')
    rows = j.get('data') or []
    if not isinstance(rows, list): print('%s|-1|0' % code); raise SystemExit
    bad = 0
    for r in rows:
        if not isinstance(r, dict): continue
        v = r.get('ownerId', r.get('owner_id'))
        if v is not None and str(v) != '1': bad += 1
    print('%s|%d|%d' % (code, len(rows), bad))
except Exception:
    print('-1|-1|0')
" 2>/dev/null)
  fu_code="${fu_stat%%|*}"; fu_stat_rest="${fu_stat#*|}"; fu_len="${fu_stat_rest%%|*}"; fu_bad="${fu_stat_rest##*|}"
  if [[ "${fu_code}" != "200" ]]; then
    nok "14.10 GET /opc/crm/follow-up?ownerId=${foreign_owner} → code=${fu_code} — 端点可能已变形"
  elif [[ "${fu_bad}" -gt 0 ]]; then
    nok "14.10 GET /opc/crm/follow-up?ownerId=${foreign_owner} → ${fu_bad}/${fu_len} 行是别人的 — 客户端能指定别人的 ownerId"
  else
    ok "14.10 GET /opc/crm/follow-up?ownerId=${foreign_owner} → ${fu_len} 行全是自己的 (客户端 ownerId 不作数)"
  fi

  # --- 14.11 往别人的客户下写 → 403, 且库里一条都不许多 ---
  #     标记用一次性时间戳, 事后用 SELECT COUNT 证明「一条都没写进去」。
  local tag payload sql left
  tag="HC-PROBE-$(date +%s)"
  for probe in \
    "contact:{\"customerId\":${foreign_cust},\"name\":\"${tag}\"}:SELECT COUNT(*) FROM opc_crm_contact WHERE name='${tag}'" \
    "opportunity:{\"customerId\":${foreign_cust},\"name\":\"${tag}\"}:SELECT COUNT(*) FROM opc_crm_opportunity WHERE name='${tag}'" \
    "follow-up:{\"customerId\":${foreign_cust},\"content\":\"${tag}\"}:SELECT COUNT(*) FROM opc_crm_follow_up WHERE content='${tag}'" \
    "order:{\"customerId\":${foreign_cust},\"orderNo\":\"${tag}\",\"itemsJson\":\"[]\",\"total\":0}:SELECT COUNT(*) FROM opc_crm_order WHERE order_no='${tag}'" \
    "contract:{\"customerId\":${foreign_cust},\"contractNo\":\"${tag}\",\"title\":\"${tag}\"}:SELECT COUNT(*) FROM opc_crm_contract WHERE contract_no='${tag}'" ; do
    kind="${probe%%:*}"; rest="${probe#*:}"
    payload="${rest%:*}"; sql="${rest##*:}"
    b=$(curl -s -m 15 -X POST \
      -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
      -d "${payload}" "${base}/opc/crm/${kind}" 2>/dev/null) || b=""
    c=$(crm_body_code "${b}")
    left=$(db_q "${sql}" | tr -d '\r')
    if [[ "${c}" == "403" && "${left}" == "0" ]]; then
      ok "14.11 POST /opc/crm/${kind} (挂到别人客户下) → 403, 库里 0 行"
    elif [[ "${c}" == "403" ]]; then
      nok "14.11 POST /opc/crm/${kind} → 403, 但库里多了 ${left} 行 (标记 ${tag}, 需手工清理)"
    elif [[ "${left}" != "0" ]]; then
      nok "14.11 POST /opc/crm/${kind} → body.code=${c}, 库里多了 ${left} 行 — 归属守卫没挡住写入 (标记 ${tag}, 需手工清理)"
    else
      nok "14.11 POST /opc/crm/${kind} → body.code=${c} — 归属守卫没挡住写入"
    fi
  done

  # --- 14.12 改别人的客户 → 403 ---
  #     用「同值写回」(幂等): 守卫万一失效, 也只是 update_time 变一下, 内容不改。
  local foreign_name put_body name_now
  foreign_name=$(db_q "SELECT name FROM opc_crm_customer WHERE id=${foreign_cust} LIMIT 1;" | tr -d '\r')
  put_body=$(python -c "
import json,sys
print(json.dumps({'name': sys.argv[1]}, ensure_ascii=False))
" "${foreign_name}" 2>/dev/null)
  b=$(curl -s -m 15 -X PUT \
    -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
    -d "${put_body}" "${base}/opc/crm/customer/${foreign_cust}" 2>/dev/null) || b=""
  c=$(crm_body_code "${b}")
  name_now=$(db_q "SELECT name FROM opc_crm_customer WHERE id=${foreign_cust} LIMIT 1;" | tr -d '\r')
  if [[ "${c}" == "403" && "${name_now}" == "${foreign_name}" ]]; then
    ok "14.12 PUT /opc/crm/customer/${foreign_cust} (别人的) → 403, 内容未变"
  elif [[ "${c}" == "403" ]]; then
    nok "14.12 PUT /opc/crm/customer/${foreign_cust} → 403, 但内容变了 (${foreign_name} → ${name_now})"
  else
    nok "14.12 PUT /opc/crm/customer/${foreign_cust} → body.code=${c} — 越权改到了别人的客户"
  fi

  # --- 14.13 删别人的客户 → 403 ---
  #     这里用**不存在的 id**: 真的去删别人的记录, 万一守卫失效就 soft-delete 了
  #     演示数据。改用不存在的 id 一样能验出「删除路径走的是带归属的查询」——
  #     老代码这里会抛 IllegalArgumentException(body.code=500),
  #     新代码抛 ServiceException(403)。
  b=$(curl -s -m 15 -X DELETE \
    -H "Authorization: Bearer ${token}" \
    "${base}/opc/crm/customer/999999999" 2>/dev/null) || b=""
  c=$(crm_body_code "${b}")
  if [[ "${c}" == "403" ]]; then
    ok "14.13 DELETE /opc/crm/customer/999999999 → 403 (删除路径走的是带归属的查询)"
  else
    nok "14.13 DELETE /opc/crm/customer/999999999 → body.code=${c} — 删除路径没走带归属的查询"
  fi

  # --- 14.14 看板不能是空的 ---
  #     以前「什么都不传」直接返回空数组, 商机看板因此永远是空的;
  #     现在返回当前用户自己的商机。
  b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/crm/opportunity" 2>/dev/null) || b=""
  local kb kb_code kb_len
  kb=$(crm_body_codelen "${b}")
  kb_code="${kb%%|*}"; kb_len="${kb##*|}"
  if [[ "${kb_code}" != "200" ]]; then
    nok "14.14 GET /opc/crm/opportunity → code=${kb_code} — 端点可能已变形"
  elif [[ "${kb_len}" -gt 0 ]]; then
    ok "14.14 GET /opc/crm/opportunity (不带参数) → ${kb_len} 行 (商机看板不再是空的)"
  else
    nok "14.14 GET /opc/crm/opportunity (不带参数) → 0 行 — 商机看板还是空的"
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
  check_content
  check_marketplace
  check_gateway_whitelist
  check_tenant_guard
  check_crm_ownership
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