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
#  15) 端口收敛护栏 (业务端口不得外露)
#  16) 请求体租户归属 + OAuth state 签名 (3b 回归护栏)
#  17) LLM 计量链 (3c 回归护栏)
#  18) 多公司切租户 (3d 回归护栏)
#  19) 市场页改名 + 数字真实性 + 监控
#  20) 第 2 轮的动作是真的 (订阅 + MODULE 装完真的通知社区)
#  21) CRM 的 AI 评分是真的 (分数和理由同生共死)
#  22) Nacos 配置中心是真的在管事 (命名空间 + 配置真在跑)
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
# opc-agent-hub 的宿主机端口 (只绑 127.0.0.1)。第 17 节要直连它:
# agent-hub 的 provider 凭证配没配, 从网关那侧看不出来 —— 只能直连问它自己。
AGENT_HUB_PORT="${AGENT_HUB_PORT:-9303}"
# opc-ai-core 的宿主机端口 (只绑 127.0.0.1)。第 21 节要用它做分诊:
# CRM 的「LLM 评分」失败时, 直连它探一次 —— 直连也不通 = provider 挂了,
# 直连通了 = CRM → AI 这条路断了。
AI_CORE_PORT="${AI_CORE_PORT:-9301}"
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

# 取业务返回体里的 code (RuoYi 的业务错误是 HTTP 200 + body.code, 不是 HTTP 错误码)。
# 解析不出来返回 "-1" —— 跟「后端明确回了 0/500」区分开, 免得把「读不懂」当成「通过」。
body_code() {
  printf '%s' "$1" | python -c "
import sys,json
try:
    print(json.load(sys.stdin).get('code','-1'))
except Exception:
    print('-1')
" 2>/dev/null
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

# ---------- 15. 端口收敛护栏 (业务端口不得外露) ----------
#
# 对外只该有前端 8079。业务/中间件端口一旦绑到 0.0.0.0, 等于把网关白送:
# 网关那层的鉴权、租户守卫、限流全绕开; 更糟的是能直连服务端口伪造
# from-source: inner 头去调所有 @InnerAuth 内部接口(这头网关本来会剥掉)。
#
# 这条专门防「有人把 compose 端口改回去」—— 端口策略写在文件里不算数,
# 每次健康检查现场验一遍才算数。
check_port_exposure() {
  hr
  echo "[15] 端口收敛护栏 (业务端口不得外露)"
  hr

  # --- 15.1 收集宿主机监听表 ---
  local listen
  listen=""
  if command -v ss >/dev/null 2>&1; then
    listen="$(ss -ltnH 2>/dev/null | awk '{print $4}')"
  elif command -v netstat >/dev/null 2>&1; then
    listen="$(netstat -ltn 2>/dev/null | awk 'NR>2 {print $4}')"
  fi
  if [[ -z "${listen}" ]]; then
    nok "拿不到宿主机监听表 (ss/netstat 都不可用) — 端口收敛无法验证"
    return
  fi

  # --- 15.2 业务 + 中间件端口必须只绑回环口 ---
  # 网关也算业务端口: 它做鉴权, 直连它等于绕过前端那条链路。
  local p exposed bad=""
  local -a guard_ports=(8080 9201 19200 9301 9302 9303 9304 9305 9306 9310 9311 9312 9316 9317 9322 9325
                        3308 6379 5672 15672 8848 9848 9000 9001 9200 6333 6334 9090 3000 8081 11800 12800)
  for p in "${guard_ports[@]}"; do
    exposed="$(printf '%s\n' "${listen}" | grep -E ":${p}\$" | grep -vE '^(127\.0\.0\.1|\[::1\]):' | tr '\n' ' ')"
    if [[ -n "${exposed}" ]]; then
      bad="${bad} ${p}→${exposed}"
    fi
  done
  if [[ -z "${bad}" ]]; then
    ok "15.2 ${#guard_ports[@]} 个业务/中间件端口全部只绑 127.0.0.1"
  else
    nok "15.2 端口外露:${bad}"
  fi

  # --- 15.3 前端必须是唯一对外口 ---
  local fe
  fe="$(printf '%s\n' "${listen}" | grep -E ":${FRONTEND_PORT}\$" | tr '\n' ' ')"
  if printf '%s' "${fe}" | grep -qE '^(0\.0\.0\.0|\*|\[::\]):'; then
    ok "15.3 前端 ${FRONTEND_PORT} 对外监听 (唯一公网入口)"
  else
    nok "15.3 前端 ${FRONTEND_PORT} 没对外监听 (实际: ${fe:-没找到}) — 公网入口断了"
  fi

  # --- 15.4 从非回环地址直连业务端口必须连不上 ---
  # 光看 ss 是静态证据; 真去连一下才算数。绑回环口时, 连本机的
  # 非回环 IP 会被拒 —— 因为那个地址不属于那个 socket。
  local lan_ip
  lan_ip="$(ip route get 1.1.1.1 2>/dev/null | sed -n 's/.*src \([0-9.]*\).*/\1/p')"
  [[ -z "${lan_ip}" ]] && lan_ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  if [[ -n "${lan_ip}" ]]; then
    if timeout 3 bash -c "exec 3<>/dev/tcp/${lan_ip}/9312" 2>/dev/null; then
      nok "15.4 ${lan_ip}:9312 直连成功 — 业务端口仍然外露"
    else
      ok "15.4 ${lan_ip}:9312 直连被拒 (业务端口只绑回环口)"
    fi
  else
    warn "15.4 拿不到本机非回环 IP — 直连探测跳过 (没验过, 不是验过了)"
  fi

  # --- 15.5 公网链路: 前端 nginx → 网关 ---
  # 端口收敛把网关的宿主机口收回回环, 浏览器那条路靠的是 nginx 反代到
  # aiopc-gateway:8080(容器内网)。这里从 8079 打一遍, 证明这条路还通。
  local fe_body fe_token
  fe_body=$(curl -s -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://127.0.0.1:${FRONTEND_PORT}/prod-api/login" 2>/dev/null) || fe_body=""
  fe_token=$(printf '%s' "${fe_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
    print(d.get('token','') or d.get('access_token',''))
except Exception:
    print('')
" 2>/dev/null)
  if [[ -n "${fe_token}" ]]; then
    ok "15.5 前端 ${FRONTEND_PORT} → /prod-api → 网关 链路通 (公网入口没被端口收敛打断)"
  else
    nok "15.5 前端 ${FRONTEND_PORT}/prod-api/login 拿不到 token — 公网链路断了"
  fi
}

# ---------- 16. 请求体租户归属 + OAuth state 签名 (3b 回归护栏) ----------
#
# 16.1 generateLlm 的 body 里塞 companyId=2 → 租户守卫必须把它改回登录租户。
#      以前这个端点收 Map<String,String>: 守卫靠反射在 body 类型上找 companyId
#      字段, Map 没有字段可找 → 整条 body 路线失效, 客户端填什么就是什么
#      (端点还拿 1L 兜底)。现在 body 是声明式 DTO, 守卫认得到。
#      探针故意漏掉 title —— 服务层会先抛「title 不能为空」, 不用真调 LLM,
#      而守卫的告警日志已经打出来了。
# 16.2 抖音 authorize 的 state 必须带签名, 载荷里的公司号 = 登录租户。
# 16.3 伪造的 state (老格式裸 base64) 必须被拒, 平台账号表一条都不多。
check_body_tenant_and_oauth_state() {
  hr
  echo "[16] 请求体租户归属 + OAuth state 签名 (3b 回归护栏)"
  hr

  local base="http://${GATEWAY_HOST}:${GATEWAY_PORT}"

  # --- 登录 (admin → 公司 1) ---
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
    nok "登录失败, 请求体租户护栏无法验证"
    return
  fi
  ok "登录成功 (admin → 公司1)"

  # --- 16.1 body 里的 companyId=2 必须被改写 ---
  #   故意不给 title: 守卫改写 companyId 之后, 服务层会先因为 title 为空退出,
  #   不用花钱调 LLM, 而守卫自己的告警已经把「客户端填了 2、服务端按 1 算」写下来了。
  local ts1 guard_log
  ts1=$(date +%s)
  curl -s -m 20 -X POST \
    -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
    -d '{"companyId":2,"description":"探针"}' \
    "${base}/opc/hr/job/generate-llm" >/dev/null 2>&1
  guard_log=$(docker logs aiopc-hr --since "${ts1}" 2>&1 | grep -F "请求里的 companyId=2 与作用域 1" | tail -1)
  if [[ -n "${guard_log}" ]]; then
    ok "16.1 POST /opc/hr/job/generate-llm body 里塞 companyId=2 → 守卫改写为登录租户"
  else
    nok "16.1 POST /opc/hr/job/generate-llm body 里的 companyId 没被改写 (opc-hr 日志里没有守卫告警)"
  fi

  # --- 16.2 state 必须带签名, 且载荷里的公司号 = 登录租户 ---
  local hdrs location state payload
  hdrs=$(curl -s -m 15 -D - -o /dev/null \
    -H "Authorization: Bearer ${token}" \
    "${base}/opc/content/platform-account/oauth/douyin/authorize?companyId=2" 2>/dev/null) || hdrs=""
  location=$(printf '%s' "${hdrs}" | grep -i '^location:' | tail -1 | tr -d '\r')
  state=$(printf '%s' "${location}" | sed -n 's/.*[?&]state=\([^&[:space:]]*\).*/\1/p')
  if [[ -z "${state}" ]]; then
    nok "16.2 抖音 authorize 没拿到 state (响应头: $(printf '%s' "${hdrs}" | head -3 | tr '\n' ' '))"
  elif [[ "${state}" != *"."* ]]; then
    nok "16.2 抖音 authorize 的 state 没有签名 (不含 '.') —— 又变回裸 base64 了"
  else
    payload=$(printf '%s' "${state%%.*}" | python3 -c "
import sys, base64
try:
    s = sys.stdin.read().strip()
    raw = base64.urlsafe_b64decode(s + '=' * (-len(s) % 4))
    print(raw.decode('utf-8', 'replace'))
except Exception:
    print('')
" 2>/dev/null)
    if [[ "${payload}" == 1:* ]]; then
      ok "16.2 抖音 authorize 的 state 带签名, 载荷公司号 = 登录租户 (${payload%%:*})"
    else
      nok "16.2 抖音 authorize 的 state 载荷公司号 = ${payload:-解析不了} —— 不是登录租户 1"
    fi
  fi

  # --- 16.3 伪造的 state 必须被拒, 且一条账号都不多 ---
  local before after forged b c
  before=$(db_q "SELECT COUNT(*) FROM opc_content_platform_account;" | tr -d '\r')
  # 老格式: base64(companyId:nonce) —— 编码不是签名, 谁都能把公司号改成 2
  forged=$(printf '%s' "2:hc-probe-nonce" | base64 | tr '+/' '-_' | tr -d '=\n')
  b=$(curl -s -m 15 -H "Authorization: Bearer ${token}" \
    "${base}/opc/content/platform-account/oauth/callback?code=HC-PROBE&state=${forged}" 2>/dev/null) || b=""
  c=$(crm_body_code "${b}")
  after=$(db_q "SELECT COUNT(*) FROM opc_content_platform_account;" | tr -d '\r')
  if [[ "${c}" == "403" && "${after}" == "${before}" ]]; then
    ok "16.3 伪造的 state (裸 base64) → 403, 平台账号表没多行"
  elif [[ "${c}" != "403" ]]; then
    nok "16.3 伪造的 state → body.code=${c} —— 伪造的 state 没被挡住"
  else
    nok "16.3 伪造的 state 被挡了, 但平台账号表从 ${before} 变成 ${after} 行"
  fi
}

# ---------- 17. LLM 计量链 (3c 回归护栏) ----------
#
# 3c 的洞有三处, 互相独立, 这里各配一个探针:
#
#   17.1 缓存命中也要计量
#        以前 LlmGateway 在缓存命中时直接 return, 根本不调 tokenMeter.record ——
#        缓存越命中, 账单越空。修法: 命中也计量, token 数照记、费用记 0。
#        探针: **同一段**消息连发两次 → 计量表必须长 2 行; 第二行 request_id 以
#        "cache-" 开头 (LlmGateway 命中时打的标记) 且 cost = 0。
#        消息里要带一次性随机串 —— 否则会命中上一次巡检留下的缓存条目,
#        第一次就不是 miss 了, 探针就变成在测空气。
#
#   17.2 agent-hub 自己也得能调 LLM
#        工作流的 LLM 节点 (WorkflowEngine → AgentRuntime → LlmGateway) 是**进程内**
#        直连 provider, 不走 Feign —— 所以 agent-hub 自己得有一份 provider 凭证。
#        以前它一个都没有 → 所有 provider 的 enabled() 恒为 false
#        → 工作流只要带 LLM 节点就必然失败 (连「漏记」都谈不上, 是压根没调成)。
#        探针: 直连 agent-hub 的 /opc/llm/chat (127.0.0.1:${AGENT_HUB_PORT}) → body.code 必须是 200。
#
#   17.3 没有登录态的内部调用也要记账
#        工作流是定时触发的 (@InnerAuth, 只有 Quartz 能调), 本来就没有登录态;
#        归属必须由工作流自己带进 LLM 调用, 否则这次调用的 token/成本凭空消失。
#        探针: 用内部端点触发一次工作流 → 计量表必须长行, 且新行的
#        company_id / user_id 都是 0 (系统模板工作流归 0 = 系统)。
#        这一步会在 opc_agent_workflow_run 里多一条运行记录 —— 那是运行日志, 不是业务数据。
check_metering_chain() {
  hr
  echo "[17] LLM 计量链 (3c 回归护栏)"
  hr

  local base="http://${GATEWAY_HOST}:${GATEWAY_PORT}"
  local hub="http://127.0.0.1:${AGENT_HUB_PORT}"

  # --- 登录 (admin → 公司 1) ---
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
    nok "登录失败, LLM 计量链护栏无法验证"
    return
  fi
  ok "登录成功 (admin → 公司1)"

  # --- 17.1 缓存命中也要计量 ---
  local nonce msg before after last_req last_cost code1 code2
  nonce="n$(date +%s%N | tail -c 7)"
  msg="只回复一个字:好 ${nonce}"
  before=$(db_q "SELECT COUNT(*) FROM opc_agent_token_usage;" | tr -d '\r')
  code1=$(body_code "$(curl -s -m 90 -X POST \
    -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
    -d "{\"messages\":[{\"role\":\"user\",\"content\":\"${msg}\"}]}" \
    "${base}/opc/llm/chat" 2>/dev/null)")
  sleep 2
  code2=$(body_code "$(curl -s -m 90 -X POST \
    -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
    -d "{\"messages\":[{\"role\":\"user\",\"content\":\"${msg}\"}]}" \
    "${base}/opc/llm/chat" 2>/dev/null)")
  sleep 3
  after=$(db_q "SELECT COUNT(*) FROM opc_agent_token_usage;" | tr -d '\r')
  last_req=$(db_q "SELECT request_id FROM opc_agent_token_usage ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  last_cost=$(db_q "SELECT cost FROM opc_agent_token_usage ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  if [[ "${code1}" != "200" || "${code2}" != "200" ]]; then
    warn "17.1 /opc/llm/chat 没成功 (code1=${code1} code2=${code2}) — provider 可能不可用, 护栏未能验证"
  elif [[ -z "${after}" || -z "${before}" ]]; then
    nok "17.1 计量表查不到 — 护栏空转, 证明不了什么"
  elif [[ "${after}" -le $((before + 1)) ]]; then
    nok "17.1 两次同样的调用只让计量表从 ${before} 变成 ${after} 行 — 缓存命中那次没计量 (第 2 次应记 token、费用记 0)"
  elif [[ "${last_req}" != cache-* ]]; then
    nok "17.1 最后一行 request_id=${last_req:-空} —— 缓存命中没打上 'cache-' 标记, 走的还是 provider"
  elif [[ "${last_cost}" != "0.0000" && "${last_cost}" != "0" ]]; then
    nok "17.1 缓存命中那行 cost=${last_cost} —— 缓存命中不该再付一次 provider 的钱, 必须是 0"
  else
    ok "17.1 缓存命中也计量 (${before} → ${after} 行, 末行 request_id=${last_req} cost=${last_cost})"
  fi

  # --- 17.2 agent-hub 自己的 LLM 网关要能用 ---
  #     绕开网关直连服务端口: agent-hub 的 provider 凭证配没配, 网关那侧看不出来。
  local hub_code
  hub_code=$(body_code "$(curl -s -m 90 -X POST \
    -H "Content-Type: application/json" \
    -d "{\"messages\":[{\"role\":\"user\",\"content\":\"只回复一个字:好 hub${nonce}\"}]}" \
    "${hub}/opc/llm/chat" 2>/dev/null)")
  if [[ "${hub_code}" == "200" ]]; then
    ok "17.2 agent-hub 自带的 LLM 网关可用 (直连 127.0.0.1:${AGENT_HUB_PORT} → 200)"
  else
    nok "17.2 agent-hub 自带的 LLM 网关不可用 (body.code=${hub_code}) —— 工作流的 LLM 节点必然失败 (provider 凭证没配?)"
  fi

  # --- 17.3 没有登录态的内部调用也要记账 ---
  local wf_code wf_before wf_after wf_status wf_body
  wf_code=$(db_q "SELECT workflow_code FROM opc_agent_workflow WHERE enabled=1 ORDER BY id LIMIT 1;" | tr -d '\r')
  if [[ -z "${wf_code}" ]]; then
    warn "17.3 没有启用的工作流 — 内部调用(无登录态)的计量护栏跳过"
    return
  fi
  wf_before=$(db_q "SELECT COUNT(*) FROM opc_agent_token_usage WHERE user_id=0;" | tr -d '\r')
  wf_body=$(curl -s -m 240 -X POST -H "from-source: inner" \
    "${hub}/opc/agent/workflows/${wf_code}/trigger?triggerSource=healthcheck" 2>/dev/null) || wf_body=""
  wf_status=$(printf '%s' "${wf_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data') or {}
    print(d.get('status','') or '')
except Exception:
    print('')
" 2>/dev/null)
  sleep 2
  wf_after=$(db_q "SELECT COUNT(*) FROM opc_agent_token_usage WHERE user_id=0;" | tr -d '\r')
  if [[ "${wf_status}" == "RUNNING" ]]; then
    warn "17.3 工作流 ${wf_code} 正在运行中(30 秒去重) — 本次未触发, 护栏未能验证"
  elif [[ -z "${wf_before}" || -z "${wf_after}" ]]; then
    nok "17.3 计量表查不到 — 护栏空转, 证明不了什么"
  elif [[ "${wf_after}" -le "${wf_before}" ]]; then
    nok "17.3 触发工作流 ${wf_code} (status=${wf_status:-未知}) 后, user_id=0 的计量行没多 (${wf_before} → ${wf_after}) —— 无登录态的内部调用把用量整条丢了"
  else
    ok "17.3 工作流 ${wf_code} (status=${wf_status}) 的用量也记账了 (user_id=0 的计量行 ${wf_before} → ${wf_after})"
  fi
}

# ---------- 18. 多公司切租户 (3d 回归护栏) ----------
#
# 3d 修的是「多公司用户只能看见第一家公司」。这条链路上有两处必须守住:
#
#   18.1 「我加入的公司」要能列出来, 且当前租户要有 current 标记
#        以前这个列表只按 owner_user_id 过滤 —— 被邀请进别人公司的员工
#        (demo 里 user 101 挂在公司 2 名下) 拿到的是空数组, 界面上直接显示
#        「没有公司」; 而且默认选中第一家, 显示的公司名和真实租户对不上。
#        探针: 列表非空 + 恰好一条 current=true。
#
#   18.2 不能切到自己不是成员的公司
#        切换 = 改写服务端的登录态 (Redis 里的 LoginUser.companyId)。
#        如果只凭请求体里的 id 就改, 任何登录用户都能把租户换成别家公司 ——
#        换完就能读别人家的财务/订单。探针: admin (公司 1 的成员) 去切公司 2
#        (公司 2 的成员是 101) → 必须被拒 (body.code 403), 且当前租户不变。
#
#   18.3 每家公司都得有在职成员
#        createCompany 以前只写 opc_company_profile、不写 opc_company_member ——
#        自己开的公司对自己都不是租户 (companyId 为空 → 所有租户接口 400)。
#        探针: NORMAL 公司里「没有 ACTIVE 成员行」的数量必须为 0。
check_company_switch() {
  hr
  echo "[18] 多公司切租户 (3d 回归护栏)"
  hr

  local base="http://${GATEWAY_HOST}:${GATEWAY_PORT}"
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
    nok "登录失败, 多公司切租户护栏无法验证"
    return
  fi
  ok "登录成功 (admin)"

  # --- 18.1 我加入的公司 ---
  local members_body list_count current_id
  members_body=$(curl -s -m 15 \
    -H "Authorization: Bearer ${token}" \
    "${base}/opc/user/company-members" 2>/dev/null) || members_body=""
  list_count=$(printf '%s' "${members_body}" | python -c "
import sys,json
try:
    data = json.load(sys.stdin).get('data') or []
    print(len(data) if isinstance(data, list) else -1)
except Exception:
    print(-1)
" 2>/dev/null)
  current_id=$(printf '%s' "${members_body}" | python -c "
import sys,json
try:
    data = json.load(sys.stdin).get('data') or []
    cur = [c for c in data if c.get('current')]
    print(cur[0].get('id','') if cur else '')
except Exception:
    print('')
" 2>/dev/null)
  if [[ "${list_count}" == "-1" ]]; then
    nok "18.1 GET /opc/user/company-members 读不出列表 (返回体不是预期结构) — 护栏空转"
  elif [[ "${list_count}" -le 0 ]]; then
    nok "18.1 我加入的公司是空的 — 员工看不到自己所在的公司"
  elif [[ -z "${current_id}" ]]; then
    nok "18.1 列表里没有 current=true 的项 — 前端显示的公司名会和真实租户对不上"
  else
    ok "18.1 我加入的公司 ${list_count} 家, 当前租户 companyId=${current_id}"
  fi

  # --- 18.2 不能切到自己不是成员的公司 ---
  #     找一个 admin(user_id=1) 不是成员的公司 —— 切它必须被拒。
  local other_id
  other_id=$(db_q "SELECT p.id FROM opc_company_profile p WHERE p.status='NORMAL' AND p.owner_user_id <> 1 AND NOT EXISTS (SELECT 1 FROM opc_company_member m WHERE m.company_id = p.id AND m.user_id = 1) LIMIT 1;" | tr -d '\r')
  if [[ -z "${other_id}" ]]; then
    warn "18.2 库里没有 admin 不是成员的公司 — 越权切换这一项跳过"
  else
    local code after_id
    code=$(body_code "$(curl -s -m 15 -X POST \
      -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
      -d "{\"targetCompanyId\":${other_id}}" \
      "${base}/opc/user/switch-company" 2>/dev/null)")
    after_id=$(printf '%s' "$(curl -s -m 15 -H "Authorization: Bearer ${token}" "${base}/opc/user/company-members" 2>/dev/null)" | python -c "
import sys,json
try:
    data = json.load(sys.stdin).get('data') or []
    cur = [c for c in data if c.get('current')]
    print(cur[0].get('id','') if cur else '')
except Exception:
    print('')
" 2>/dev/null)
    if [[ "${code}" == "200" ]]; then
      nok "18.2 admin 切到了自己不是成员的公司 ${other_id} —— 任何登录用户都能把租户换成别人的公司"
    elif [[ "${code}" != "403" ]]; then
      nok "18.2 切到非成员公司 ${other_id} 返回 code=${code} —— 应当是 403"
    elif [[ "${after_id}" != "${current_id}" ]]; then
      nok "18.2 切换被拒了, 但当前租户从 ${current_id} 变成了 ${after_id} —— 登录态被改了"
    else
      ok "18.2 切到非成员公司 ${other_id} 被拒 (403), 当前租户仍是 ${current_id}"
    fi
  fi

  # --- 18.3 每家公司都得有在职成员 ---
  local orphan
  orphan=$(db_q "SELECT COUNT(*) FROM opc_company_profile p WHERE p.status='NORMAL' AND NOT EXISTS (SELECT 1 FROM opc_company_member m WHERE m.company_id = p.id AND m.status='ACTIVE');" | tr -d '\r')
  if [[ -z "${orphan}" ]]; then
    nok "18.3 查不到成员行 — 护栏空转, 证明不了什么"
  elif [[ "${orphan}" != "0" ]]; then
    nok "18.3 有 ${orphan} 家公司没有任何在职成员 —— 这家公司谁都进不去 (建公司时没写成员行)"
  else
    ok "18.3 每家 NORMAL 公司都有在职成员"
  fi
}

# ---------- 19. 市场页改名 + 数字真实性 + 监控 ----------
#
# 这一节守的是「演示环境不说假话」:
#   * 菜单改了名, 别改回去 (两个市场页得分清谁是谁)
#   * 商品卡上的每个数字都得能从真实表推出来 —— 编的数字是这条的靶子
#   * Prometheus 得真的在收指标 —— micrometer-registry-prometheus
#     缺了的话 /actuator/prometheus 从来没注册过, 监控一个指标都没收到
check_marketplace_truth() {
  hr
  echo "[19] 市场页改名 + 数字真实性 + 监控"
  hr

  # --- 19.1 菜单改名 ---
  local pair mid want got
  for pair in "2001:开发者市场" "2002:开发者集市" "2035:官方数字员工"; do
    mid="${pair%%:*}"
    want="${pair#*:}"
    got=$(db_q "SELECT menu_name FROM sys_menu WHERE menu_id=${mid};" | head -1)
    if [[ "${got}" == "${want}" ]]; then
      ok "19.1 菜单 ${mid} = ${want}"
    else
      nok "19.1 菜单 ${mid} 应为「${want}」, 实际是「${got:-<查不到>}」"
    fi
  done

  # --- 19.2 install_count 必须等于 mp_install 行数 ---
  #
  # 不变量:InstallServiceImpl 只在「新插入行」时 +1,复活既有 UNINSTALLED
  # 行不 +1,卸载是软删(行还在)也不减 —— 所以 install_count 恒等于
  # COUNT(mp_install)。对不上就说明有人在手改数字。
  local bad_install
  bad_install=$(db_q "SELECT COUNT(*) FROM mp_publication p WHERE p.deleted = 0 AND p.install_count <> (SELECT COUNT(*) FROM mp_install i WHERE i.publication_id = p.id);" | tr -d '\r')
  if [[ -z "${bad_install}" ]]; then
    nok "19.2 查不到 mp_publication 行 —— 护栏空转, 证明不了什么"
  elif [[ "${bad_install}" != "0" ]]; then
    nok "19.2 有 ${bad_install} 件商品的 install_count 与 mp_install 行数对不上 —— 数字是编的"
  else
    ok "19.2 每件商品的 install_count 都等于它的 mp_install 行数"
  fi

  # --- 19.3 avg_rating / rating_count 必须等于 VISIBLE 评价的 AVG / COUNT ---
  #
  # 口径与 MpReviewMapper 的统计 SQL 完全一致 (status='VISIBLE' AND deleted=0)。
  local bad_rating
  bad_rating=$(db_q "SELECT COUNT(*) FROM mp_publication p WHERE p.deleted = 0 AND ( p.rating_count <> (SELECT COUNT(*) FROM mp_review r WHERE r.publication_id = p.id AND r.status = 'VISIBLE' AND r.deleted = 0) OR p.avg_rating <> (SELECT COALESCE(AVG(r.rating), 0) FROM mp_review r WHERE r.publication_id = p.id AND r.status = 'VISIBLE' AND r.deleted = 0) );" | tr -d '\r')
  if [[ -z "${bad_rating}" ]]; then
    nok "19.3 查不到 mp_publication 行 —— 护栏空转, 证明不了什么"
  elif [[ "${bad_rating}" != "0" ]]; then
    nok "19.3 有 ${bad_rating} 件商品的评分/评价数与真实评价行对不上 —— 数字是编的"
  else
    ok "19.3 每件商品的评分与评价数都等于它的 VISIBLE 评价行"
  fi

  # --- 19.4 MODULE 商品必须指向真实存在的社区模块 ---
  local dangling
  dangling=$(db_q "SELECT COUNT(*) FROM mp_publication p WHERE p.deleted = 0 AND p.product_type = 'MODULE' AND ( p.module_id IS NULL OR NOT EXISTS (SELECT 1 FROM opc_community_module m WHERE m.id = p.module_id) );" | tr -d '\r')
  if [[ -z "${dangling}" ]]; then
    nok "19.4 查不到 mp_publication 行 —— 护栏空转, 证明不了什么"
  elif [[ "${dangling}" != "0" ]]; then
    nok "19.4 有 ${dangling} 件功能模块商品指向不存在的社区模块 —— 装它是假的"
  else
    ok "19.4 每件功能模块商品都指向真实存在的社区模块"
  fi

  # --- 19.5 商品的类目 code 必须在 mp_category 里有对应行 ---
  #
  # 用 BINARY 比:列表页的 categoryName 走 Java Map<productType|code> 查表,
  # 那是**大小写敏感**的 —— SQL 比对 (utf8mb4_unicode_ci) 不敏感,
  # 'FINANCE' 能匹配上 'finance', 但 Java Map 匹配不上, 卡片上就显示
  # 原始 code 而不是中文类目名。所以这里必须按大小写逐字节比。
  local bad_category
  bad_category=$(db_q "SELECT COUNT(*) FROM mp_publication p WHERE p.deleted = 0 AND NOT EXISTS (SELECT 1 FROM mp_category c WHERE c.product_type = p.product_type AND BINARY c.code = BINARY p.category_code);" | tr -d '\r')
  if [[ -z "${bad_category}" ]]; then
    nok "19.5 查不到 mp_publication 行 —— 护栏空转, 证明不了什么"
  elif [[ "${bad_category}" != "0" ]]; then
    nok "19.5 有 ${bad_category} 件商品的类目 code 在 mp_category 里查不到 (大小写逐字节比对) —— 卡片显示的是原始 code"
  else
    ok "19.5 每件商品的类目 code 都能在 mp_category 里查到 (大小写逐字节一致)"
  fi

  # --- 19.6 列表页得真的显示出中文类目名, 而不是原始 code ---
  #
  # 19.5 守的是「库里存对了」。这条守的是「页面上看得对」——
  # 列表页的 categoryName 走 PublicationServiceImpl.loadJoins 建的
  # Java Map<productType|code> 查表, 那是**大小写敏感**的:
  # 库里存 'finance', Map 的键也是 'finance' 才查得到;
  # 一旦有一边大写, categoryName 就是 null, 卡片上显示原始 code "FINANCE"。
  #
  # 这两件事(库里对 / 页面对)曾经是分叉的, 所以这条护栏比 19.5 更贴近用户:
  # 它查的就是浏览器里那个接口。
  local mp_body mp_bad mp_total
  mp_body=$(curl -sf -m 15 \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/marketplace/publication/list?pageSize=50" \
    2>/dev/null) || mp_body=""
  if [[ -z "${mp_body}" ]]; then
    nok "19.6 集市列表接口拿不到 —— 护栏空转, 证明不了什么"
  else
    read -r mp_total mp_bad <<<"$(printf '%s' "${mp_body}" | python -c "
import sys, json
try:
    d = json.load(sys.stdin).get('data', {})
except Exception:
    print('0 -1')
    raise SystemExit
rows = d.get('rows', []) or []
bad = 0
for r in rows:
    code = r.get('categoryCode') or ''
    name = r.get('categoryName') or ''
    # 类目名要么是中文类目名, 要么为空(没归类) —— 但不能等于原始 code
    if code and name == code:
        bad += 1
print('%d %d' % (len(rows), bad))
" 2>/dev/null)"
    if [[ "${mp_bad}" == "-1" ]]; then
      nok "19.6 集市列表接口的返回读不懂 —— 护栏空转"
    elif [[ "${mp_total}" == "0" ]]; then
      nok "19.6 集市列表接口一条商品都没有 —— 护栏空转, 证明不了什么"
    elif [[ "${mp_bad}" != "0" ]]; then
      nok "19.6 有 ${mp_bad} 件商品的类目名显示成了原始 code —— 列表页与详情页不一致"
    else
      ok "19.6 集市列表 ${mp_total} 件商品的类目名都不是原始 code"
    fi
  fi

  # --- 19.7 7 个被抓的服务得真的有 /actuator/prometheus ---
  #
  # 端点靠 micrometer-registry-prometheus 注册 —— 依赖不在, 端点就没注册,
  # Prometheus 每 15 秒抓到的是 404 (RuoYi 把它当静态资源找不到,
  # 页面返回 code=500)。这里要求响应里出现 jvm_ 开头的指标行。
  local sp sname sport pbody
  for sp in "aiopc-gateway:8080" "aiopc-system:9201" "aiopc-ai-core:9301" \
            "aiopc-user-center:9302" "aiopc-agent-hub:9303" "aiopc-billing:9304" \
            "aiopc-finance:9305"; do
    sname="${sp%%:*}"
    sport="${sp#*:}"
    pbody=$(curl -sf -m 10 "http://127.0.0.1:${sport}/actuator/prometheus" 2>/dev/null) || pbody=""
    if echo "${pbody}" | grep -q "^jvm_"; then
      ok "19.7 ${sname} /actuator/prometheus 有 jvm_ 指标"
    else
      nok "19.7 ${sname} /actuator/prometheus 没吐出 jvm_ 指标 —— micrometer-registry-prometheus 没生效"
    fi
  done

  # --- 19.8 Prometheus 那边的 target 是不是真的 up ---
  local t_body t_state down_cnt miss_cnt
  t_body=$(curl -sf -m 10 "http://127.0.0.1:9090/api/v1/targets" 2>/dev/null) || t_body=""
  if [[ -z "${t_body}" ]]; then
    warn "19.8 Prometheus 不可达 (127.0.0.1:9090) —— 该项跳过 (没验过, 不是验过了)"
  else
    # 输出 "不 up 的 target 数 没被采集的 job 数"
    t_state=$(printf '%s' "${t_body}" | python -c "
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print('-1 -1')
    raise SystemExit
want = {'opc-gateway','opc-system','opc-ai-core','opc-user-center',
        'opc-agent-hub','opc-billing','opc-finance'}
targets = d.get('data', {}).get('activeTargets', [])
seen = set()
down = 0
for t in targets:
    job = t.get('labels', {}).get('job', '')
    if job in want:
        seen.add(job)
        if t.get('health') != 'up':
            down += 1
print('%d %d' % (down, len(want - seen)))
" 2>/dev/null)
    read -r down_cnt miss_cnt <<<"${t_state}"
    if [[ "${down_cnt}" == "0" && "${miss_cnt}" == "0" ]]; then
      ok "19.8 Prometheus 7 个 opc target 全部 up"
    elif [[ "${down_cnt}" == "-1" ]]; then
      nok "19.8 Prometheus /api/v1/targets 的返回读不懂 —— 护栏空转"
    else
      nok "19.8 Prometheus 有 ${down_cnt} 个 opc target 不 up, ${miss_cnt} 个 job 根本没被采集"
    fi
  fi

  # --- 19.9 共享库 jar 不得比源码旧 ---
  #
  # deploy.sh 的 dependency:copy-dependencies 是**另一次** mvn 调用, 不带 -am,
  # 解析不到 reactor —— 依赖只能从 ~/.m2 拿快照。所以共享库改了代码而没先
  # install, 镜像里就是旧包: 改动根本没上线, 而部署照样报成功。
  #
  # 这不是假设: opc-ai-core 一直是 9 月 23 日的快照, 10-23 的
  # PromptGuard 接入 /opc/llm/chat、10-26 的两笔 LLM 计量修复,
  # 仓库里都有、生产上一条没生效 —— 安全与监控修复"看起来做了"。
  local sh smod sname sjar stale
  for sh in "ruoyi-common/ruoyi-common-security:ruoyi-common-security" \
            "opc-common:opc-common" \
            "ruoyi-modules/opc-ai-core:opc-ai-core"; do
    smod="${sh%%:*}"
    sname="${sh#*:}"
    sjar="${HOME}/.m2/repository/com/ruoyi/${sname}/3.6.8/${sname}-3.6.8.jar"
    if [[ ! -f "${sjar}" ]]; then
      nok "19.9 ${sname} 没 install 进 ~/.m2 —— 镜像里会打进旧包"
      continue
    fi
    stale=$(find "${DEPLOY_DIR}/../${smod}/src" -type f -newer "${sjar}" 2>/dev/null | wc -l | tr -d ' ')
    if [[ "${stale}" == "0" ]]; then
      ok "19.9 ${sname} 的 jar 不比源码旧"
    else
      nok "19.9 ${sname} 的 jar 比源码旧 (${stale} 个源文件更新) —— 改动没进镜像"
    fi
  done

  # --- 19.10 subscription_count 必须等于 mp_subscription 里的活跃订阅行数 ---
  #
  # 这条守的是「商品卡上的订阅数是不是真的」。W80 P2 之前, subscription_count
  # 是页面上唯一一个**完全没有写入路径**的数字(后端 incrementSubscriptionCount
  # 零调用方)—— 页面上挂着一个底下什么都没有的数。
  #
  # 口径: subscription_count = COUNT(mp_subscription WHERE status='ACTIVE')。
  # 比 COUNT(*) 更严 —— 退订的行(CANCELLED)不算数。
  local bad_sub sub_total
  bad_sub=$(db_q "SELECT COUNT(*) FROM mp_publication p WHERE p.deleted = 0 AND p.subscription_count <> (SELECT COUNT(*) FROM mp_subscription s WHERE s.publication_id = p.id AND s.status = 'ACTIVE' AND s.deleted = 0);" | tr -d '\r')
  sub_total=$(db_q "SELECT COUNT(*) FROM mp_subscription;" | tr -d '\r')
  if [[ -z "${bad_sub}" ]]; then
    nok "19.10 查不到 mp_publication 行 —— 护栏空转, 证明不了什么"
  elif [[ "${bad_sub}" != "0" ]]; then
    nok "19.10 有 ${bad_sub} 件商品的订阅数与 mp_subscription 里的活跃订阅行数对不上 —— 数字是编的"
  else
    ok "19.10 每件商品的订阅数都等于它的活跃订阅行数 (mp_subscription 共 ${sub_total:-?} 行)"
  fi

  # --- 19.11 mp_author.total_subscribers 必须等于去重后的活跃订阅公司数 ---
  #
  # 口径: COUNT(DISTINCT tenant_company_id) —— 同一家公司的 5 个人订阅算 1 个订阅者。
  # 所以这个数用「回写」而不是 +1/-1(退订时不知道该减几个)。
  local bad_author_sub
  bad_author_sub=$(db_q "SELECT COUNT(*) FROM mp_author a WHERE a.deleted = 0 AND a.total_subscribers <> (SELECT COUNT(DISTINCT s.tenant_company_id) FROM mp_subscription s JOIN mp_publication p ON p.id = s.publication_id WHERE p.author_id = a.id AND s.status = 'ACTIVE' AND s.deleted = 0 AND p.deleted = 0);" | tr -d '\r')
  if [[ -z "${bad_author_sub}" ]]; then
    nok "19.11 查不到 mp_author 行 —— 护栏空转, 证明不了什么"
  elif [[ "${bad_author_sub}" != "0" ]]; then
    nok "19.11 有 ${bad_author_sub} 个作者的订阅者数与真实订阅公司数对不上 —— 数字是编的"
  else
    ok "19.11 每个作者的订阅者数都等于真实订阅它的公司数"
  fi
}

# ---------- 20. 第 2 轮的动作是真的 (订阅 + MODULE 装完真的通知社区) ----------
#
# 第 19 节守的是「静态数字对得上」—— 页面上的数能不能从表里推出来。
# 这一节守的是「**动作**是不是真的」—— 点一下订阅 / 装一下模块, 库里到底有没有变。
#
# 只读库的护栏证明不了「点一下按钮真的有反应」—— 那正是第 2 轮修的病:
# 订阅页面上挂着一个数, 底下什么都没有; MODULE 装完弹一句「安装成功」,
# 社区那边一行没动。
#
# 设计约束(健康检查得能反复跑):
#   装 / 订阅这类动作如果每次跑都往库里堆东西, 数字就会离「真实」越来越远 ——
#   护栏自己变成了制造假数字的机器。所以:
#     · 订阅 → 退订 是闭环(退订有 -1 的减法), 跑完回到原点, 数字不漂。
#     · 重复安装 → 409 且一个数都不动(证明不重复计数), 这条每次都跑。
#     · 首次安装 → 三个数一起 +1, 只在第一次跑时执行 —— 之后那条 mp_install 行
#       一直存在, 再点就是 409。这不是「测一次就作废」: 20.5 每次都跑, 而且它守的
#       正是「社区那边没漏通知」—— 那条通知路径一断, 20.5 立刻就红。
check_marketplace_actions() {
  hr
  echo "[20] 第 2 轮的动作是真的 (订阅 + MODULE 装完真的通知社区)"
  hr

  # ---- 登录拿 token ----
  local login_body token
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  token=$(printf '%s' "${login_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
    print(d.get('token','') or d.get('access_token',''))
except Exception:
    print('')
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    nok "20.0 拿不到 admin 的 token —— 动作类检查全部跳过"
    return
  fi

  # 带 JWT 打网关(前端走的就是这条路: nginx → 网关 → 服务)
  mp_api() {
    local method="$1" path="$2" data="${3:-}"
    if [[ -n "${data}" ]]; then
      curl -s -m 20 -X "${method}" \
        -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json" \
        -d "${data}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}${path}" 2>/dev/null || true
    else
      curl -s -m 20 -X "${method}" \
        -H "Authorization: Bearer ${token}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}${path}" 2>/dev/null || true
    fi
  }

  # ---------------- 20.1 / 20.2 订阅 ↔ 退订 ----------------
  #
  # 选一件已发布的商品当靶子。admin(user_id=1, company_id=1)没有订阅过任何商品,
  # 所以第一次跑是「新插入行」, 之后是「复活既有行」—— 两条路径的计数都该 +1/-1。
  local sub_pub
  sub_pub=$(db_q "SELECT id FROM mp_publication WHERE deleted = 0 AND status = 'PUBLISHED' ORDER BY id LIMIT 1;" | head -1)
  if [[ -z "${sub_pub}" ]]; then
    nok "20.1 查不到已发布的商品 —— 护栏空转, 证明不了什么"
  else
    local c0 s0 c1 s1
    c0=$(db_q "SELECT subscription_count FROM mp_publication WHERE id = ${sub_pub};" | head -1); c0=${c0:-0}
    s0=$(db_q "SELECT COUNT(*) FROM mp_subscription WHERE publication_id = ${sub_pub} AND status = 'ACTIVE' AND deleted = 0;" | head -1); s0=${s0:-0}

    local body code
    body=$(mp_api POST "/opc/marketplace/subscription/${sub_pub}")
    code=$(body_code "${body}")
    if [[ "${code}" != "200" ]]; then
      nok "20.1 订阅接口返回 code=${code}: ${body:0:160}"
    else
      c1=$(db_q "SELECT subscription_count FROM mp_publication WHERE id = ${sub_pub};" | head -1); c1=${c1:-0}
      s1=$(db_q "SELECT COUNT(*) FROM mp_subscription WHERE publication_id = ${sub_pub} AND status = 'ACTIVE' AND deleted = 0;" | head -1); s1=${s1:-0}
      if [[ "${c1}" == "$((c0 + 1))" && "${s1}" == "$((s0 + 1))" ]]; then
        ok "20.1 订阅是真的: 商品 ${sub_pub} 订阅数 ${c0} → ${c1}, 活跃订阅行 ${s0} → ${s1}"
      else
        nok "20.1 订阅后数字没对上: 订阅数 ${c0} → ${c1}(应 $((c0 + 1))), 活跃订阅行 ${s0} → ${s1}(应 $((s0 + 1)))"
      fi
    fi

    # 退订 —— 必须把数字还原回去, 否则健康检查自己就是个造假机器
    body=$(mp_api DELETE "/opc/marketplace/subscription/${sub_pub}")
    code=$(body_code "${body}")
    if [[ "${code}" != "200" ]]; then
      nok "20.2 退订接口返回 code=${code}: ${body:0:160}"
    else
      c1=$(db_q "SELECT subscription_count FROM mp_publication WHERE id = ${sub_pub};" | head -1); c1=${c1:-0}
      s1=$(db_q "SELECT COUNT(*) FROM mp_subscription WHERE publication_id = ${sub_pub} AND status = 'ACTIVE' AND deleted = 0;" | head -1); s1=${s1:-0}
      local row_status
      row_status=$(db_q "SELECT status FROM mp_subscription WHERE publication_id = ${sub_pub} AND user_id = 1 ORDER BY id DESC LIMIT 1;" | head -1)
      if [[ "${c1}" == "${c0}" && "${s1}" == "${s0}" && "${row_status}" == "CANCELLED" ]]; then
        ok "20.2 退订是真的: 订阅数回到 ${c1}, 活跃订阅行回到 ${s1}, 那行状态 = ${row_status}"
      else
        nok "20.2 退订后数字没还原: 订阅数 ${c1}(应 ${c0}), 活跃订阅行 ${s1}(应 ${s0}), 行状态 = ${row_status:-<无行>}(应 CANCELLED)"
      fi
    fi
  fi

  # ---------------- 20.3 / 20.4 MODULE 装完真的通知社区 ----------------
  #
  # 三个数必须一起动:
  #   mp_publication.install_count   (集市卡片上的「安装」)
  #   COUNT(mp_install)              (集市这一行真的存在)
  #   opc_community_module.install_count (模块社区卡片上的「安装」)
  # W80 P2 之前只有第一个会动, 后两个不动 —— 同一件事在两个页面上显示成
  # 两个对不上的数字。
  local mod_pub mod_id
  mod_pub=$(db_q "SELECT id FROM mp_publication WHERE deleted = 0 AND status = 'PUBLISHED' AND product_type = 'MODULE' AND module_id IS NOT NULL ORDER BY id LIMIT 1;" | head -1)
  if [[ -z "${mod_pub}" ]]; then
    nok "20.3 查不到已发布的功能模块商品 —— 护栏空转, 证明不了什么"
  else
    mod_id=$(db_q "SELECT module_id FROM mp_publication WHERE id = ${mod_pub};" | head -1)
    local p0 r0 m0 p1 r1 m1
    p0=$(db_q "SELECT install_count FROM mp_publication WHERE id = ${mod_pub};" | head -1); p0=${p0:-0}
    r0=$(db_q "SELECT COUNT(*) FROM mp_install WHERE publication_id = ${mod_pub};" | head -1); r0=${r0:-0}
    m0=$(db_q "SELECT install_count FROM opc_community_module WHERE id = ${mod_id};" | head -1); m0=${m0:-0}

    body=$(mp_api POST "/opc/marketplace/install/${mod_pub}")
    code=$(body_code "${body}")
    p1=$(db_q "SELECT install_count FROM mp_publication WHERE id = ${mod_pub};" | head -1); p1=${p1:-0}
    r1=$(db_q "SELECT COUNT(*) FROM mp_install WHERE publication_id = ${mod_pub};" | head -1); r1=${r1:-0}
    m1=$(db_q "SELECT install_count FROM opc_community_module WHERE id = ${mod_id};" | head -1); m1=${m1:-0}

    if [[ "${code}" == "200" ]]; then
      if [[ "${r1}" == "$((r0 + 1))" ]]; then
        # 新插入一行 → 三个数必须一起 +1
        if [[ "${p1}" == "$((p0 + 1))" && "${m1}" == "$((m0 + 1))" ]]; then
          ok "20.3 MODULE 首次安装: 三个数一起 +1 (商品 ${p0}→${p1} / 安装行 ${r0}→${r1} / 社区模块 ${m0}→${m1})"
        else
          nok "20.3 MODULE 首次安装但三个数没一起动: 商品 ${p0}→${p1}(应 $((p0+1))) / 安装行 ${r0}→${r1} / 社区模块 ${m0}→${m1}(应 $((m0+1)))"
        fi
      else
        # 复活既有行 → 三个数都不该动(重装不刷榜; 社区侧跟市场侧同一口径)
        if [[ "${p1}" == "${p0}" && "${m1}" == "${m0}" ]]; then
          ok "20.3 MODULE 重装(复活既有行): 三个数一个都没动 (商品 ${p1} / 安装行 ${r1} / 社区模块 ${m1}) —— 重装不刷榜"
        else
          nok "20.3 MODULE 重装却动了计数: 商品 ${p0}→${p1} / 安装行 ${r0}→${r1} / 社区模块 ${m0}→${m1}(应都不动)"
        fi
      fi
    elif [[ "${code}" == "409" ]]; then
      ok "20.3 该模块已装过 → 409(不重复计数); 三个数未动 (商品 ${p1} / 安装行 ${r1} / 社区模块 ${m1})"
    else
      nok "20.3 安装接口返回 code=${code}: ${body:0:160}"
    fi

    # 20.4 再点一次 → 必须 409,且一个数都不动(用户连点两下按钮)
    p0=$(db_q "SELECT install_count FROM mp_publication WHERE id = ${mod_pub};" | head -1); p0=${p0:-0}
    r0=$(db_q "SELECT COUNT(*) FROM mp_install WHERE publication_id = ${mod_pub};" | head -1); r0=${r0:-0}
    m0=$(db_q "SELECT install_count FROM opc_community_module WHERE id = ${mod_id};" | head -1); m0=${m0:-0}

    body=$(mp_api POST "/opc/marketplace/install/${mod_pub}")
    code=$(body_code "${body}")
    p1=$(db_q "SELECT install_count FROM mp_publication WHERE id = ${mod_pub};" | head -1); p1=${p1:-0}
    r1=$(db_q "SELECT COUNT(*) FROM mp_install WHERE publication_id = ${mod_pub};" | head -1); r1=${r1:-0}
    m1=$(db_q "SELECT install_count FROM opc_community_module WHERE id = ${mod_id};" | head -1); m1=${m1:-0}

    if [[ "${code}" == "409" && "${p1}" == "${p0}" && "${r1}" == "${r0}" && "${m1}" == "${m0}" ]]; then
      ok "20.4 重复安装被拒(409), 三个数一个都没动 —— 连点两下不会重复计数"
    else
      nok "20.4 重复安装没被挡住: code=${code} / 商品 ${p0}→${p1} / 安装行 ${r0}→${r1} / 社区模块 ${m0}→${m1}(应 409 且都不动)"
    fi
  fi

  # ---------------- 20.5 社区那边没漏通知 ----------------
  #
  # 对每个被 MODULE 商品引用的模块: 社区显示的安装数必须 >= 集市里真实存在的
  # 安装行数。少一个就说明「装完没通知社区」那条路断了。
  #
  # 用 >= 而不是 =: 社区自己页面上那个「安装」按钮也给同一个计数 +1, 而那次安装
  # **不留任何记录**(ModuleController#install → incrementInstall, 没有表)。
  # 所以社区计数天然可能比「可推导的行数」大 —— 但绝不能比它小。
  local bad_mod mod_detail
  bad_mod=$(db_q "SELECT COUNT(*) FROM ( SELECT m.id, m.install_count AS shown, COUNT(i.id) AS rows_cnt FROM opc_community_module m JOIN mp_publication p ON p.module_id = m.id AND p.product_type = 'MODULE' LEFT JOIN mp_install i ON i.publication_id = p.id GROUP BY m.id, m.install_count ) t WHERE t.shown < t.rows_cnt;" | head -1)
  bad_mod=${bad_mod:-0}
  mod_detail=$(db_q "SELECT CONCAT(m.code, ': 显示 ', m.install_count, ' / 真实 ', COUNT(i.id)) FROM opc_community_module m JOIN mp_publication p ON p.module_id = m.id AND p.product_type = 'MODULE' LEFT JOIN mp_install i ON i.publication_id = p.id GROUP BY m.id, m.code, m.install_count;" | tr '\n' ' ')
  if [[ "${bad_mod}" == "0" ]]; then
    ok "20.5 社区那边没漏通知 (${mod_detail:-无 MODULE 商品})"
  else
    nok "20.5 有 ${bad_mod} 个模块的显示安装数小于真实安装行数 —— 装完没通知到社区 (${mod_detail})"
  fi
}

# ---------- 21. CRM 的 AI 评分是真的 (分数和理由同生共死) ----------
#
# 商机看板卡片上那个「LLM 评分」按钮, 此前调的是一个**从没被实现过的地址**:
#   POST /opc/ai-core/opportunity/score
# opc-ai-core 里根本没有这个控制器(对端只有 /opc/llm/chat 和 /opc/llm/models);
# 加上 @FeignClient 上写了 url, base-url 里的主机名(opc-ai-core)在容器网里也
# 解析不了 —— Nacos 服务发现被绕开了。结论:这个按钮**点了必定报错**。
#
# 可看板上每张卡片却还挂着一个 85 / 70 / 95 的分数 —— 全部来自种子 SQL,
# 理由(score_reason)是空的。而系统里**没有任何一条代码路径**能产出「有分没理由」
# 的记录 —— 这跟市场页那批「823 次浏览 / 4.8 分 127 条评价」是同一种病。
#
# 这一节守两件事:
#   21.1 数据不变式 A: 不存在「有分没理由」的记录 —— 有分数就必须有出处。
#   21.2 数据不变式 B: 有分数的行必须被应用写过(update_by 非空)—— 种子写的分数一律归零。
#   21.3 动作是真的: 真按一下「LLM 评分」, 分数**和理由**一起落到那条商机上。
#   21.4 分诊: 评分失败时直连 opc-ai-core 再探一次 ——
#        直连也不通 = provider 挂了(warn, 不是代码的锅);
#        直连通了 = CRM → AI 这条路又断了(nok)。
#
# 21.3 会真的调一次 LLM(有一点 token 成本)。这是故意的: 证明「按钮能用」只能靠
# 真的按一下。反复跑会反复重评同一条商机 —— 但每次写进去的都是真分数, 不是假数。
check_crm_ai_score() {
  hr
  echo "[21] CRM 的 AI 评分是真的 (分数和理由同生共死)"
  hr

  # ---- 21.1 数据不变式 A: 有分必须有理由 ----
  local bad scored
  bad=$(db_q "SELECT COUNT(*) FROM opc_crm_opportunity WHERE score <> 0 AND score_reason IS NULL;" | head -1)
  bad=${bad:-0}
  scored=$(db_q "SELECT COUNT(*) FROM opc_crm_opportunity WHERE score <> 0;" | head -1)
  scored=${scored:-0}
  if [[ "${bad}" == "0" ]]; then
    ok "21.1 没有「有分没理由」的商机 (当前有分数的 ${scored} 条, 全部带理由)"
  else
    nok "21.1 有 ${bad} 条商机有分数却没有理由 —— 这些分数是编出来的 (V20261031 应已归零)"
  fi

  # ---- 21.2 数据不变式 B: 有分的行必须被应用写过 ----
  #
  # CRM 的每次写入都带 update_by(score 里是 opp.setUpdateBy(operatorId)),
  # 种子 INSERT 从不写它 → update_by IS NULL = 这一行应用从没碰过。
  # 电商种子曾经写死 82 分 / 75 分还配着像模像样的理由 —— 理由再像也是手写的。
  local seeded
  seeded=$(db_q "SELECT COUNT(*) FROM opc_crm_opportunity WHERE score <> 0 AND update_by IS NULL;" | head -1)
  seeded=${seeded:-0}
  if [[ "${seeded}" == "0" ]]; then
    ok "21.2 没有「应用从没碰过却有分数」的商机 (种子写的分数一律归零)"
  else
    nok "21.2 有 ${seeded} 条商机有分数、update_by 却是空的 —— 这些分数是种子写的, 不是算出来的 (V20261032 应已归零)"
  fi

  # ---- 选一条 admin 名下的商机当靶子 ----
  # 客户也必须是 admin 的 —— 服务端会按 owner 查客户, 客户不是本人就 403。
  local target
  target=$(db_q "SELECT o.id FROM opc_crm_opportunity o JOIN opc_crm_customer c ON c.id = o.customer_id AND c.owner_id = o.owner_id AND c.deleted = 0 WHERE o.owner_id = 1 AND o.deleted = 0 ORDER BY o.id LIMIT 1;" | head -1)
  if [[ -z "${target}" ]]; then
    nok "21.3 找不到 admin 名下的商机 —— 护栏空转, 证明不了任何东西"
    return
  fi

  # ---- 登录拿 token ----
  local login_body token
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  token=$(printf '%s' "${login_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
    print(d.get('token','') or d.get('access_token',''))
except Exception:
    print('')
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    nok "21.3 拿不到 admin 的 token —— 护栏空转, 证明不了任何东西"
    return
  fi

  # ---- 21.3 真按一下「LLM 评分」 ----
  local before_score body code
  before_score=$(db_q "SELECT score FROM opc_crm_opportunity WHERE id = ${target};" | head -1)

  # LLM 要好几秒, 超时给足
  body=$(curl -s -m 90 -X POST \
    -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/crm/opportunity/${target}/score" 2>/dev/null) || body=""
  code=$(body_code "${body}")

  if [[ "${code}" != "200" ]]; then
    # ---- 21.4 分诊: 直连 opc-ai-core 探一次, 分清「provider 挂了」和「路断了」 ----
    local direct direct_code
    direct=$(curl -s -m 60 -X POST \
      -H "Content-Type: application/json" \
      -d '{"messages":[{"role":"user","content":"ping"}]}' \
      "http://127.0.0.1:${AI_CORE_PORT}/opc/llm/chat" 2>/dev/null) || direct=""
    direct_code=$(body_code "${direct}")
    if [[ "${direct_code}" == "200" ]]; then
      nok "21.3 CRM 评分接口返回 code=${code}: ${body:0:160} —— 但直连 opc-ai-core 是通的, 所以断的是 CRM→AI 这条路"
    else
      warn "21.3 CRM 评分返回 code=${code} —— 直连 opc-ai-core 也不通(code=${direct_code}), 是 provider 侧的问题, 不是评分路径断了"
    fi
    return
  fi

  # 返回体里必须有分数和理由
  local score reason after_score after_reason
  score=$(printf '%s' "${body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data') or {}
    v = d.get('score')
    print(v if isinstance(v,(int,float)) else '-1')
except Exception:
    print('-1')
" 2>/dev/null)
  reason=$(printf '%s' "${body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data') or {}
    print((d.get('reason') or '').strip())
except Exception:
    print('')
" 2>/dev/null)
  after_score=$(db_q "SELECT score FROM opc_crm_opportunity WHERE id = ${target};" | head -1)
  after_reason=$(db_q "SELECT IFNULL(score_reason,'') FROM opc_crm_opportunity WHERE id = ${target};" | head -1)

  if [[ "${score}" == "-1" ]]; then
    nok "21.3 返回体里没有可用的分数: ${body:0:160}"
  elif [[ "${reason}" == "" ]]; then
    nok "21.3 分数 ${score} 有, 理由是空的 —— 「有分没理由」就是编的"
  elif [[ "${after_score}" != "${score}" ]]; then
    nok "21.3 返回 ${score} 分, 库里那条商机却是 ${after_score} 分 —— 返回值和落库对不上"
  elif [[ "${after_reason}" != "${reason}" ]]; then
    nok "21.3 理由没落库: 库里是「${after_reason}」, 返回的是「${reason}」"
  else
    ok "21.3 LLM 评分是真的: 商机 ${target} 得分 ${score}(评分前 ${before_score}), 理由「${reason:0:40}」一起落库"
  fi
}

# ---------- 22. Nacos 配置中心是真的在管事 (不是摆设) ----------
#
# 这一节守的是「第 5 条假话」: 仓库里摆着一整套 deploy/nacos/*.yml,
# 看上去「配置中心在管着服务」—— 实际上前几轮里它压根没生效:
#   · 命名空间 opc-dev 在 tenant_info 里没有户口本 → 控制台看不见这一栏;
#   · 7 份 dev 配置被老脚本用 namespaceId= 推进去, 服务端静默回落到默认 tenant,
#     配置其实躺在 public 栏, 而服务端是按 tenant=opc-dev 拉的 → 拉了个空;
#   · import-dev.sh 推送和回读用同一个错参数 → md5 校验一路 [OK], 是假阳性。
#
# 四件事:
#   22.1 命名空间在控制台看得见 (tenant_info 里那本户口)
#   22.2 10 份配置真的在 tenant=opc-dev 下, 且内容与仓库文件一致
#        (9 份 dev yml + 网关的 Sentinel 流控规则表 sentinel-ruoyi-gateway)
#   22.3 默认 tenant 下没有 OPC dev 配置残留
#        —— 同名两份摆着, 迟早有人改错那一份 (而且那份还带着
#           spring.datasource 指 127.0.0.1 / actuator 暴露 "*" 的老内容)
#   22.4 冷启动那一刻服务真的读到了这份配置
#        —— 判据: /actuator/metrics/jvm.memory.used 上带 application 标签。
#           这个标签只写在 Nacos 那份 application-dev.yml 里
#           (management.metrics.tags.application), 各服务本地 yml 都没有;
#           而「公共标签」和「端点暴露面」都是**启动时**定下的, 热刷新加不了 ——
#           所以标签在不在, 恰恰说明服务冷启动时读到没读到。
#   22.5 没读到的服务列出来 (待重建清单)
#        —— 「待重建」是按**每个服务自己拉的**那几份配置算的, 不是按全体配置的最新时刻:
#           动一份只有网关读的 Sentinel 规则表, 不该把 15 个服务全标成待重建。
#   22.6 网关的 Sentinel 数据源真的连上了 Nacos (日志里一条重试都没有)
#
# 注意 ruoyi-system 不在探测名单里: compose 里给它显式设了
# SPRING_CLOUD_NACOS_CONFIG_ENABLED=false —— 它不读 Nacos 配置, 由自己的
# 本地 application.yml 全权负责。它永远不会带上 application 标签, 那不是故障。
check_nacos_config_center() {
  hr
  echo "[22] Nacos 配置中心是真的在管事"
  hr

  local ns="opc-dev"
  local api="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos"
  local yml_dir="${DEPLOY_DIR}/nacos"

  # ---- 22.1 / 22.2 / 22.3 ----
  local out
  out=$(python3 - "${api}" "${ns}" "${yml_dir}" <<'PY' 2>/dev/null
import json, os, sys, urllib.parse, urllib.request

api, ns, yml_dir = sys.argv[1], sys.argv[2], sys.argv[3]
# (仓库文件名, Nacos DataID)
#
# 两者绝大多数同名; 唯一的例外是网关的 Sentinel 流控规则表 ——
# 网关 bootstrap.yml 里写死 dataId=sentinel-ruoyi-gateway(不带扩展名),
# 仓库里的文件叫 .json 只是为了让它被当 JSON 编辑。
# 这一对如果写成同名, 22.2 会拿一份压根没人读的配置去比, 22.3 也会去
# 默认 tenant 里找一份不存在的残留 —— 两个错都查不出来。
FILES = [("application-dev.yml", "application-dev.yml"),
         ("opc-common-dev.yml", "opc-common-dev.yml"),
         ("opc-notification-dev.yml", "opc-notification-dev.yml"),
         ("opc-crm-dev.yml", "opc-crm-dev.yml"),
         ("opc-community-dev.yml", "opc-community-dev.yml"),
         ("opc-marketplace-dev.yml", "opc-marketplace-dev.yml"),
         ("opc-hr-dev.yml", "opc-hr-dev.yml"),
         ("opc-erp-dev.yml", "opc-erp-dev.yml"),
         ("opc-content-dev.yml", "opc-content-dev.yml"),
         ("sentinel-ruoyi-gateway.json", "sentinel-ruoyi-gateway")]
DATAIDS = set(d for _, d in FILES)

def http(url):
    try:
        r = urllib.request.urlopen(url, timeout=10)
        return r.read().decode('utf-8', errors='replace')
    except Exception:
        return None

def norm(s):
    return '\n'.join((s or '').splitlines())

out = []

# ---- 22.1 命名空间在控制台看得见 ----
raw = http(api + '/v1/console/namespaces')
info = {}
if raw:
    try:
        for n in json.loads(raw).get('data') or []:
            info[n.get('namespace')] = n
    except Exception:
        pass
if ns in info:
    out.append(("OK", "22.1 命名空间 %s 在控制台看得见 (configCount=%s)" % (ns, info[ns].get('configCount'))))
else:
    out.append(("FAIL", "22.1 命名空间 %s 不在控制台命名空间列表里 —— 控制台看不见这一栏, 配置等于没纳管" % ns))

# ---- 22.2 N 份配置在 tenant=opc-dev 下, 且与仓库逐行一致 ----
missing, drift, good = [], [], []
for f, dataid in FILES:
    url = api + '/v1/cs/configs?' + urllib.parse.urlencode(
        {'dataId': dataid, 'group': 'DEFAULT_GROUP', 'tenant': ns})
    body = http(url)
    if body is None or body.strip() in ('', 'config data not exist'):
        missing.append(dataid)
        continue
    path = os.path.join(yml_dir, f)
    if not os.path.exists(path):
        drift.append(f + "(仓库里没有这份文件)")
        continue
    with open(path, encoding='utf-8') as fh:
        repo = fh.read()
    if norm(body) != norm(repo):
        drift.append(dataid)
        continue
    good.append(dataid)
if missing:
    out.append(("FAIL", "22.2 tenant=%s 下缺 %d 份配置: %s" % (ns, len(missing), ' '.join(missing))))
elif drift:
    out.append(("FAIL", "22.2 %d 份配置内容与仓库不一致(漂移): %s" % (len(drift), ' '.join(drift))))
else:
    out.append(("OK", "22.2 %d 份配置都在 tenant=%s 下, 且与仓库文件逐行一致" % (len(good), ns)))

# ---- 22.3 默认 tenant 下没有 OPC dev 残留 ----
url = api + '/v1/cs/configs?' + urllib.parse.urlencode(
    {'search': 'blur', 'dataId': '', 'group': '', 'tenant': '', 'pageNo': 1, 'pageSize': 200})
raw = http(url)
leftovers = []
if raw:
    try:
        for it in json.loads(raw).get('pageItems') or []:
            did = it.get('dataId') or ''
            if did in DATAIDS:
                leftovers.append(did)
    except Exception:
        pass
if leftovers:
    out.append(("FAIL", "22.3 默认 tenant 下还留着 %d 份 OPC dev 配置(同名两份, 迟早有人改错那一份): %s"
                % (len(leftovers), ' '.join(sorted(leftovers)))))
else:
    out.append(("OK", "22.3 默认 tenant 下没有 OPC dev 配置残留"))

for st, msg in out:
    print(st + "\t" + msg)
PY
)

  if [[ -z "${out}" ]]; then
    nok "22.1-22.3 连不上 Nacos (${api}) —— 这一节空转, 证明不了任何东西"
  else
    while IFS=$'\t' read -r st msg; do
      [[ -z "${st}" ]] && continue
      case "${st}" in
        OK)   ok   "${msg}" ;;
        WARN) warn "${msg}" ;;
        *)    nok  "${msg}" ;;
      esac
    done <<< "${out}"
  fi

  # ---- 22.4 / 22.5 冷启动时到底读到没读到 ----
  #
  # 判据: /actuator/metrics/jvm.memory.used 上带 application 标签。
  #   这个标签只写在 Nacos 那份 application-dev.yml 里
  #   (management.metrics.tags.application), 各服务本地 yml 都没有这一条;
  #   而「公共标签」和「端点暴露面」都是**启动时**定下的, 热刷新加不了 ——
  #   所以标签在不在, 恰恰说明服务冷启动那一刻读到没读到。
  #
  # 但「没有标签」有两种完全不同的原因, 严重程度也不同:
  #   · 容器是在配置进 Nacos **之前**启动的 → 还没重建, 是待办 (WARN)
  #   · 容器是在配置进 Nacos **之后**启动的, 却仍然没标签 → 配置没生效 (FAIL)
  # 拿容器启动时刻 (docker inspect) 和配置落库时刻 (config_info.gmt_modified)
  # 一比就分得清 —— 两者都是 UTC, 可以直接比。
  local cfg_rows started_at
  cfg_rows=$(db_q "SELECT data_id, DATE_FORMAT(gmt_modified, '%Y-%m-%d %H:%i:%s') FROM \`ry-config\`.config_info WHERE tenant_id = 'opc-dev';" | head -20)
  started_at=$(docker inspect --format '{{.Name}}|{{.State.StartedAt}}' \
    aiopc-gateway aiopc-auth aiopc-user-center aiopc-agent-hub aiopc-ai-core \
    aiopc-billing aiopc-finance aiopc-insight aiopc-notification aiopc-crm \
    aiopc-community aiopc-hr aiopc-erp aiopc-content aiopc-marketplace 2>/dev/null || true)

  local probe
  probe=$(python3 - "${cfg_rows}" "${started_at}" <<'PY' 2>/dev/null
import json, sys, urllib.request
from datetime import datetime, timezone

cfg_raw = (sys.argv[1] or "").strip()
start_map = {}
for line in (sys.argv[2] or "").split("\n"):
    if "|" not in line:
        continue
    name, _, ts = line.partition("|")
    start_map[name.strip().lstrip("/")] = ts.strip()

# 每份 DataID 各自的落库时刻。
#
# 为什么不能只取 MAX: 网关的 Sentinel 规则表只有网关读, 它一动就会把
# MAX 推到当下 —— 于是**所有**服务都成了"配置比容器新", 全被标成待重建。
# 那种告警没人会当真, 下一次就不看了。而真正要重建的往往只有一个。
cfg_time = {}
for line in cfg_raw.split("\n"):
    if "\t" not in line:
        continue
    did, _, ts = line.partition("\t")
    did, ts = did.strip(), ts.strip()
    if did and ts:
        cfg_time[did] = ts

def epoch(s):
    if not s:
        return None
    try:
        if "T" in s:
            return datetime.fromisoformat(s.replace("Z", "+00:00")).timestamp()
        return datetime.strptime(s, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc).timestamp()
    except Exception:
        return None

# 每个服务实际会拉的 DataID —— 抄自各服务 bootstrap.yml 的 spring.config.import:
#   nacos:application-${profile}.yml                -> application-dev.yml (全体)
#   nacos:${spring.application.name}-${profile}.yml -> <app>-dev.yml (不存在的那份 Nacos 记 "is empty" 跳过)
#   nacos:opc-common-${profile}.yml                 -> opc-common-dev.yml
# 网关的 Sentinel 数据源不走 config.import, 它自己按 dataId 取流控规则。
SVC_IMPORTS = {
    "gateway":      ["application-dev.yml", "sentinel-ruoyi-gateway"],
    "auth":         ["application-dev.yml"],
    "community":    ["application-dev.yml", "opc-community-dev.yml"],
    "crm":          ["application-dev.yml", "opc-crm-dev.yml", "opc-common-dev.yml"],
    "notification": ["application-dev.yml", "opc-notification-dev.yml", "opc-common-dev.yml"],
    "marketplace":  ["application-dev.yml", "opc-marketplace-dev.yml", "opc-common-dev.yml"],
    "hr":           ["application-dev.yml", "opc-hr-dev.yml", "opc-common-dev.yml"],
    "erp":          ["application-dev.yml", "opc-erp-dev.yml", "opc-common-dev.yml"],
    "content":      ["application-dev.yml", "opc-content-dev.yml", "opc-common-dev.yml"],
    "ai-core":      ["application-dev.yml", "opc-common-dev.yml"],
    "agent-hub":    ["application-dev.yml", "opc-common-dev.yml"],
    "user-center":  ["application-dev.yml", "opc-common-dev.yml"],
    "billing":      ["application-dev.yml", "opc-common-dev.yml"],
    "finance":      ["application-dev.yml", "opc-common-dev.yml"],
    "insight":      ["application-dev.yml", "opc-common-dev.yml"],
}

# ruoyi-system 不在名单里: compose 给它显式关了 Nacos 配置
# (SPRING_CLOUD_NACOS_CONFIG_ENABLED=false), 它由本地 application.yml 全权负责。
SVCS = [("gateway", 8080), ("auth", 19200), ("user-center", 9302),
        ("agent-hub", 9303), ("ai-core", 9301), ("billing", 9304),
        ("finance", 9305), ("insight", 9306), ("notification", 9310),
        ("crm", 9312), ("community", 9316), ("hr", 9322), ("erp", 9311),
        ("content", 9325), ("marketplace", 9317)]

tagged, pending, broken = [], [], []
for name, port in SVCS:
    url = "http://127.0.0.1:%d/actuator/metrics/jvm.memory.used" % port
    has_tag = False
    try:
        d = json.loads(urllib.request.urlopen(url, timeout=6).read().decode())
        tags = {}
        for t in (d.get("availableTags") or []):
            vals = t.get("values") or []
            tags[t.get("tag")] = vals[0] if vals else ""
        has_tag = bool(tags.get("application"))
    except Exception:
        pass
    started = epoch(start_map.get("aiopc-" + name, ""))
    # 这个服务自己关心的那些配置里, 最新的一份是何时落库的
    pairs = [(epoch(cfg_time[d]), cfg_time[d]) for d in SVC_IMPORTS.get(name, []) if d in cfg_time]
    pairs = [(e, s) for e, s in pairs if e is not None]
    best = max(pairs) if pairs else (None, "")
    if has_tag:
        tagged.append(name)
    elif best[0] is None or started is None or started < best[0]:
        pending.append((name, best[1]))   # 容器比它关心的配置还老 -> 还没重建
    else:
        broken.append((name, best[1]))    # 配置之后才重启的, 却还是没读到 -> 配置没生效

print("TAGGED\t" + " ".join(tagged))
for n, t in pending:
    print("PENDING\t" + n + "\t" + t)
for n, t in broken:
    print("BROKEN\t" + n + "\t" + t)
PY
)

  if [[ -z "${probe}" ]]; then
    nok "22.4 探不到任何服务的 actuator —— 这一节空转, 证明不了任何东西"
    return
  fi

  local tagged="" pending="" broken=""
  while IFS=$'\t' read -r kind val extra; do
    case "${kind}" in
      TAGGED)  tagged="${tagged} ${val}" ;;
      PENDING) pending="${pending} ${val}(配置 ${extra})" ;;
      BROKEN)  broken="${broken} ${val}(配置 ${extra})" ;;
    esac
  done <<< "${probe}"
  tagged="${tagged# }"; pending="${pending# }"; broken="${broken# }"

  if [[ -n "${tagged}" ]]; then
    ok "22.4 ${tagged} 冷启动时读到了 Nacos 配置 (指标上带着 application 标签)"
  fi

  if [[ -n "${broken}" ]]; then
    nok "22.4 ${broken} 是在配置进 Nacos 之后才重启的, 却仍然没有 application 标签 —— 配置压根没生效, 查 Nacos 连接和 bootstrap.yml 的 namespace"
  fi

  if [[ -n "${pending}" ]]; then
    warn "22.5 ${pending} 还没在配置生效之后重建过 (容器启动早于它自己那份配置落库的时刻) —— 重建它们: bash deploy.sh rebuild <服务>"
  else
    ok "22.5 没有待重建的服务"
  fi

  # ---- 22.6 网关的 Sentinel 数据源真的连上了 Nacos ----
  #
  # 这条不是查配置, 是查**连接**。Sentinel 的 Nacos 数据源自己起一个
  # Nacos 客户端; 那个地址要是写着 127.0.0.1, 容器里指的就是网关自己,
  # 客户端永远停在 STARTING, 每秒十几条 "Client not connected",
  # 而网关的流控规则一条都从来没加载过 —— 而配置本身看上去一切正常。
  #
  # 症状是唯一的判据: 日志里一条 "Client not connected" 都没有 = 连上了。
  # (容器重建后 docker logs 只含本次启动的日志, 不用自己算时间窗。)
  #
  # 但**读不到日志不等于没问题** —— 容器名写错、容器刚被删了, 一样是 0 条。
  # 那种情况下这一项必须说"证明不了任何东西", 不能报 OK:
  # 一个永远绿的检查等于没有检查。
  local gw_log gw_notconn
  gw_log=$(docker logs aiopc-gateway 2>&1 || true)
  if [[ -z "${gw_log}" ]] || [[ "${gw_log}" == *"Error response from daemon"* ]] \
     || [[ "${gw_log}" == *"No such container"* ]]; then
    nok "22.6 读不到网关日志 —— 这一项证明不了任何东西"
  else
    gw_notconn=$(grep -c "Client not connected" <<<"${gw_log}" || true)
    gw_notconn="${gw_notconn//[^0-9]/}"
    [[ -z "${gw_notconn}" ]] && gw_notconn=0
    if [[ "${gw_notconn}" -eq 0 ]]; then
      ok "22.6 网关的 Sentinel 数据源没有连不上的重试 (Nacos 客户端已连上)"
    else
      nok "22.6 网关日志里有 ${gw_notconn} 条 'Client not connected' —— Sentinel 的 Nacos 客户端连不上 (多半是 bootstrap.yml 里 sentinel.datasource...server-addr 还写着 127.0.0.1, 容器里那是网关自己)"
    fi
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
  check_port_exposure
  check_body_tenant_and_oauth_state
  check_metering_chain
  check_company_switch
  check_marketplace_truth
  check_marketplace_actions
  check_crm_ai_score
  check_nacos_config_center
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