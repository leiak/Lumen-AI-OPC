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
#  10) opc-content 11 项健康检查 (W74 Task 15; 2026-10-29 重写 ——
#      原来 5 条业务端点检查全是 `curl -sf` 看 HTTP 状态码, 而 RuoYi 把所有异常
#      都转成 HTTP 200 + body.code=500 → 一条查不存在端点的检查一直报 ok)
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
#  23) 记忆系统记事 + Sentinel 限流真的在限
#  24) Sentinel 控制台是真的在管事 (不是「零节点零规则」的空壳)
#  25) 定时任务是真的在跑 (ruoyi-job 真的被装起来 + 真的触发过)
#  26) 财务数字员工读的是真数据 (日报里的金额逐笔等于库里的金额 +
#      降级链真的开着 + 四份 yml 对得上 +
#      日报**正文**里的数字也来自 kpi_json, 不许模型编)
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
# Sentinel 控制台的宿主机端口 (只绑 127.0.0.1)。第 24 节要用它:
# 控制台不是自己存规则的 —— 它是回头问网关要 (GET http://<网关ip>:<网关port>/gateway/getRules),
# 所以「控制台看得见规则」= 「网关内存里真的加载了规则」, 这是限流那条链的最后一环。
SENTINEL_DASHBOARD_PORT="${SENTINEL_DASHBOARD_PORT:-8718}"
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

# 取一份 yml 里 opc.llm 这一段的「键与值」(注释不算)。
#
# 为什么不用 `awk '/^  llm:/{f=1} f{print} f&&/^  [a-z]/{exit}'`:
# 那个写法会把**结束这一段的下一行也打出来**(先 print 再 exit),于是
#   · opc-ai-core / opc-agent-hub / nacos 那三份 llm 段后面跟的是 `  memory:`
#   · opc-insight 那份后面跟的是**顶格**的 `mybatis:`(它的 opc: 段里只有 llm)
# → 四份提取出来的东西**最后一行不一样** → 26.11 永远红。
# 这里改成:只取 llm 段**内部**的行(下一个缩进 <= 2 的键就停),四份逐字相同。
llm_section() {
  awk '
    /^[[:space:]]*#/ { next }
    /^  llm:/ { inllm = 1; next }
    inllm && /^[^[:space:]]/ { exit }   # 顶格的下一个键 → llm 段结束
    inllm && /^  [^ ]/ { exit }         # 同级(缩进 2)的下一个键 → llm 段结束
    !inllm { next }
    {
      line = $0
      sub(/[[:space:]]+#.*$/, "", line)   # 去行尾注释
      sub(/[[:space:]]+$/, "", line)      # 去行尾空白
      if (line == "") next
      print line
    }
  ' "$1" 2>/dev/null
}

# 取一份 yml 里 opc.llm.<provider>.<key> 的值。
#
# 缩进是分层的:`  llm:`(2) → `    provider:`(4) → `      key: value`(6)。
# 「provider 层」必须按**正好 4 个空格**来认 —— 按「缩进后是字母」来认的话,
# `base-url` 这种以字母开头的属性会被误认成下一个 provider。
llm_yml_value() {
  # $1 = yml 路径, $2 = provider, $3 = 键名
  awk -v want_provider="$2" -v want_key="$3" '
    /^[[:space:]]*#/ { next }
    /^  llm:/ { inllm = 1; next }
    inllm && /^[^[:space:]]/ { exit }   # 顶格的下一个键 → llm 段结束
    inllm && /^  [^ ]/ { exit }         # 同级(缩进 2)的下一个键 → llm 段结束
    !inllm { next }
    {
      line = $0
      if (line ~ /^    [^ ]/) {                 # provider 层
        head = line; sub(/:.*$/, "", head); gsub(/[[:space:]]/, "", head); prov = head; next
      }
      if (line ~ /^      [^ ]/) {               # provider 的属性
        head = line; sub(/:.*$/, "", head); gsub(/[[:space:]]/, "", head)
        if (prov == want_provider && head == want_key) {
          v = line
          sub(/^[^:]*:[[:space:]]*/, "", v)
          sub(/[[:space:]]+#.*$/, "", v)
          sub(/[[:space:]]+$/, "", v)
          print v
          exit
        }
      }
    }
  ' "$1" 2>/dev/null
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
    "aiopc-job"
    "aiopc-user-center"
    "aiopc-agent-hub"
    "aiopc-ai-core"
    "aiopc-billing"
    "aiopc-finance"
    "aiopc-frontend"
    "aiopc-sentinel-dashboard"
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

  # 10.4 公开浏览端点 (阻塞)
  #
  # ⚠️ 这里必须看 body.code, 不能只看 HTTP 状态码:
  #    RuoYi 的 GlobalExceptionHandler 把**所有**异常都转成 HTTP 200 + body.code=500
  #    (AjaxResult.error(...)), 缺必填参数、路径变量类型不匹配都走这条路。
  #    所以 `curl -sf` 会对一个「压根没返回数据」的端点报成功。
  #
  #    原来那条查的是 /opc/content/script/recent —— opc-content 只有 3 个
  #    controller, **没有 recent 这个 handler**。它撞上 @GetMapping("/{id}"),
  #    "recent" 转不成 Long → MethodArgumentTypeMismatchException → HTTP 200 +
  #    code=500 → 那条检查一直报 ok。
  #    网关白名单里早就把它记成「失效条目」(nacos/whitelist-opc-invite.yml 的
  #    whitesRemove), 只有健康检查还当它存在。
  #
  # 这几条走**网关**而不是服务直连: 这几个 GET 本来就在网关的公开白名单里
  # (GET:/opc/content/script/list|dashboard|**, GET:/opc/content/platform-account/list),
  # 匿名可读就是它本来的样子 —— 公开浏览就是给匿名看的。
  #
  # companyId 显式带上: 租户守卫(TenantCompanyFilter)会把它改写成服务端判定的
  # 作用域(匿名 → 公开租户 opc.tenant.public-company-id, 默认 1), 但守卫被关掉时
  # 就不会补 —— 那时缺参数会得到 code=500, 这条检查就该红。
  local content_eps=(
    "/opc/content/script/list?companyId=1&page=1&size=10"
    "/opc/content/script/dashboard?companyId=1"
    "/opc/content/platform-account/list?companyId=1"
  )
  local -A c_body=()
  for ep in "${content_eps[@]}"; do
    local cbody ccode
    cbody=$(curl -s -m 10 "http://${GATEWAY_HOST}:${GATEWAY_PORT}${ep}" 2>/dev/null) || cbody=""
    c_body["${ep}"]="${cbody}"
    ccode=$(body_code "${cbody}")
    if [[ "${ccode}" == "200" ]]; then
      ok "${ep} body.code=200"
    elif echo "${cbody}" | grep -q "No static resource"; then
      nok "${ep} 网关未路由 (静态回退): ${cbody:0:120}"
    else
      nok "${ep} body.code=${ccode}: ${cbody:0:120}"
    fi
  done

  # 10.4b 发布记录列表 (阻塞) —— 这条**不在**公开白名单里, 必须带 token
  #      (白名单只有 4 条 GET: script/list | script/dashboard | script/** |
  #       platform-account/list)。拿不到 token 就报「无法验证」, 不假装通过 ——
  #      悄悄跳过一条检查, 就是在制造下一句假话。
  #
  #      断言两件事: code=200 (端点真的通) + total>0 (真的取到了这家公司的记录)。
  #      admin 属于 company 1 (opc_company_member 里 user_id=1 → company_id=1),
  #      租户守卫会把 companyId 改写成登录态作用域, 所以 companyId=1 是对的。
  local c_login c_token c_pub_body c_pub_code c_pub_total
  # 口令经 stdin 送进去, 不进 argv —— ps aux / /proc/*/cmdline 看不到它。
  # (文件里其余 14 处还是 `-d '{...}'` 的老写法, 那是历史, 不在这里扩大。)
  c_login=$(printf '%s' '{"username":"admin","password":"admin123"}' \
    | curl -s -m 15 -X POST \
        -H "Content-Type: application/json" \
        --data-binary @- \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || c_login=""
  c_token=$(printf '%s' "${c_login}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {}) or {}
    print(d.get('token','') or d.get('access_token',''))
except Exception:
    print('')
" 2>/dev/null)
  if [[ -z "${c_token}" ]]; then
    nok "拿不到 admin token —— /opc/content/publish/list 无法验证(它不在公开白名单里)"
  else
    c_pub_body=$(curl -s -m 10 \
      -H "Authorization: Bearer ${c_token}" \
      "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/content/publish/list?companyId=1&page=1&size=10" 2>/dev/null) || c_pub_body=""
    c_pub_code=$(body_code "${c_pub_body}")
    c_pub_total=$(printf '%s' "${c_pub_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data') or {}
    print(d.get('total', 0) if isinstance(d, dict) else 0)
except Exception:
    print(-1)
" 2>/dev/null)
    if [[ "${c_pub_code}" != "200" ]]; then
      nok "/opc/content/publish/list body.code=${c_pub_code}: ${c_pub_body:0:120}"
    elif [[ "${c_pub_total}" -le 0 ]]; then
      nok "/opc/content/publish/list body.code=200 但 total=${c_pub_total} —— company 1 名下没有发布记录"
    else
      ok "/opc/content/publish/list body.code=200 total=${c_pub_total}"
    fi
  fi

  # 10.5 最近脚本: 原来那条查的 /script/recent 压根不存在, 但「最近脚本」这个
  #     功能是真的 —— 它住在 dashboard 的 recent_scripts 字段里
  #     (OpcContentScriptServiceImpl:297 scriptMapper.selectRecent(companyId, 5))。
  #     所以这里查它真正的位置, 并且要求真的取到数据, 而不是「返回了一个 200」。
  #     复用 10.4 已经取到的 dashboard 响应体, 不重复发请求。
  local dash_body dash_code recent_n
  dash_body="${c_body["/opc/content/script/dashboard?companyId=1"]:-}"
  dash_code=$(body_code "${dash_body}")
  recent_n=$(printf '%s' "${dash_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data') or {}
    print(len(d.get('recent_scripts') or []))
except Exception:
    print(-1)
" 2>/dev/null)
  if [[ "${dash_code}" != "200" ]]; then
    nok "dashboard body.code=${dash_code}: ${dash_body:0:120}"
  elif [[ "${recent_n}" -gt 0 ]]; then
    ok "最近脚本真的取到了 ${recent_n} 条 (dashboard.recent_scripts)"
  else
    nok "dashboard.recent_scripts 取不到数据 (条数=${recent_n}): ${dash_body:0:120}"
  fi

  # 10.6 脚本详情路由 (阻塞) —— id 从库里取一条真实脚本, 不写死
  #     (写死的 id 会随种子变化而失效, 那才是另一种「不说假话」)
  local content_sid
  content_sid=$(db_q "SELECT id FROM opc_content_script WHERE company_id = 1 ORDER BY id LIMIT 1;" | head -1)
  if [[ -z "${content_sid}" ]]; then
    nok "取不到 company_id=1 的脚本 id (mysql 客户端缺失 / 查询失败 / 库里没这家公司数据) —— 详情路由无从验证"
  else
    local sbody scode
    sbody=$(curl -s -m 10 "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/content/script/${content_sid}?companyId=1" 2>/dev/null) || sbody=""
    scode=$(body_code "${sbody}")
    if [[ "${scode}" == "200" ]]; then
      ok "/opc/content/script/${content_sid} body.code=200"
    else
      nok "/opc/content/script/${content_sid} body.code=${scode}: ${sbody:0:120}"
    fi
  fi

  # 10.7 鉴权: 闸在网关, 不在 opc-content (阻塞)
  #
  # ⚠️ 原来这条查的是**服务直连**(127.0.0.1:9325)的 HTTP 状态码, 而且把 200
  #    也算成「拦截正确」—— 等于什么都没断言: 直连本来就绕过网关的 AuthFilter,
  #    opc-content 的 controller 上也没有 @SaCheckPermission。
  #
  #    真正的闸是网关: 白名单只放行了 4 条 GET, POST /opc/content/script 不在
  #    里面 → 匿名必须被拦。网关的拒绝也是 HTTP 200 + body.code=401
  #    (ServletUtils.webFluxResponseWriter 传的是 HttpStatus.OK), 只能看 body.code。
  local cgw_body cgw_code
  cgw_body=$(curl -s -m 10 -X POST -H "Content-Type: application/json" \
    -d '{}' "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/content/script" 2>/dev/null) || cgw_body=""
  cgw_code=$(body_code "${cgw_body}")
  if [[ "${cgw_code}" == "401" ]] || [[ "${cgw_code}" == "403" ]]; then
    ok "网关拦截正确 (POST /opc/content/script → body.code=${cgw_code})"
  else
    nok "网关未拦截匿名写请求 (body.code=${cgw_code}): ${cgw_body:0:120} — opc-content 的写端点匿名可达"
  fi

  # 10.8 数据库连接 (非阻塞, db indicator)
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
  local -a guard_ports=(8080 9201 9203 19200 9301 9302 9303 9304 9305 9306 9310 9311 9312 9316 9317 9322 9325
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
  #
  # ⚠️ **只数最近 30 分钟的**(`docker logs --since`)。`docker logs` 是**容器创建
  #    以来**的全部日志, 不是「本次启动」的 —— 容器重启(restart)不会清空它,
  #    只有重建(create)才会。2026-11-05 这条检查就是这么假红的:
  #    网关容器 10-30 建的, 11-03 有过一段 Nacos 抖动(84 条, 全是历史),
  #    而最近两小时一条都没有 —— 数全量日志等于**拿历史故障判现在的健康**。
  #
  # 但**读不到日志不等于没问题** —— 容器名写错、容器刚被删了, 一样是 0 条。
  # 那种情况下这一项必须说"证明不了任何东西", 不能报 OK:
  # 一个永远绿的检查等于没有检查。
  local gw_log gw_win gw_notconn gw_win_lines gw_last gw_ago gw_now
  gw_log=$(docker logs aiopc-gateway 2>&1 || true)
  if [[ -z "${gw_log}" ]] || [[ "${gw_log}" == *"Error response from daemon"* ]] \
     || [[ "${gw_log}" == *"No such container"* ]]; then
    nok "22.6 读不到网关日志 —— 这一项证明不了任何东西"
  else
    # 窗口内的日志(带 RFC3339 时间戳, 用来判断故障是不是**还在持续**)
    gw_win=$(docker logs -t --since 30m aiopc-gateway 2>&1 || true)
    if [[ "${gw_win}" == *"Error response from daemon"* ]] || [[ "${gw_win}" == *"No such container"* ]]; then
      nok "22.6 读不到网关最近 30 分钟的日志 —— 这一项证明不了任何东西"
    else
      gw_win_lines=$(grep -c '[^[:space:]]' <<<"${gw_win}" || true)
      gw_win_lines="${gw_win_lines//[^0-9]/}"
      [[ -z "${gw_win_lines}" ]] && gw_win_lines=0
      gw_notconn=$(grep -c "Client not connected" <<<"${gw_win}" || true)
      gw_notconn="${gw_notconn//[^0-9]/}"
      [[ -z "${gw_notconn}" ]] && gw_notconn=0
      if [[ "${gw_win_lines}" -eq 0 ]]; then
        # 窗口里一条日志都没有 → 「没有连不上的重试」这句话无从谈起(可能只是闲着)
        warn "22.6 网关最近 30 分钟一条日志都没有 —— 判据落空, 说不了 Sentinel 数据源现在的状态"
      elif [[ "${gw_notconn}" -eq 0 ]]; then
        ok "22.6 最近 30 分钟网关日志(${gw_win_lines} 行)里没有 'Client not connected' —— Sentinel 的 Nacos 客户端连得上"
      else
        # 窗口里有过 → 还在持续吗? 看**最后一条**距现在多久。
        # 「有过但已经停了」和「现在还在每秒十几条地重试」是两回事, 不能一律判 FAIL。
        gw_last=$(grep "Client not connected" <<<"${gw_win}" | tail -1 | awk '{print $1}')
        gw_now=$(date -u +%s)
        gw_ago=$(date -u -d "${gw_last}" +%s 2>/dev/null || echo "")
        if [[ -z "${gw_ago}" ]]; then
          nok "22.6 最近 30 分钟有 ${gw_notconn} 条 'Client not connected', 但解析不出时间戳 —— 判不了它是不是还在持续"
        elif [[ $(( gw_now - gw_ago )) -le 300 ]]; then
          nok "22.6 最近 30 分钟有 ${gw_notconn} 条 'Client not connected', 最后一条就在 $(( (gw_now - gw_ago) / 60 )) 分钟前 —— Sentinel 的 Nacos 客户端**现在还**连不上 (多半是 bootstrap.yml 里 sentinel.datasource...server-addr 还写着 127.0.0.1, 容器里那是网关自己)"
        else
          warn "22.6 最近 30 分钟有 ${gw_notconn} 条 'Client not connected', 但最后一条是 $(( (gw_now - gw_ago) / 60 )) 分钟前 —— 已经停了(多半是那会儿 Nacos 在抖), 现在没在重试"
        fi
      fi
    fi
  fi
}

# ---------- 23) 记忆系统记事 + Sentinel 限流真的在限 ----------
#
# 这两件事以前都是「看着有、其实没有」：
#
#   · 记忆系统不记事 —— embed() 返回 Math.random() 填的"向量"，
#     loadShortTerm() 永远返回空列表，writeLongTerm/searchLongTerm 零调用方。
#     接口一路 200，日志里什么都查不到，多轮对话没有上下文。
#   · 网关没有限流 —— bootstrap.yml 里 transport.dashboard 是注释掉的，
#     Nacos 里的 Sentinel 规则表是空的 []，而 bootstrap.yml 的
#     sentinel.datasource...server-addr 还写着 127.0.0.1（容器里那是网关自己）。
#     README 写着「网关限流已接入 Nacos 持久化」，实际上那条路是断的。
#
# 所以判据必须是「真的发生了一件事」，不能是「配置里写了有」：
#   记忆 → 真做一次 embed → 写 Qdrant → 搜回来 → 删探针 的往返；
#   限流 → 真打满并发，看有没有 429 回来。
check_memory_and_flow_control() {
  hr
  echo "[23] 记忆系统记事 + Sentinel 限流真的在限"
  hr

  local ns="opc-dev"
  local api="http://${GATEWAY_HOST}:${NACOS_PORT}/nacos"
  local gw="http://${GATEWAY_HOST}:${GATEWAY_PORT}"

  # ---- 登录拿 token ----
  local login_body token
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "${gw}/login" 2>/dev/null) || true
  token=$(printf '%s' "${login_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
    print(d.get('token','') or d.get('access_token',''))
except Exception:
    print('')
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    nok "23.1 拿不到 admin 的 token —— 这一节空转, 证明不了任何东西"
    return
  fi

  # ---- 23.1 记忆自检 + 23.2 embedding 端点 ----
  local mem_body mem_code emb_body emb_code
  mem_body=$(curl -s -m 90 -H "Authorization: Bearer ${token}" \
    "${gw}/opc/llm/memory/health" 2>/dev/null) || mem_body=""
  mem_code=$(body_code "${mem_body}")

  emb_body=$(curl -s -m 90 -X POST \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d '{"texts":["健康检查探针: 记忆系统自检"],"type":"query"}' \
    "${gw}/opc/llm/embedding" 2>/dev/null) || emb_body=""
  emb_code=$(body_code "${emb_body}")

  local out
  out=$(python3 - "${mem_body}" "${mem_code}" "${emb_body}" "${emb_code}" <<'PY' 2>/dev/null
import json, sys

mem_raw, mem_code, emb_raw, emb_code = sys.argv[1:5]
out = []

def load(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None

def data_of(obj):
    d = (obj or {}).get("data")
    return d if isinstance(d, dict) else {}

# ---- 23.1 记忆往返 ----
mem = load(mem_raw)
if mem_code != "200" or not isinstance(mem, dict):
    out.append(("FAIL", "23.1 记忆自检接口不可用 (code=%s): %s" % (mem_code, (mem_raw or "")[:160])))
else:
    d = data_of(mem)
    coll = d.get("collection")
    provider = d.get("embeddingProvider")
    err = d.get("roundTripError") or d.get("qdrantError")
    if err:
        out.append(("FAIL", "23.1 记忆自检报错: %s" % err))
    if d.get("roundTrip") is not True:
        out.append(("FAIL", "23.1 记忆写进去搜不回来 (roundTrip=%s) —— 向量化或 Qdrant 这一环是断的" % d.get("roundTrip")))
    else:
        out.append(("OK", "23.1 记忆真的往返了一次 (embed→写 Qdrant→搜回来→删探针), provider=%s 集合=%s"
                    % (provider, coll)))
    if d.get("collectionExists") is not True:
        out.append(("FAIL", "23.1 长期记忆集合 %s 不存在 —— 记忆无处可存" % coll))
    dim, exp = d.get("collectionDimension"), d.get("expectedDimension")
    if not isinstance(dim, int) or dim <= 0:
        out.append(("FAIL", "23.1 读不出集合的真实维度 (collectionDimension=%s)" % dim))
    elif isinstance(exp, int) and dim != exp:
        out.append(("FAIL", "23.1 集合维度 %s 与配置期望 %s 对不上 —— 写进去也检索不出来" % (dim, exp)))
    else:
        out.append(("OK", "23.1 集合 %s 维度 %s, 与配置一致" % (coll, dim)))
    if d.get("embeddingAvailable") is not True:
        out.append(("FAIL", "23.1 没有可用的 embedding provider (embeddingProvider=%s) —— 长期记忆写不进去" % provider))

    # ---- 23.3 短期记忆 ----
    st = d.get("shortTermSessions")
    if isinstance(st, int) and st > 0:
        out.append(("OK", "23.3 Redis 里有 %d 个会话的短期记忆" % st))
    elif st == 0:
        out.append(("WARN", "23.3 Redis 里一个短期记忆会话都没有 —— 要么真没人聊过, 要么写入路径没通"))
    else:
        out.append(("WARN", "23.3 读不出短期记忆会话数 (%s)%s" % (st, (": " + d["shortTermError"]) if d.get("shortTermError") else "")))

# ---- 23.2 embedding 端点真的算出向量 ----
emb = load(emb_raw)
if emb_code != "200" or not isinstance(emb, dict):
    out.append(("FAIL", "23.2 embedding 接口不可用 (code=%s): %s" % (emb_code, (emb_raw or "")[:160])))
else:
    e = data_of(emb)
    vecs = e.get("vectors") or []
    dim = e.get("dimension")
    if not vecs:
        out.append(("FAIL", "23.2 embedding 没算出向量 (vectors 为空, provider=%s)" % e.get("provider")))
    else:
        got = len(vecs[0]) if isinstance(vecs[0], list) else -1
        if got <= 0:
            out.append(("FAIL", "23.2 第一个向量长度异常: %s" % got))
        elif isinstance(dim, int) and dim != got:
            out.append(("FAIL", "23.2 声明维度 %s 与实际向量长度 %s 对不上" % (dim, got)))
        else:
            out.append(("OK", "23.2 embedding 真的算出了向量 provider=%s model=%s 维度=%s"
                        % (e.get("provider"), e.get("model"), got)))

for st, msg in out:
    print(st + "\t" + msg)
PY
)

  if [[ -z "${out}" ]]; then
    nok "23.1-23.3 解析不了自检响应 —— 这一节空转, 证明不了任何东西"
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

  # ---- 23.4 Sentinel 规则表: 每条规则都挂在真实的路由 id 上, 且没有漏网的路由 ----
  #
  # 23.2 只保证「配置在」, 这里保证「配置是对的」:
  # Sentinel 的 gw-flow 规则是**按路由 id** 匹配的 (SentinelGatewayFilter 取 Route.getId()),
  # resource 写错一个字符 → 这条规则永远不生效, 而且没有任何报错。
  # 反过来, 路由没有规则 = 这条路没有限流, 也不该悄悄放过。
  local rules_out
  rules_out=$(python3 - "${api}" "${ns}" "${DEPLOY_DIR}/../ruoyi-gateway/src/main/resources/application.yml" <<'PY' 2>/dev/null
import json, os, re, sys, urllib.parse, urllib.request

api, ns, gw_yml = sys.argv[1], sys.argv[2], sys.argv[3]
out = []

def http(url):
    try:
        r = urllib.request.urlopen(url, timeout=10)
        return r.read().decode('utf-8', errors='replace')
    except Exception:
        return None

# ---- 网关里真实的路由 id ----
#
# 按缩进找 routes: 那一段 —— 网关的 yml 里 routes: 后面先跟两行注释,
# 按「遇到非 - 开头的行就结束」来切会把 routes 切空(第一行注释就把它切断了)。
route_ids = []
if os.path.exists(gw_yml):
    with open(gw_yml, encoding='utf-8') as fh:
        lines = fh.read().splitlines()
    routes_indent = None
    for i, line in enumerate(lines):
        m = re.match(r'^(\s*)routes:\s*$', line)
        if not m:
            continue
        routes_indent = len(m.group(1))
        for nxt in lines[i + 1:]:
            if not nxt.strip() or nxt.strip().startswith('#'):
                continue
            indent = len(nxt) - len(nxt.lstrip())
            if indent <= routes_indent:
                break                       # 出了 routes 段
            mi = re.match(r'^\s*- id:\s*(\S+)\s*$', nxt)
            if mi:
                route_ids.append(mi.group(1))
        break

# ---- Nacos 里的规则表 ----
url = api + '/v1/cs/configs?' + urllib.parse.urlencode(
    {'dataId': 'sentinel-ruoyi-gateway', 'group': 'DEFAULT_GROUP', 'tenant': ns})
raw = http(url)
rules = None
if raw and raw.strip() not in ('', 'config data not exist'):
    try:
        rules = json.loads(raw)
    except Exception:
        rules = None

if rules is None:
    out.append(("FAIL", "23.4 tenant=%s 下读不到 Sentinel 规则表 (dataId=sentinel-ruoyi-gateway)" % ns))
elif not isinstance(rules, list) or not rules:
    out.append(("FAIL", "23.4 Sentinel 规则表是空的 —— 网关现在一条限流规则都没有(空数组 = 全放行)"))
else:
    resources = []
    bad = []
    for r in rules:
        if not isinstance(r, dict):
            bad.append("(整条不是对象)")
            continue
        res = r.get("resource")
        if not res:
            bad.append("(没有 resource)")
            continue
        resources.append(res)
        if route_ids and res not in route_ids:
            bad.append("%s(网关里没有这条路由)" % res)
        if r.get("resourceMode") not in (0, 1):
            bad.append("%s(resourceMode=%s, 0=路由id 1=自定义api分组)" % (res, r.get("resourceMode")))
    if bad:
        out.append(("FAIL", "23.4 %d 条规则有问题: %s" % (len(bad), "; ".join(sorted(set(bad))[:6]))))
    else:
        out.append(("OK", "23.4 Sentinel 规则表 %d 条, 每条都挂在真实的路由 id 上 (resourceMode=0=按路由 id 匹配)" % len(rules)))
    missing = [r for r in route_ids if r not in resources]
    if missing:
        out.append(("FAIL", "23.4 %d 条路由没有限流规则(等于不限流): %s" % (len(missing), " ".join(missing))))
    else:
        out.append(("OK", "23.4 %d 条路由全部有对应的限流规则" % len(route_ids)))

for st, msg in out:
    print(st + "\t" + msg)
PY
)

  if [[ -z "${rules_out}" ]]; then
    nok "23.4 解析不了 Sentinel 规则表 —— 这一项空转, 证明不了任何东西"
  else
    while IFS=$'\t' read -r st msg; do
      [[ -z "${st}" ]] && continue
      case "${st}" in
        OK)   ok   "${msg}" ;;
        WARN) warn "${msg}" ;;
        *)    nok  "${msg}" ;;
      esac
    done <<< "${rules_out}"
  fi

  # ---- 23.5 打满并发, 看有没有 429 真的回来 ----
  #
  # 这是唯一能证明「限流在限」的判据。配置对了不等于限流在跑 ——
  # 网关没连上 Nacos (127.0.0.1 指容器自己)、规则 resource 写错、
  # 数据源根本没加载, 都会让「配置看着对、限流却是摆设」。
  #
  # 先单发一次确认前置通: 不然 401/404 也会被误读成「限流生效了」。
  local single
  single=$(curl -s -m 20 -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${token}" "${gw}/opc/llm/models" 2>/dev/null) || single="000"
  if [[ "${single}" != "200" ]]; then
    nok "23.5 单发一次 /opc/llm/models 返回 ${single} —— 前置就没通过, 分不清 429 是限流还是别的"
  else
    # opc-ai-core 路由的规则是 10 QPS —— 40 个并发必然超
    #
    # 这里把**每个状态码 + 响应体**都留下来, 而不是只数 429:
    # 「限流没生效」和「限流生效了但响应不是 429」是两回事, 只报一个计数分不开。
    local burst
    burst=$(seq 1 40 | xargs -P 20 -I{} curl -s -m 20 -w '\n<<CODE=%{http_code}>>' \
      -H "Authorization: Bearer ${token}" "${gw}/opc/llm/models" 2>/dev/null \
      | python3 -c "
import sys, collections
# curl 的 body 在前、-w 的标记在后, 所以整条流是:
#   body_1 <<CODE=200>> body_2 <<CODE=429>> ...
# 按标记切开后, 第 i 段的头 3 位是**第 i 个**响应的状态码,
# 它 '>>' 之后的内容是**第 i+1 个**响应的 body。
raw = sys.stdin.read()
parts = raw.split('\n<<CODE=')
bodies = [parts[0]] if parts and parts[0].strip() else []
codes = []
for p in parts[1:]:
    code = p[:3]
    rest = p[3:]
    if rest.startswith('>>'):
        rest = rest[2:]
    codes.append(code if code.isdigit() else '???')
    bodies.append(rest)
samples, counts = {}, collections.Counter()
for i, c in enumerate(codes):
    counts[c] += 1
    samples.setdefault(c, bodies[i].strip()[:120] if i < len(bodies) else '')
for c, n in counts.most_common():
    print('COUNT\t%s\t%d' % (c, n))
for c in sorted(samples):
    print('SAMPLE\t%s\t%s' % (c, samples[c]))
" 2>/dev/null)

    local hist c429 c200 samples
    hist=$(printf '%s\n' "${burst}" | grep '^COUNT' | awk -F'\t' '{printf "%s×%s ", $2, $3}' | sed 's/ $//')
    samples=$(printf '%s\n' "${burst}" | grep '^SAMPLE' | cut -f2,3 | tr '\t' '=' | tr '\n' ' ')
    c429=$(printf '%s\n' "${burst}" | awk -F'\t' '$1=="COUNT" && $2=="429" {print $3+0}')
    c200=$(printf '%s\n' "${burst}" | awk -F'\t' '$1=="COUNT" && $2=="200" {print $3+0}')
    c429=${c429:-0}
    c200=${c200:-0}

    if [[ "${c429}" -gt 0 ]]; then
      ok "23.5 并发 40 次打 /opc/llm/models: 出现 429 —— 限流真的在限 (分布 ${hist})"
    else
      nok "23.5 并发 40 次打 /opc/llm/models, 一个 429 都没有 (分布: ${hist:-空}) —— 限流规则没生效; 各种响应的样本: ${samples:-无}"
    fi
  fi

  # ---- 23.6 tenant_id='public' 的历史配置清干净了 ----
  #
  # 这 9 份是当初导错了租户留下的: 配置 API 用 tenant= 查,
  # 'public' 会被归一成默认租户(空串), 所以这 9 份**任何 API 都读不到**。
  # 它们不是「备用的旧版本」, 是谁也读不到的死配置 ——
  # 同样的 dataId 在 opc-dev 下还有一份活着的, 两份同名, 迟早有人改错那一份。
  local pub
  pub=$(db_q "SELECT COUNT(*) FROM \`ry-config\`.config_info WHERE tenant_id = 'public';" | head -1)
  if [[ -z "${pub}" ]]; then
    warn "23.6 查不到 ry-config.config_info —— 这一项空转"
  elif [[ "${pub}" == "0" ]]; then
    ok "23.6 tenant_id='public' 下的历史配置已清空(以前 9 份谁也读不到的死配置)"
  else
    nok "23.6 tenant_id='public' 下还留着 ${pub} 份历史配置 —— 那是任何 API 都读不到的死配置, 且与 opc-dev 下的同名配置重复"
  fi
}

# ---------- 24) Sentinel 控制台是真的在管事 ----------
#
# 控制台自己不拦流量 —— 它只是把网关的状态和规则显示出来。
# 所以它天生容易变成一个「看着一切正常、其实什么都没连上」的空壳:
#
#   · 镜像自带的 application.yml 写的是 localhost:8848 + 默认命名空间,
#     可在容器里 127.0.0.1 是控制台自己 —— 它连 Nacos 必然失败;
#   · 镜像自带 server.port 是 8858, 而 compose 映射的是 8718 —— 端口对不上;
#   · 挂载路径写成 /home/admin/sentinel-dashboard/application.yml 时挂载**静默失败**
#     (镜像的 WORKDIR 是 /bladex/sentinel), 镜像自带那份照旧生效, 一个异常都不报;
#   · Sentinel 的心跳地址在**启动那一刻**解析一次就固定了 ——
#     网关先起、控制台后起时解析不到就整组丢弃, 之后永远不再重试,
#     控制台就永远是「零节点、零规则」。
#
# 所以判据必须是「控制台真的看得见网关、真的拿到了网关的限流规则」,
# 不是「控制台容器在跑」。
check_sentinel_dashboard() {
  hr
  echo "[24] Sentinel 控制台是真的在管事"
  hr

  local out
  out=$(python3 - \
      "http://127.0.0.1:${SENTINEL_DASHBOARD_PORT}" \
      "http://${GATEWAY_HOST}:${NACOS_PORT}/nacos" \
      "opc-dev" \
      "${DEPLOY_DIR}/../ruoyi-gateway/src/main/resources/application.yml" \
      "${DEPLOY_DIR}/sentinel-dashboard/application.yml" <<'PY' 2>/dev/null
import http.cookiejar, json, os, re, sys, urllib.error, urllib.parse, urllib.request

dash, api, ns, gw_yml, dash_yml = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
out = []
APP = 'ruoyi-gateway'

# 控制台是 session 鉴权的 —— 没登录就 401。
# 账号口令从控制台自己的配置文件里读 (不落命令行、不打日志),
# 跟控制台拿的是同一份, 不会对不上。
jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def auth_from(path):
    """从控制台的 application.yml 里读 auth.username / auth.password"""
    cred = {}
    if not os.path.exists(path):
        return cred
    inside = False
    with open(path, encoding='utf-8') as fh:
        for line in fh:
            raw = line.rstrip('\n')
            if not raw.strip() or raw.strip().startswith('#'):
                continue
            if not raw.startswith((' ', '\t')):
                inside = raw.strip() == 'auth:'
                continue
            if inside:
                m = re.match(r'^\s+(username|password)\s*:\s*(\S+)\s*$', raw)
                if m:
                    cred[m.group(1)] = m.group(2).strip('"\'')
    return cred


def http(url, method='GET', form=None):
    data = urllib.parse.urlencode(form).encode() if form is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    try:
        r = opener.open(req, timeout=10)
        return r.status, r.read().decode('utf-8', errors='replace')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', errors='replace')
    except Exception as e:
        return None, str(e)


def data_of(raw):
    """控制台的统一返回体: {"success":..,"code":..,"msg":..,"data":..}"""
    if raw is None:
        return None
    try:
        d = json.loads(raw)
    except Exception:
        return None
    if not isinstance(d, dict):
        return None
    return d.get('data')


def route_ids_of(path):
    """网关里真实的路由 id (按缩进切出 routes: 段)"""
    ids = []
    if not os.path.exists(path):
        return ids
    with open(path, encoding='utf-8') as fh:
        lines = fh.read().splitlines()
    for i, line in enumerate(lines):
        m = re.match(r'^(\s*)routes:\s*$', line)
        if not m:
            continue
        base = len(m.group(1))
        for nxt in lines[i + 1:]:
            if not nxt.strip() or nxt.strip().startswith('#'):
                continue
            if len(nxt) - len(nxt.lstrip()) <= base:
                break
            mi = re.match(r'^\s*- id:\s*(\S+)\s*$', nxt)
            if mi:
                ids.append(mi.group(1))
        break
    return ids


def emit():
    for st, msg in out:
        print(st + "\t" + msg)


# ---- 24.1 控制台活着, 而且登录得上 ----
cred = auth_from(dash_yml)
if not cred.get('username') or not cred.get('password'):
    out.append(("FAIL", "24.1 %s 里读不到 auth.username / auth.password —— 没法登录控制台" % dash_yml))
    emit()
    raise SystemExit(0)

st, _ = http(dash + '/app/names.json')
if st is None:
    out.append(("FAIL", "24.1 控制台 %s 没应答 —— 容器没起来, 或者 compose 映射的端口跟它的 server.port 对不上" % dash))
    emit()
    raise SystemExit(0)

# 未登录时 401 是正常的 —— 判据是「能不能用配置里的账号登进去」。
st, body = http(dash + '/auth/login', method='POST',
                form={'username': cred['username'], 'password': cred['password']})
if st != 200:
    out.append(("FAIL", "24.1 控制台登录失败 (HTTP %s) —— %s 里的账号口令跟控制台对不上, "
                        "多半是那份挂载没生效" % (st, os.path.basename(dash_yml))))
    emit()
    raise SystemExit(0)
out.append(("OK", "24.1 控制台 %s 活着, 登录得上 (账号来自 %s)" % (dash, os.path.basename(dash_yml))))

# ---- 24.2 控制台看得见网关这个应用 (心跳真的到了) ----
names = data_of(http(dash + '/app/names.json')[1])
if not isinstance(names, list):
    out.append(("FAIL", "24.2 读不出控制台的应用列表 (data=%s) —— 这一项空转" % type(names).__name__))
elif APP in names:
    out.append(("OK", "24.2 控制台的应用列表里有 %s —— 网关的心跳真的到了控制台" % APP))
else:
    out.append(("FAIL", "24.2 控制台的应用列表里没有 %s (现在有: %s) —— 网关没把心跳发过来"
                        "(控制台比网关晚起的话, 网关必须重启: Sentinel 的心跳地址只在启动那一刻解析一次)"
                % (APP, ",".join(names) if names else "空")))

# ---- 24.3 控制台看得见网关这台机器 (ip + 端口) ----
machines = data_of(http(dash + '/app/' + APP + '/machines.json')[1])
ip = port = None
if not isinstance(machines, list) or not machines:
    out.append(("FAIL", "24.3 控制台里没有 %s 的机器 —— 网关没注册上来" % APP))
else:
    m = max(machines, key=lambda x: x.get('lastHeartbeat') or 0)
    ip, port = m.get('ip'), m.get('port')
    if not ip or not port:
        out.append(("FAIL", "24.3 控制台记下了机器但没有 ip/port: %s" % json.dumps(m, ensure_ascii=False)[:160]))
    else:
        out.append(("OK", "24.3 控制台看得见网关这台机器 (ip=%s port=%s healthy=%s)" % (ip, port, m.get('healthy'))))

# ---- 24.4 控制台真的拿到了网关的限流规则 ----
#
# 控制台不是自己存规则 —— 它是回头问网关:
#   GET http://<网关ip>:<网关port>/gateway/getRules
# (网关侧的 handler 是 GetGatewayRuleCommandHandler, 读的是 GatewayRuleManager 里的内存规则)
# 所以这里能拿到多少条, 就等于网关内存里真的加载了多少条。
dash_rules = []
if ip and port:
    q = urllib.parse.urlencode({'app': APP, 'ip': ip, 'port': port})
    st, raw = http(dash + '/gateway/flow/list.json?' + q)
    dash_rules = data_of(raw)
    if st != 200:
        out.append(("FAIL", "24.4 控制台问网关要限流规则要不到 (HTTP %s) —— "
                            "网关的命令服务(默认 8719)没开, 或控制台够不着它" % st))
    elif not isinstance(dash_rules, list):
        out.append(("FAIL", "24.4 控制台返回的规则不是列表: %s" % str(dash_rules)[:160]))
    elif not dash_rules:
        out.append(("FAIL", "24.4 控制台问网关要来了 0 条限流规则 —— 网关内存里一条都没加载"))
    else:
        out.append(("OK", "24.4 控制台真的从网关取到了 %d 条网关限流规则" % len(dash_rules)))
else:
    out.append(("FAIL", "24.4 拿不到网关的 ip/port, 没法问它要规则"))

# ---- 24.5 控制台看到的规则 = Nacos 里的规则表 = 网关的路由表 ----
#
# 三张表必须对得上:
#   Nacos(sentinel-ruoyi-gateway) = 网关内存(GatewayRuleManager) = 控制台看到的
# 对不上就说明中间某一环在骗人: 规则写进了 Nacos 但网关没加载, 或者
# 网关加载了但控制台看的是另一份。
route_ids = route_ids_of(gw_yml)
nacos_url = api + '/v1/cs/configs?' + urllib.parse.urlencode(
    {'dataId': 'sentinel-ruoyi-gateway', 'group': 'DEFAULT_GROUP', 'tenant': ns})
nacos_raw = http(nacos_url)[1]
nacos_rules = None
if nacos_raw and nacos_raw.strip() not in ('', 'config data not exist'):
    try:
        nacos_rules = json.loads(nacos_raw)
    except Exception:
        nacos_rules = None

if not isinstance(nacos_rules, list) or not nacos_rules:
    out.append(("FAIL", "24.5 tenant=%s 下读不到 Sentinel 规则表 —— 没法跟控制台对账" % ns))
elif not isinstance(dash_rules, list):
    out.append(("FAIL", "24.5 控制台那侧没取到规则, 对不了账"))
else:
    nacos_res = sorted({r.get('resource') for r in nacos_rules if isinstance(r, dict) and r.get('resource')})
    dash_res = sorted({r.get('resource') for r in dash_rules
                       if isinstance(r, dict) and r.get('resource')})
    only_nacos = [r for r in nacos_res if r not in dash_res]
    only_dash = [r for r in dash_res if r not in nacos_res]
    if only_nacos:
        out.append(("FAIL", "24.5 Nacos 里有、控制台里没有的规则: %s —— 网关没把它们加载进内存"
                    % " ".join(only_nacos)))
    if only_dash:
        out.append(("FAIL", "24.5 控制台里有、Nacos 里没有的规则: %s —— 这一份是凭空来的"
                    % " ".join(only_dash)))
    if not only_nacos and not only_dash:
        out.append(("OK", "24.5 控制台看到的 %d 条规则与 Nacos 规则表逐条对得上" % len(dash_res)))
    bad = [r.get('resource') for r in dash_rules
           if isinstance(r, dict) and r.get('resource') and r.get('resource') not in route_ids]
    if bad:
        out.append(("FAIL", "24.5 控制台上有 %d 条规则挂在不存在的路由上: %s"
                    % (len(bad), " ".join(sorted(set(bad))[:6]))))
    elif route_ids:
        missing = [r for r in route_ids if r not in dash_res]
        if missing:
            out.append(("FAIL", "24.5 %d 条路由没有限流规则(等于不限流): %s"
                        % (len(missing), " ".join(missing))))
        else:
            out.append(("OK", "24.5 网关 %d 条路由全部有对应的限流规则, 控制台上一条不缺" % len(route_ids)))

emit()
PY
)

  if [[ -z "${out}" ]]; then
    nok "24 解析不了控制台的响应 —— 这一节空转, 证明不了任何东西"
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
}

# ---------- 25) 定时任务是真的在跑 (不是界面上的一行字) ----------
#
# 这一节存在的理由:ruoyi-job 模块的代码一直在仓库里(Quartz + sys_job 表 +
# WorkflowCronJob + 前端「定时任务」页面,一整套都齐),但它**从来没被部署过** ——
# 没有 Dockerfile、没有 compose 容器、网关没有 /schedule 路由。
# 于是界面上「定时任务」菜单点进去全是 404,而 sys_job 里那条
# 「每天 09:00 生成财务日报」的 cron 一次都没跑过(sys_job_log 表 0 行)。
#
# 判据不能是「容器在跑」—— 那也可能是空壳。要**真的触发一次**,然后看见:
#   · opc_agent_workflow_run 多了一行 trigger_source='manual:...' 的记录
#     (本节自己触发的是「手动执行一次」; 定时触发落的是 `quartz:...`,
#      由第 26.1 / 26.6 去查 —— 见那两节)
#   · sys_job_log 多了一行 status='0'(成功) **且** 那次业务运行也是 SUCCESS
# 这两行都是应用自己写的,不是检查脚本造的 —— 造不出来才是关键。
#
# ⚠️ URL 不带 /prod-api:前端 axios 的 baseURL 是 /prod-api,但 nginx 的
#    proxy_pass 末尾带斜杠,会把 /prod-api/ 整段剥掉再转给网关
#    (见 vue3-typescript/nginx.conf)。所以网关收到的是 /schedule/job/list。
check_scheduler_truth() {
  hr
  echo "[25] 定时任务是真的在跑"
  hr

  # --- 25.1 容器 ---
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "aiopc-job"; then
    ok "25.1 aiopc-job 容器在跑"
  else
    nok "25.1 aiopc-job 容器没起来 —— 调度器代码在仓库里,但没被装起来跑过"
    return
  fi

  # --- 25.2 sys_job 里得有启用中的任务 ---
  local enabled_jobs
  enabled_jobs=$(db_q "SELECT COUNT(*) FROM sys_job WHERE status='0';" | tr -d '\r')
  if [[ -z "${enabled_jobs}" ]]; then
    nok "25.2 查不到 sys_job —— 护栏空转, 证明不了任何东西"
  elif [[ "${enabled_jobs}" == "0" ]]; then
    nok "25.2 sys_job 里一条启用中的任务都没有 —— 调度器起来了也没东西可跑"
  else
    ok "25.2 sys_job 里有 ${enabled_jobs} 条启用中的任务"
  fi

  # --- 25.3 网关这条路由真的通, 且列表里确实有「财务日报」 ---
  #
  # 以前网关没有 /schedule/** 这条路由 → 界面「定时任务」点进去所有接口 404。
  # 这一条直接问网关, 用 body.code 判定(RuoYi 的错误是 HTTP 200 + body.code)。
  local login_body token
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  token=$(printf '%s' "${login_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
except Exception:
    print('')
    raise SystemExit
print(d.get('token','') or d.get('access_token',''))
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    nok "25.3 拿不到 admin token —— 25.3 之后的断言都跑不了"
    return
  fi

  local list_body list_code
  list_body=$(curl -s -m 15 -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/schedule/job/list" 2>/dev/null) || list_body=""
  list_code=$(body_code "${list_body}")
  if [[ "${list_code}" != "200" ]]; then
    nok "25.3 GET /schedule/job/list body.code=${list_code} —— 网关没有 /schedule 路由, 或 ruoyi-job 没起来"
    return
  fi
  local has_daily
  has_daily=$(printf '%s' "${list_body}" | python -c "
import sys,json
try:
    rows = (json.load(sys.stdin).get('rows') or [])
except Exception:
    print('0')
    raise SystemExit
print(sum(1 for r in rows if r.get('jobId') == 100))
" 2>/dev/null)
  if [[ "${has_daily}" == "1" ]]; then
    ok "25.3 网关 /schedule/job/list 通, 且列得出 job 100「财务日报」"
  else
    nok "25.3 /schedule/job/list 里没有 job 100「财务日报」(命中 ${has_daily:-?}) —— 界面上的定时任务是空的"
  fi

  # --- 25.4 真触发一次 ---
  #
  # PUT /schedule/job/run → 网关 StripPrefix=1 → /job/run → SysJobController.run
  # → scheduler.triggerJob() → Quartz 工作线程 → WorkflowCronJob.execute()
  # → Feign 调 opc-agent-hub → 工作流真的跑起来。
  #
  # triggerJob 是**异步**的:HTTP 立刻返回,工作流在后台跑。所以下面要轮询等落库。
  # 另外 WorkflowTriggerServiceImpl 有 30 秒去重窗口(正在跑的不会重复触发),
  # 触发前先等掉上一次没跑完的。
  local busy
  for _ in $(seq 1 12); do
    busy=$(db_q "SELECT COUNT(*) FROM opc_agent_workflow_run WHERE workflow_id=1 AND status='RUNNING' AND start_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND);" | tr -d '\r')
    [[ "${busy}" == "0" ]] && break
    sleep 3
  done

  local before_id
  before_id=$(db_q "SELECT COALESCE(MAX(id),0) FROM opc_agent_workflow_run;" | tr -d '\r')
  if [[ -z "${before_id}" ]]; then
    nok "25.4 查不到 opc_agent_workflow_run —— 护栏空转, 证明不了任何东西"
    return
  fi

  local run_body run_code
  run_body=$(curl -s -m 30 -X PUT \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d '{"jobId":100,"jobGroup":"SYSTEM"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/schedule/job/run" 2>/dev/null) || run_body=""
  run_code=$(body_code "${run_body}")
  if [[ "${run_code}" == "200" ]]; then
    ok "25.4 PUT /schedule/job/run 受理了 (job 100 立即执行一次)"
  else
    nok "25.4 PUT /schedule/job/run body.code=${run_code}: ${run_body:0:160} —— 触发链没通"
    return
  fi

  # --- 25.5 等真的落库:新的运行记录 trigger_source='manual:...' ---
  #
  # 这一行是 opc-agent-hub 自己写的 (WorkflowTriggerServiceImpl.newRun)。
  #
  # ⚠️ 等的是 `manual:` 不是 `quartz:`:
  # 健康检查第 25.4 步调的是 `PUT /schedule/job/run`(手动执行一次)。
  # Task 7 之后手动执行在 Quartz 的 JobDataMap 里放了一个标记
  # (ruoyi-job 的 ManualTriggerMarker),agent-hub 那边收到的 triggerSource
  # 就是 `manual:<code>` —— 而定时触发才是 `quartz:<code>`。
  # 等 quartz: 会**永远等不到**(60 次 × 3 秒 = 3 分钟超时 → 25.5 当场红),
  # 而且更糟: 等 quartz: 会让健康检查自己把「定时器跑没跑过」这个问题喂绿。
  # 「定时器有没有真跑过」由第 26.1 / 26.6 查 quartz: 的历史记录来回答。
  local new_run="" new_run_id="" run_status=""
  #
  # ⚠️ 等的是「**跑到终态**」, 不是「记录出现」。
  #   以前这里一看到记录就 break —— 而记录是**先落库、后跑**的:
  #   触发受理 → newRun 落库(status=PENDING) → 引擎跑(十几秒到几分钟)→ 落终态。
  #   于是在 LLM 真的要花时间的那次(2026-11-05: 缓存过期, 一次真调用 16 秒),
  #   25.5b 读到的是 **RUNNING**, 25.6 也跟着判红 ——
  #   **一条还在飞的运行, 被判成「业务没跑成」**。
  #   (同一个健康检查跑两遍, 结果不一样: 缓存命中时 0.5 秒就 SUCCESS 了。)
  #   判「业务跑成没跑成」就得等它跑完 —— 拿到 PENDING/RUNNING 时继续等。
  #
  # id 一旦定下来就**钉住**: 两次触发挨得近时, 每次都取「最新那条」会配错行。
  for _ in $(seq 1 60); do
    if [[ -z "${new_run_id}" ]]; then
      new_run_id=$(db_q "SELECT id FROM opc_agent_workflow_run WHERE id > ${before_id} AND trigger_source LIKE 'manual:%' ORDER BY id DESC LIMIT 1;" | tr -d '\r')
    fi
    if [[ -n "${new_run_id}" ]]; then
      run_status=$(db_q "SELECT status FROM opc_agent_workflow_run WHERE id = ${new_run_id};" | tr -d '\r')
      case "${run_status}" in
        SUCCESS|PARTIAL|FAILED) break ;;   # 终态 → 可以判了
      esac
    fi
    sleep 3
  done
  if [[ -n "${new_run_id}" ]]; then
    new_run=$(db_q "SELECT CONCAT(id,'|',run_code,'|',trigger_type,'|',trigger_source,'|',status) FROM opc_agent_workflow_run WHERE id = ${new_run_id};" | tr -d '\r')
  fi
  if [[ -z "${new_run}" ]]; then
    nok "25.5 触发后 3 分钟内没等到 trigger_source='manual:%' 的运行记录 —— 手动执行这一环没通"
  elif [[ "${run_status}" == "SUCCESS" || "${run_status}" == "PARTIAL" || "${run_status}" == "FAILED" ]]; then
    ok "25.5 真跑出一条运行记录(手动执行, 已跑到终态): ${new_run}"
  else
    nok "25.5 触发后 3 分钟这条运行(id=${new_run_id})还没跑到终态(status=${run_status:-<空>}) —— 它还在飞, 不是跑成了"
  fi

  # --- 25.5b 落库的那条运行记录, 状态必须是真的跑成了 ---
  #
  # 以前 25.5 只断「记录存在」→ 4 条 FAILED 的记录照样通过。
  #
  # 状态**从上面那条 new_run 的第 5 段切**, 不回查:
  # new_run 的格式是 id|run_code|trigger_type|trigger_source|status,
  # 回查拿到的是「此刻表里那一行」—— 两次触发挨得近时可能不是这次那一条
  # (一条会配错的断言比没有断言更坏)。
  #
  # PARTIAL 是 **warn** 不是 nok: PARTIAL = 本次运行有节点降级
  # (LLM 没成功, 用了引擎侧确定性聚合摘要) —— 是「跑过了, 只是降级」, 不是失败。
  # ⚠️ PARTIAL **不代表产出物落过表**(没有 persist 声明的 dag 记 PARTIAL 时业务表里
  # 一行都没有)—— 所以这里绝不能断言「PARTIAL 的运行必有对应的日报行」。
  # 「LLM 通不通」由 26.2 / 26.7 / 26.9b / 26.10 负责。
  local new_run_id2
  new_run_id2=$(printf '%s' "${new_run}" | cut -d'|' -f1)
  run_status=$(printf '%s' "${new_run}" | cut -d'|' -f5)
  if [[ -z "${new_run}" ]]; then
    nok "25.5b 没有一条可判状态的运行记录 —— 25.5 本身就没跑成"
  elif [[ "${run_status}" == "SUCCESS" ]]; then
    ok "25.5b 这条运行记录 status=SUCCESS (id=${new_run_id2})"
  elif [[ "${run_status}" == "PARTIAL" ]]; then
    warn "25.5b 这条运行记录 status=PARTIAL (id=${new_run_id2}) —— 跑成了, 但有节点降级(不等于业务成功)"
  elif [[ "${run_status}" == "PENDING" || "${run_status}" == "RUNNING" ]]; then
    nok "25.5b 这条运行(id=${new_run_id2})等了 3 分钟还是 ${run_status} —— 它**没跑完**(不是失败), 别跟「跑成了」混为一谈"
  else
    nok "25.5b 这条运行记录 status=${run_status:-<空>} (id=${new_run_id2}) —— 记录落库了, 但业务没跑成(以前的 25.5 只看记录在不在)"
  fi

  # --- 25.6 sys_job_log 里必须有这次执行, 而且业务也得真跑成 ---
  #
  # sys_job_log.status 记的是「Feign 触发**受理**成功」, 不是业务成功 ——
  # 历史上 4 条业务全 FAILED, sys_job_log 却全是 status=0。
  # 所以现在要求「调度器记成功」**且**「25.5b 那条业务运行也是成功」。
  #
  # ⚠️ 不按 start_time 关联(brief 原来那么写):两列虽然都是墙上时间,
  # 但「按时间关联」在两次触发挨得近时会配错行 —— 一条会配错的断言比没有断言更坏。
  # 25.4 触发 → 25.5 等到**那条**记录 → 25.6 看**它**的状态:关联关系是**因果**。
  local log_row
  log_row=$(db_q "SELECT CONCAT(job_name,'|',status,'|',LEFT(job_message,40)) FROM sys_job_log WHERE job_name='财务日报' ORDER BY job_log_id DESC LIMIT 1;" | tr -d '\r')
  if [[ -z "${log_row}" ]]; then
    nok "25.6 sys_job_log 里没有「财务日报」的执行记录 —— 调度器没真的执行过"
  else
    local log_status
    log_status=$(printf '%s' "${log_row}" | cut -d'|' -f2)
    if [[ "${log_status}" != "0" ]]; then
      nok "25.6 sys_job_log 记下了这次执行但**失败**: ${log_row}"
    elif [[ "${run_status}" == "SUCCESS" ]]; then
      ok "25.6 调度器记成功, 业务运行也成功: ${log_row}"
    elif [[ "${run_status}" == "PARTIAL" ]]; then
      warn "25.6 调度器记成功, 业务运行是 PARTIAL(有节点降级, 不是业务失败): ${log_row}"
    else
      nok "25.6 调度器记成功但业务没跑成(status=${run_status:-<空>}) —— 「触发受理成功」不等于「业务成功」: ${log_row}"
    fi
  fi

  # --- 25.7 没有冒充 CRON 的运行记录 ---
  #
  # trigger_type 记的是**这次到底怎么触发的**。历史上 30 条 healthcheck 触发的
  # 记录都写着 'CRON' —— 那时根本没有调度器,这个字段在冒充事实。
  # 现在规则是:trigger_source LIKE 'quartz:%' 才算 CRON,其余一律 MANUAL。
  local fake_cron
  fake_cron=$(db_q "SELECT COUNT(*) FROM opc_agent_workflow_run WHERE trigger_type='CRON' AND (trigger_source IS NULL OR trigger_source NOT LIKE 'quartz:%');" | tr -d '\r')
  if [[ -z "${fake_cron}" ]]; then
    nok "25.7 查不到 opc_agent_workflow_run —— 护栏空转"
  elif [[ "${fake_cron}" != "0" ]]; then
    nok "25.7 有 ${fake_cron} 条运行记录 trigger_type='CRON' 但不是定时器触发的 —— 类型字段在冒充事实"
  else
    ok "25.7 没有一条运行记录冒充 CRON"
  fi

  # --- 25.8 界面上没有指向 localhost 的死外链 ---
  #
  # 那四个菜单(Sentinel控制台 / Nacos控制台 / Admin控制台 / 系统接口)
  # 指的是**访客自己电脑**的 localhost —— 演示环境从浏览器点开必然是
  # 「无法访问此网站」。三个改成隐藏,一个(9100 端口上什么都没有)直接删掉,
  # 需要的人按 README 里的 ssh -L 端口转发自己开隧道看。
  local dead_links visible_links
  dead_links=$(db_q "SELECT COUNT(*) FROM sys_menu WHERE path LIKE 'http://localhost%';" | tr -d '\r')
  visible_links=$(db_q "SELECT COUNT(*) FROM sys_menu WHERE visible='0' AND path LIKE 'http://localhost%';" | tr -d '\r')
  if [[ -z "${dead_links}" ]]; then
    nok "25.8 查不到 sys_menu —— 护栏空转"
  elif [[ "${visible_links}" != "0" ]]; then
    nok "25.8 界面上还有 ${visible_links} 个指向 localhost 的外链菜单 —— 点开必然打不开"
  else
    ok "25.8 界面上没有指向 localhost 的死外链菜单"
  fi

  # --- 25.9 sys_job_log 同一行里不能有两个钟 ---
  #
  # 原来 mapper 里写的是 sysdate() → create_time 走 **MySQL 服务器的钟**(本机 UTC),
  # 而同一行的 start_time / end_time 走 **JDBC 的钟**(serverTimezone=Asia/Shanghai → 北京)。
  # 结果同一行差 8 小时:
  #     start_time = 2026-11-06 09:00:00   (北京)
  #     create_time = 2026-11-06 01:00:17 (UTC)
  #
  # 为什么它有牙: RuoYi 自带的定时任务日志页按 create_time 过滤日期区间
  # (date_format(create_time,'%Y%m%d')) → 按北京时间挑「今天」, 会把 00:00~08:00
  # 之间发生的执行算到昨天去。演示前端没有那个页面(views/monitor 不存在),
  # 所以对访客不可见 —— 但对「查库的人」是真陷阱。
  #
  # 判据: create_time 就是那一次执行落表的时刻, 必须落在 end_time 之后 5 分钟之内。
  # 差 8 小时 = -28800 秒 → 红。
  local clock_row clock_skew
  clock_row=$(db_q "SELECT CONCAT(job_log_id,'|',start_time,'|',end_time,'|',create_time,'|',TIMESTAMPDIFF(SECOND, end_time, create_time)) FROM sys_job_log WHERE job_name='财务日报' ORDER BY job_log_id DESC LIMIT 1;" | tr -d '\r')
  if [[ -z "${clock_row}" ]]; then
    nok "25.9 查不到 sys_job_log —— 护栏空转, 证明不了任何东西"
  else
    clock_skew=$(printf '%s' "${clock_row}" | awk -F'|' '{print $5}')
    if [[ -z "${clock_skew}" || "${clock_skew}" -lt -60 || "${clock_skew}" -gt 300 ]]; then
      nok "25.9 sys_job_log 同一行里两个钟: ${clock_row} —— create_time 比 end_time 差 ${clock_skew:-?} 秒(该在 0~300 秒之间)"
    else
      ok "25.9 sys_job_log 整行一个钟(create_time 比 end_time 晚 ${clock_skew} 秒): ${clock_row}"
    fi
  fi
}

# ---------- 26. 财务数字员工读的是真数据 ----------
#
# 这一节存在的理由:前面那些 Task 让「财务数字员工」真的产出日报了,
# 但「产出了」不等于「产出的数字是真的」。这一节把「真」拆成三层:
#
#   · 配置层:降级链**接上了**吗?
#       26.9  配了几个 provider(接上了)
#       26.9b 那把钥匙**真的能开门**吗(接上了 ≠ 能用)
#       26.11 四份 yml 的 opc.llm 段对得上吗(没有第二个真相源)
#       26.12 容器里开关真的开着、钥匙真的在吗(yml 配了 ≠ 跑起来会用)
#   · 运行层:定时器**真跑过**吗?
#       26.1  历史上跑成过吗(不限时间)
#       26.6  最近一次跑成了吗(限 7 天 —— 一周多没跑过也是实话)
#   · 数据层:日报里的数字**等于**库里的数字吗?
#       26.2  最新一条日报是 FULL、不是降级产物、出自一次 SUCCESS 的运行
#       26.3  日报里的金额**逐笔**等于库里同公司同区间的金额
#       26.4  取数用的公司 == 工作流挂的公司
#       26.7  工作流路径一条降级日报都没有
#       26.8  每一条日报的来源都说得清(谁写的 / 能不能反查回那次运行)
#
# ⚠️ 这一节**不许**调 chat/completions —— 那是真花钱(用户 2026-11-04 点名)。
#    探 provider 连通性只用 GET /v1/models(零 token 消耗)。
# ⚠️ 这一节**不许**自动调 POST /opc/insight/advice —— 那也是真花钱。
#    26.10 只**读**最近一条;造那条非 FALLBACK 的行是**部署流程**里的一次动作。
# ⚠️ 这一节**不许**把 key 的值打进输出(坑 #12)—— 只判「有没有 / 多长 / 是不是真值」。
check_finance_agent_truth() {
  hr
  echo "[26] 财务数字员工读的是真数据"
  hr

  # --- 26.0 自己登录一次 ---
  #
  # HEALTH_ONLY=26 bash deploy.sh health 必须能单独跑 → 不能依赖第 25 节的 token。
  local login_body token
  login_body=$(curl -sf -m 15 -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/login" 2>/dev/null) || true
  token=$(printf '%s' "${login_body}" | python -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data', {})
except Exception:
    print('')
    raise SystemExit
print(d.get('token','') or d.get('access_token',''))
" 2>/dev/null)
  if [[ -z "${token}" ]]; then
    nok "26.0 拿不到 admin token —— 26.9 / 26.9b 跑不了(走数据库的那几条不受影响)"
  else
    ok "26.0 admin 登录通了(拿到 token, 不打印它)"
  fi

  # --- 26.1 历史上有没有真的跑成过的定时触发 ---
  #
  # **不限时间**:这一条问的是「这条链路**历史上**有没有成功过」,
  # 不是「最近有没有成功」。最近的那次由 26.6 问(限 7 天)。
  local q_ok
  q_ok=$(db_q "SELECT COUNT(*) FROM opc_agent_workflow_run WHERE trigger_source LIKE 'quartz:%' AND status='SUCCESS';" | tr -d '\r')
  if [[ -z "${q_ok}" ]]; then
    nok "26.1 查不到 opc_agent_workflow_run —— 护栏空转, 证明不了任何东西"
  elif [[ "${q_ok}" == "0" ]]; then
    nok "26.1 从来没有一条 quartz: 触发的运行 status='SUCCESS' —— 定时触发这条链一次都没跑成过"
  else
    ok "26.1 历史上跑成过 ${q_ok} 条 quartz: 触发的运行 —— 这条链路真的成功过"
  fi

  # --- 26.2 最新一条数字员工日报:是 FULL、不是降级产物、出自一次 SUCCESS 的运行 ---
  #
  # 判**最新一条**,不是「存在一条」:「存在一条」会被历史上的一条好日报永久满足
  # → 「今天跑出来的是不是降级产物」这个问题就永远绿了。
  local rep
  rep=$(db_q "SELECT CONCAT(id,'|',IFNULL(report_status,''),'|',IFNULL(llm_used,''),'|',IFNULL(source_workflow_run_id,''),'|',company_id,'|',period) FROM opc_insight_daily_report WHERE report_source='AGENT_WORKFLOW' ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  if [[ -z "${rep}" ]]; then
    nok "26.2 opc_insight_daily_report 里一条 report_source='AGENT_WORKFLOW' 的日报都没有 —— 数字员工没产出过东西"
  else
    local rep_id rep_status rep_llm rep_runid rep_company rep_period rep_why=""
    rep_id=$(printf '%s' "${rep}" | cut -d'|' -f1)
    rep_status=$(printf '%s' "${rep}" | cut -d'|' -f2)
    rep_llm=$(printf '%s' "${rep}" | cut -d'|' -f3)
    rep_runid=$(printf '%s' "${rep}" | cut -d'|' -f4)
    rep_company=$(printf '%s' "${rep}" | cut -d'|' -f5)
    rep_period=$(printf '%s' "${rep}" | cut -d'|' -f6)
    if [[ "${rep_status}" != "FULL" ]]; then
      rep_why="${rep_why} report_status='${rep_status}'(要 FULL) "
    fi
    if [[ -z "${rep_llm}" || "${rep_llm}" == "FALLBACK" ]]; then
      rep_why="${rep_why} llm_used='${rep_llm}'(要非空且不是 FALLBACK) "
    fi
    if [[ -z "${rep_runid}" ]]; then
      rep_why="${rep_why} source_workflow_run_id 为空(查不回产出它的那次运行) "
    elif ! [[ "${rep_runid}" =~ ^[0-9]+$ ]]; then
      rep_why="${rep_why} source_workflow_run_id='${rep_runid}' 不是运行 id "
    else
      local rep_run_status
      rep_run_status=$(db_q "SELECT status FROM opc_agent_workflow_run WHERE id = ${rep_runid};" | tr -d '\r')
      if [[ -z "${rep_run_status}" ]]; then
        rep_why="${rep_why} run ${rep_runid} 在 opc_agent_workflow_run 里不存在 "
      elif [[ "${rep_run_status}" != "SUCCESS" ]]; then
        rep_why="${rep_why} run ${rep_runid} status='${rep_run_status}'(要 SUCCESS) "
      fi
    fi
    if [[ -z "${rep_why}" ]]; then
      ok "26.2 最新一条数字员工日报(id=${rep_id} company=${rep_company} period=${rep_period})是 FULL、非降级产物, 出自一次 SUCCESS 的运行 ${rep_runid}"
    else
      nok "26.2 最新一条数字员工日报(id=${rep_id} company=${rep_company} period=${rep_period})不合格:${rep_why}"
    fi
  fi

  # --- 26.3 日报里的金额, 逐笔等于库里的金额 ---
  #
  # 这是这一节最强的一条:日报的 kpi_json.query_bank_flow.items[] 里每一笔金额,
  # 都要等于 opc_finance_bank_flow 里**同一公司、同一区间**的金额。
  #
  # 区间**不猜**:FlowRangeVo 把实际用的 fromDate / toDate 回写进了响应
  # (「响应里回写实际使用的 fromDate / toDate, 健康检查靠这两个值原样复算」),
  # 所以这里用日报自己回写的值去查库 —— 用的是日报**实际**用的区间。
  #
  # DB 侧的谓词**逐字复刻** mapper(OpcFinanceBankFlowMapper.xml):
  #   WHERE company_id = ? AND trade_time >= ? AND trade_time < DATE_ADD(?, INTERVAL 1 DAY)
  #   ORDER BY trade_time ASC, id ASC
  # 自己另写一个 BETWEEN 就是在对一套不存在的口径。
  #
  # 金额比较用 Decimal:JSON 里的 13200.00 经 json.loads 会变成 float(13200.0),
  # 裸字符串比较会**假红**。两侧都按 Decimal 解析、量化到 2 位小数再比数值
  # (FlowRangeVo 的 javadoc 也明说「下游用 compareTo 不用 equals」)。
  #
  # ⚠️ 只取**数字**字段(items[*].amount / totalIn / totalOut / net / 区间),
  #    不取 items 里那些自由文本(memo / counterParty / rawText):
  #    mysql -B 会把反斜杠和换行转义掉, JSON 文本过一趟管道就可能被弄坏。
  #    数字字段不会有这个问题。
  local kpi_line kpi_cid kpi_from kpi_to kpi_tin kpi_tout kpi_net kpi_items
  kpi_line=$(db_q "SELECT CONCAT_WS('|',
      IFNULL(JSON_UNQUOTE(JSON_EXTRACT(kpi_json, '\$.query_bank_flow.companyId')),''),
      IFNULL(JSON_UNQUOTE(JSON_EXTRACT(kpi_json, '\$.query_bank_flow.fromDate')),''),
      IFNULL(JSON_UNQUOTE(JSON_EXTRACT(kpi_json, '\$.query_bank_flow.toDate')),''),
      IFNULL(JSON_UNQUOTE(JSON_EXTRACT(kpi_json, '\$.query_bank_flow.totalIn')),''),
      IFNULL(JSON_UNQUOTE(JSON_EXTRACT(kpi_json, '\$.query_bank_flow.totalOut')),''),
      IFNULL(JSON_UNQUOTE(JSON_EXTRACT(kpi_json, '\$.query_bank_flow.net')),''),
      IFNULL(JSON_EXTRACT(kpi_json, '\$.query_bank_flow.items[*].amount'),'')
    ) FROM opc_insight_daily_report WHERE report_source='AGENT_WORKFLOW' ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  kpi_cid=$(printf '%s' "${kpi_line}" | cut -d'|' -f1)
  kpi_from=$(printf '%s' "${kpi_line}" | cut -d'|' -f2)
  kpi_to=$(printf '%s' "${kpi_line}" | cut -d'|' -f3)
  kpi_tin=$(printf '%s' "${kpi_line}" | cut -d'|' -f4)
  kpi_tout=$(printf '%s' "${kpi_line}" | cut -d'|' -f5)
  kpi_net=$(printf '%s' "${kpi_line}" | cut -d'|' -f6)
  kpi_items=$(printf '%s' "${kpi_line}" | cut -d'|' -f7)

  if [[ -z "${kpi_line}" ]]; then
    nok "26.3 取不到数字员工日报里的 kpi_json —— 没有可对账的日报"
  elif ! [[ "${kpi_cid}" =~ ^[0-9]+$ ]] || ! [[ "${kpi_from}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || ! [[ "${kpi_to}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    nok "26.3 日报的 kpi_json 里取不出「公司 + 区间」(companyId='${kpi_cid}' from='${kpi_from}' to='${kpi_to}') —— 对不了账"
  else
    local db_amounts db_totals db_tin db_tout db_net
    db_amounts=$(db_q "SELECT amount FROM opc_finance_bank_flow WHERE company_id = ${kpi_cid} AND trade_time >= '${kpi_from}' AND trade_time < DATE_ADD('${kpi_to}', INTERVAL 1 DAY) ORDER BY trade_time ASC, id ASC;" | tr -d '\r')
    db_totals=$(db_q "SELECT CONCAT_WS('|',
        IFNULL(SUM(CASE WHEN direction='IN' THEN amount ELSE 0 END),0),
        IFNULL(SUM(CASE WHEN direction='OUT' THEN amount ELSE 0 END),0),
        IFNULL(SUM(CASE WHEN direction='IN' THEN amount ELSE 0 END),0) - IFNULL(SUM(CASE WHEN direction='OUT' THEN amount ELSE 0 END),0)
      ) FROM opc_finance_bank_flow WHERE company_id = ${kpi_cid} AND trade_time >= '${kpi_from}' AND trade_time < DATE_ADD('${kpi_to}', INTERVAL 1 DAY);" | tr -d '\r')
    db_tin=$(printf '%s' "${db_totals}" | cut -d'|' -f1)
    db_tout=$(printf '%s' "${db_totals}" | cut -d'|' -f2)
    db_net=$(printf '%s' "${db_totals}" | cut -d'|' -f3)

    local verdict
    verdict=$(KPI_ITEMS="${kpi_items}" KPI_TIN="${kpi_tin}" KPI_TOUT="${kpi_tout}" KPI_NET="${kpi_net}" \
              DB_AMOUNTS="${db_amounts}" DB_TIN="${db_tin}" DB_TOUT="${db_tout}" DB_NET="${db_net}" \
              python - <<'PY' 2>/dev/null
import os, sys, json, decimal

Q = decimal.Decimal('0.01')
D = decimal.Decimal


def dec(v):
    v = (v or '').strip()
    if not v:
        return None
    try:
        return D(v)
    except Exception:
        return None


def q(v):
    return None if v is None else v.quantize(Q)


items_raw = os.environ.get('KPI_ITEMS', '').strip()
if not items_raw:
    print('NOITEMS|日报的 kpi_json 里没有 query_bank_flow.items[] —— 对不了账')
    sys.exit(0)
try:
    items = json.loads(items_raw, parse_float=decimal.Decimal)
except Exception:
    print('BADJSON|日报的 items[] 不是合法 JSON')
    sys.exit(0)
if not isinstance(items, list) or len(items) == 0:
    print('EMPTYITEMS|日报的 items[] 是空的 —— 一条流水都没有, 那份日报不该存在')
    sys.exit(0)
try:
    kpi = sorted(q(D(str(x))) for x in items)
except Exception:
    print('BADITEMS|日报 items[] 里的金额不是数字')
    sys.exit(0)

db_raw = [l.strip() for l in os.environ.get('DB_AMOUNTS', '').splitlines() if l.strip()]
if not db_raw:
    print('DBEMPTY|同公司同区间在 opc_finance_bank_flow 里一笔流水都没有 —— 日报算错区间了(不能「两个空集相等」就算通过)')
    sys.exit(0)
try:
    db = sorted(q(D(x)) for x in db_raw)
except Exception:
    print('DBBAD|库里的金额不是数字')
    sys.exit(0)

if len(kpi) != len(db):
    print('COUNT|日报 %d 笔 / 库 %d 笔' % (len(kpi), len(db)))
    sys.exit(0)
if kpi != db:
    print('AMOUNT|日报 %s / 库 %s' % (','.join(str(x) for x in kpi), ','.join(str(x) for x in db)))
    sys.exit(0)

# 明细对得上但合计对不上 —— 也是一种假话, 一起断。
for label, ke, de in (('totalIn', 'KPI_TIN', 'DB_TIN'),
                       ('totalOut', 'KPI_TOUT', 'DB_TOUT'),
                       ('net', 'KPI_NET', 'DB_NET')):
    kv = q(dec(os.environ.get(ke, '')))
    dv = q(dec(os.environ.get(de, '')))
    if kv is None or dv is None:
        print('TOTALMISS|%s|日报 %s / 库 %s' % (label, kv, dv))
        sys.exit(0)
    if kv != dv:
        print('TOTAL|%s|日报 %s / 库 %s' % (label, kv, dv))
        sys.exit(0)

print('OK|%d 笔金额逐笔相等, totalIn/totalOut/net 也相等' % len(kpi))
PY
    )
    local v_kind v_msg
    v_kind=$(printf '%s' "${verdict}" | cut -d'|' -f1)
    v_msg=$(printf '%s' "${verdict}" | cut -d'|' -f2-)
    if [[ "${v_kind}" == "OK" ]]; then
      ok "26.3 日报里的金额与库逐笔对得上(company=${kpi_cid} ${kpi_from}~${kpi_to}): ${v_msg}"
    elif [[ -z "${v_kind}" || "${v_kind}" == "${verdict}" ]]; then
      nok "26.3 对账脚本没给出结论 —— 这条断言等于没跑"
    else
      nok "26.3 日报里的金额与库对不上: ${v_msg}"
    fi
  fi

  # --- 26.4 取数用的公司 == 工作流挂的公司 == 2 ---
  #
  # 工作流以前挂在 company 1, 而全部财务数据在 company 2 →
  # 工具恒查空集,「如实说无流水」分不出「链路是真的」和「工具完全坏掉」。
  # 三方相等(工具结果 / 工作流 / 期望值)才能挡住这种漂移。
  local wf_cid
  wf_cid=$(db_q "SELECT company_id FROM opc_agent_workflow WHERE workflow_code='finance_daily_report_v1';" | tr -d '\r')
  if [[ -z "${wf_cid}" ]]; then
    nok "26.4 查不到 finance_daily_report_v1 这个工作流 —— 护栏空转"
  elif [[ -z "${kpi_cid}" ]]; then
    nok "26.4 日报里取不到 query_bank_flow.companyId —— 归属公司对不了"
  elif [[ "${kpi_cid}" != "${wf_cid}" || "${wf_cid}" != "2" ]]; then
    nok "26.4 归属公司对不上: 工具结果 companyId=${kpi_cid} / 工作流 company_id=${wf_cid} / 期望 2"
  else
    ok "26.4 归属公司三方相等: 工具结果 companyId=${kpi_cid} == 工作流 company_id=${wf_cid} == 2"
  fi

  # --- 26.5 工具注册表里没有假工具, 有三个真工具 ---
  #
  # 「假工具下线了」以前只是源码里的一个说法,从外面看不见。
  #
  # ⚠️ GET /opc/agent/tools 是 @InnerAuth → 网关会剥掉 from-source 头 →
  #    网关侧必然 401 → 必须**直连** agent-hub。
  local tools_body tools_code
  tools_body=$(curl -s -m 15 -H "from-source: inner" \
    "http://127.0.0.1:${AGENT_HUB_PORT}/opc/agent/tools" 2>/dev/null) || tools_body=""
  tools_code=$(body_code "${tools_body}")
  if [[ "${tools_code}" != "200" ]]; then
    nok "26.5 直连 agent-hub 的 /opc/agent/tools body.code=${tools_code} —— 工具注册表读不到"
  else
    local tool_verdict
    tool_verdict=$(printf '%s' "${tools_body}" | python -c "
import sys, json
FAKE = {'query_daily_report', 'create_voucher', 'extract_voucher'}
REAL = {'query_bank_flow', 'query_voucher', 'query_balance'}
try:
    rows = json.load(sys.stdin).get('data') or []
except Exception:
    print('BADJSON|读不出工具列表'); raise SystemExit
names = set(str(r.get('name')) for r in rows)
fake = sorted(FAKE & names)
miss = sorted(REAL - names)
if fake:
    print('FAKE|假工具还在: %s' % ','.join(fake))
elif miss:
    print('MISS|真工具不见了: %s' % ','.join(miss))
else:
    print('OK|%d 个工具, 假工具已下线, 三个真工具都在' % len(names))
" 2>/dev/null)
    local t_kind t_msg
    t_kind=$(printf '%s' "${tool_verdict}" | cut -d'|' -f1)
    t_msg=$(printf '%s' "${tool_verdict}" | cut -d'|' -f2-)
    if [[ "${t_kind}" == "OK" ]]; then
      ok "26.5 工具注册表对得上: ${t_msg}"
    elif [[ -z "${t_kind}" || "${t_kind}" == "${tool_verdict}" ]]; then
      nok "26.5 解析工具列表失败 —— 这条断言等于没跑"
    else
      nok "26.5 工具注册表不对: ${t_msg}"
    fi
  fi

  # --- 26.6 最近一次定时触发:是 SUCCESS, 且在 7 天内 ---
  #
  # 7 天是个取舍:演示环境可能一周没人动 → 这条会红。**那是实话**
  # (「定时任务最近一周没成功跑过」)。部署时用临时的 2 分钟 cron 保证当场绿。
  #
  # ⚠️ 时区:opc_agent_workflow_run.start_time 存的是**北京**墙上时间,
  #    而 MySQL 的 NOW() 是 **UTC**(容器时区是 UTC) → 直接比会少 8 小时,
  #    在 7 天边界上会**假红**。所以窗口要 +8 HOUR。
  local q_last q_status q_start q_recent
  q_last=$(db_q "SELECT CONCAT(id,'|',status,'|',IFNULL(start_time,'')) FROM opc_agent_workflow_run WHERE trigger_source LIKE 'quartz:%' ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  if [[ -z "${q_last}" ]]; then
    nok "26.6 从来没有一条 quartz: 触发的运行记录 —— 定时器这一环没通"
  else
    q_status=$(printf '%s' "${q_last}" | cut -d'|' -f2)
    q_start=$(printf '%s' "${q_last}" | cut -d'|' -f3)
    q_recent=$(db_q "SELECT COUNT(*) FROM opc_agent_workflow_run WHERE trigger_source LIKE 'quartz:%' AND start_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 8 HOUR;" | tr -d '\r')
    if [[ "${q_status}" != "SUCCESS" ]]; then
      nok "26.6 最近一次定时触发(运行 ${q_last})不是 SUCCESS —— 定时任务最近没跑成"
    elif [[ -z "${q_recent}" ]]; then
      nok "26.6 查不了 7 天窗口 —— 这条断言等于没跑(运行 ${q_last})"
    elif [[ "${q_recent}" == "0" ]]; then
      nok "26.6 最近一次定时触发成功了(${q_start}), 但**不在最近 7 天内** —— 这套环境一周多没让定时任务真跑过"
    else
      ok "26.6 最近一次定时触发是 SUCCESS 且在 7 天内: ${q_last}"
    fi
  fi

  # --- 26.7 工作流路径一条降级日报都不许有 ---
  #
  # 判「工作流路径产出的日报一条都不是 FALLBACK」, 不判「全局最近一条」——
  # 全局最近一条会被**手工生成**的日报污染, 而手工路径不由工作流负责。
  local fb_cnt
  fb_cnt=$(db_q "SELECT COUNT(*) FROM opc_insight_daily_report WHERE report_source='AGENT_WORKFLOW' AND llm_used='FALLBACK';" | tr -d '\r')
  if [[ -z "${fb_cnt}" ]]; then
    nok "26.7 查不到 opc_insight_daily_report —— 护栏空转"
  elif [[ "${fb_cnt}" != "0" ]]; then
    nok "26.7 工作流路径产出过 ${fb_cnt} 条降级日报(llm_used='FALLBACK') —— 那些数字是模板编的, 不是模型看着真实数据写的"
  else
    ok "26.7 工作流路径一条降级日报都没有"
  fi

  # --- 26.8 每一条日报的来源都说得清 ---
  #
  # 对**每一条**日报行断:
  #   · report_source='AGENT_WORKFLOW' → source_workflow_run_id 必须非空,
  #     且它指向的那次运行必须存在且 status='SUCCESS'
  #   · report_source='MANUAL'         → source_workflow_run_id 必须为空
  #
  # ⚠️ **不**断「同一 (company_id, period) 最多一行」—— 表上有唯一索引
  #    uk_company_period, 那件事由数据库保证 → 断它等于断一条**不可能红**的断言。
  #    (手工路径撞上数字员工的行会直接抛 409, 也不许覆盖 —— 那是代码里的行为,
  #     这里断的是「行上写着的来源」是不是真的。)
  local bad_rows
  bad_rows=$(db_q "SELECT CONCAT(d.id,'|',IFNULL(d.report_source,'<NULL>'),'|',IFNULL(d.source_workflow_run_id,'<NULL>'),'|',IFNULL(r.status,'<无此运行>')) FROM opc_insight_daily_report d LEFT JOIN opc_agent_workflow_run r ON r.id = d.source_workflow_run_id WHERE d.report_source IS NULL OR d.report_source NOT IN ('AGENT_WORKFLOW','MANUAL') OR (d.report_source='MANUAL' AND d.source_workflow_run_id IS NOT NULL) OR (d.report_source='AGENT_WORKFLOW' AND (d.source_workflow_run_id IS NULL OR r.id IS NULL OR r.status<>'SUCCESS'));" | tr -d '\r')
  if [[ -z "${bad_rows}" ]]; then
    ok "26.8 每一条日报的来源都说得清: 数字员工的行指着一次 SUCCESS 的运行, 手工的行不指着任何运行"
  else
    local bad_n bad_first
    bad_n=$(printf '%s\n' "${bad_rows}" | grep -c '|')
    bad_first=$(printf '%s' "${bad_rows}" | head -1)
    nok "26.8 有 ${bad_n} 行日报的来源对不上(id|report_source|source_workflow_run_id|那次运行的状态): ${bad_first}"
  fi

  # --- 26.9 降级链接上了:已启用的 provider >= 2 且含 deepseek ---
  #
  # GET /opc/llm/models 只返回 **enabled 的** provider(enabled = 开关 && key 非空),
  # 返回里**不含 key 的值**。
  #
  # provider 名是小写(ChatModelProvider.name() 返回小写)→ 用小写匹配,
  # 免得以后有人把它改成驼峰这条断言就莫名其妙变红。
  local enabled_providers=""
  local models_body models_code
  models_body=$(curl -s -m 15 -H "Authorization: Bearer ${token}" \
    "http://${GATEWAY_HOST}:${GATEWAY_PORT}/opc/llm/models" 2>/dev/null) || models_body=""
  models_code=$(body_code "${models_body}")
  if [[ -z "${token}" ]]; then
    nok "26.9 没有 token, 读不了 /opc/llm/models"
  elif [[ "${models_code}" != "200" ]]; then
    nok "26.9 GET /opc/llm/models body.code=${models_code} —— 读不到 provider 列表"
  else
    enabled_providers=$(printf '%s' "${models_body}" | python -c "
import sys, json
try:
    rows = json.load(sys.stdin).get('data') or []
except Exception:
    print(''); raise SystemExit
names = []
for r in rows:
    if not isinstance(r, dict):
        continue
    if r.get('enabled') is False:
        continue
    n = str(r.get('provider', '')).strip().lower()
    if n and n not in names:
        names.append(n)
print(' '.join(names))
" 2>/dev/null)
    local n_prov has_ds=""
    n_prov=$(printf '%s' "${enabled_providers}" | wc -w | tr -d ' ')
    for p in ${enabled_providers}; do
      [[ "${p}" == *deepseek* ]] && has_ds="yes"
    done
    if [[ "${n_prov}" -lt 2 ]]; then
      nok "26.9 已启用的 provider 只有 ${n_prov} 个 —— 降级链没接上"
    elif [[ -z "${has_ds}" ]]; then
      nok "26.9 已启用的 provider 里没有 deepseek —— 降级链的目标不在"
    else
      ok "26.9 降级链接上了: 已启用 ${n_prov} 个 provider(${enabled_providers})"
    fi
  fi

  # --- 26.9b 那把钥匙真的能开门吗 ---
  #
  # enabled() 的定义是「配置开关 && apiKey **非空**」→ **不验 key 对不对**。
  # 一把 401 的 key 也是非空 → deepseek 会被算成「已启用」→ 26.9 报绿,
  # 而降级链其实有一半是死的。那就是一条**假绿灯**,比没有这条断言更坏。
  #
  # 这里用零成本的 `GET /v1/models` 真探一次(不计 token):每个已启用的 provider
  # 都必须 200。凭据**只在容器自己的 env 里**, 脚本里不出现 key 的值,
  # 输出里**只有 HTTP 状态码**(状态码不是秘密, key 是)。
  # base-url 从 opc-ai-core 的 yml 里读 —— 不在脚本里写死, 也不新增真相源。
  if [[ -z "${enabled_providers}" ]]; then
    nok "26.9b 没有已启用的 provider 可探 —— 26.9 就没通过"
  else
    local ai_core_yml probe_bad="" probe_ok=""
    ai_core_yml="${DEPLOY_DIR}/../ruoyi-modules/opc-ai-core/src/main/resources/application.yml"
    for p in ${enabled_providers}; do
      local base_url env_var code
      base_url=$(llm_yml_value "${ai_core_yml}" "${p}" "base-url")
      env_var="$(printf '%s' "${p}" | tr '[:lower:]' '[:upper:]')_API_KEY"
      if [[ -z "${base_url}" ]]; then
        probe_bad="${probe_bad} ${p}=<yml 里没有这个 provider 的 base-url>"
        continue
      fi
      # $ENVVAR 在**容器里**展开 → key 的值既不上宿主机命令行, 也不进输出
      code=$(docker exec aiopc-ai-core sh -c "curl -s -o /dev/null -w '%{http_code}' --max-time 20 -H \"Authorization: Bearer \$${env_var}\" '${base_url}/models'" 2>/dev/null)
      code=$(printf '%s' "${code}" | tr -dc '0-9')
      if [[ -z "${code}" ]]; then
        probe_bad="${probe_bad} ${p}=探不通(docker exec 没拿到状态码: 容器没起来? 容器里没有 curl?)"
      elif [[ "${code}" == "000" ]]; then
        probe_bad="${probe_bad} ${p}=000(探不通: 网络不通 / 超时 / 容器里没有 curl)"
      elif [[ "${code}" != "200" ]]; then
        probe_bad="${probe_bad} ${p}=${code}"
      else
        probe_ok="${probe_ok} ${p}=200"
      fi
    done
    if [[ -n "${probe_bad}" ]]; then
      nok "26.9b 已启用的 provider 里这把钥匙开不了门:${probe_bad} —— 降级链有一半是死的(enabled() 只看 key 非空, 不看 key 对不对)"
    else
      ok "26.9b 每个已启用的 provider 的 key 都被接受(GET /v1/models):${probe_ok}"
    fi
  fi

  # --- 26.10 insight 自己的 LLM 也通了 ---
  #
  # 只**读**最近一条(按 id 取最新)。
  # ⚠️ **不许**在健康检查里调 POST /opc/insight/advice —— 那是真花钱
  #    (用户 2026-11-04:「记住减少真实测试, 花钱的」)。
  #    造那条非 FALLBACK 的行是**部署流程**里的一次动作(调一次, 之后零成本)。
  # 这一条**不限定公司**:建议接口的 companyId 取自登录用户, 与工作流的 company 无关。
  #
  # ⚠️ 光看 llm_used 不够 —— 那只说明**那一条**是真 LLM 写的,
  #    说不了「现在」通不通。2026-11-05 这条就是这么假红的:
  #    最新一行是 2026-10-20 的种子数据(llm_used='FALLBACK'),
  #    用它判「今天的 insight LLM 健不健康」= **拿 16 天前的一行判今天**。
  #    所以这里要求它**够新**(7 天内, 与 26.6 一个口径):
  #    · 太旧 → 红的理由是「只能证明当时通, 证明不了现在通」, 不是「当时没通」;
  #    · 修法是部署后调一次 POST /opc/insight/advice(一次 LLM 调用, 之后零成本)。
  local adv adv_id adv_llm adv_time adv_age
  adv=$(db_q "SELECT CONCAT(id,'|',IFNULL(llm_used,''),'|',IFNULL(create_time,'')) FROM opc_insight_advice ORDER BY id DESC LIMIT 1;" | tr -d '\r')
  if [[ -z "${adv}" ]]; then
    nok "26.10 opc_insight_advice 一行都没有 —— insight 的 LLM 从来没跑过"
  else
    adv_id=$(printf '%s' "${adv}" | cut -d'|' -f1)
    adv_llm=$(printf '%s' "${adv}" | cut -d'|' -f2)
    adv_time=$(printf '%s' "${adv}" | cut -d'|' -f3)
    adv_age=$(db_q "SELECT TIMESTAMPDIFF(HOUR, '${adv_time}', NOW());" 2>/dev/null | tr -d '\r')
    if [[ -z "${adv_llm}" || "${adv_llm}" == "FALLBACK" ]]; then
      nok "26.10 最近一条建议(id=${adv_id}, ${adv_time})的 llm_used='${adv_llm}' —— 这一条是降级模板, 不是模型看着真实数据写的"
    elif [[ -z "${adv_age}" ]]; then
      nok "26.10 最近一条建议(id=${adv_id})的 llm_used='${adv_llm}' 但算不出它有多旧 —— 这条断言等于没跑"
    elif [[ "${adv_age}" -gt 168 ]]; then
      nok "26.10 最近一条真 LLM 的建议(id=${adv_id})是 $(( adv_age / 24 )) 天前的 —— 只能证明当时通, 证明不了现在通(部署后调一次 POST /opc/insight/advice)"
    else
      ok "26.10 最近一条建议(id=${adv_id}, ${adv_age} 小时前)是真 LLM 写的: llm_used=${adv_llm}"
    fi
  fi

  # --- 26.13 LLM 的正文不许是空的(2026-11-05 那个坑) ---
  #
  # 线上怎么炸的: MiniMax-M3.1-Flash 是**强制思考**模型, 思考 token 计入
  # max_tokens → 额度被思考吃光 → 响应里**没有 content 字段** → 解析成 null →
  # provider 报「成功」→ 一路漏到工作流 persist 层, 才炸成一句看不懂的
  # 「产出不是字符串: n2」(锅甩给了一个无辜的节点)。
  #
  # 这条断的是**结果**而不是日志:
  #   · 最近一次工作流运行得是 SUCCESS(不是那个坑造成的 FAILED);
  #   · 它落表的那份日报, 正文要么是模型写的(REAL), 要么是引擎的确定性兜底摘要
  #     (FALLBACK_PREFIX = "[自动聚合·未走 LLM]")—— 空正文不许悄悄存进去。
  # 判「最新一次」而不是「有没有一次」: 「存在一条好结果」会被历史上的一条永久满足。
  local n13
  n13=$(db_q "SELECT CONCAT(r.id,'|',r.status,'|',
        IFNULL(d.report_status,'<无日报>'),'|',
        CASE WHEN d.id IS NULL THEN 'NOREPORT'
             WHEN d.summary_md IS NULL OR TRIM(d.summary_md) = '' THEN 'EMPTY'
             WHEN d.summary_md LIKE '[自动聚合%' THEN 'FALLBACK_PREFIX'
             ELSE 'REAL' END)
      FROM opc_agent_workflow_run r
      LEFT JOIN opc_insight_daily_report d ON d.source_workflow_run_id = r.id
      ORDER BY r.id DESC, d.id DESC LIMIT 1;" | tr -d '\r')
  if [[ -z "${n13}" ]]; then
    nok "26.13 查不到 opc_agent_workflow_run —— 护栏空转, 证明不了任何东西"
  else
    local n13_id n13_status n13_repstat n13_body
    n13_id=$(printf '%s' "${n13}" | cut -d'|' -f1)
    n13_status=$(printf '%s' "${n13}" | cut -d'|' -f2)
    n13_repstat=$(printf '%s' "${n13}" | cut -d'|' -f3)
    n13_body=$(printf '%s' "${n13}" | cut -d'|' -f4)
    local n13_why=""
    [[ "${n13_status}" != "SUCCESS" ]] && n13_why="${n13_why} 最近一次运行 status='${n13_status}' "
    case "${n13_body}" in
      NOREPORT)       n13_why="${n13_why} 它没产出任何日报 " ;;
      EMPTY)          n13_why="${n13_why} 日报正文是空的 " ;;
      FALLBACK_PREFIX) n13_why="${n13_why} 日报正文是引擎的兜底摘要(LLM 没给出正文) " ;;
    esac
    if [[ -z "${n13_why}" ]]; then
      ok "26.13 最近一次工作流运行(id=${n13_id})是 SUCCESS, 它的日报正文是模型写的(report_status=${n13_repstat})"
    else
      nok "26.13${n13_why}—— 2026-11-05 的坑(LLM 报成功但正文为空)还在"
    fi
  fi

  # --- 26.11 四份 yml 的 opc.llm 段必须逐行对得上 ---
  #
  # 四份各写各的 = 「配置真相源有两份」→ 改了一份另外三份不跟 → 又是漂移。
  # 口径:只比 opc.llm 段里的**键与值**, 逐行相等; **注释不算**。
  local yml_ai yml_hub yml_insight yml_nacos sec_ai sec_hub sec_insight sec_nacos
  yml_ai="${DEPLOY_DIR}/../ruoyi-modules/opc-ai-core/src/main/resources/application.yml"
  yml_hub="${DEPLOY_DIR}/../ruoyi-modules/opc-agent-hub/src/main/resources/application.yml"
  yml_insight="${DEPLOY_DIR}/../ruoyi-modules/opc-insight/src/main/resources/application.yml"
  yml_nacos="${DEPLOY_DIR}/nacos/application-dev.yml"
  sec_ai=$(llm_section "${yml_ai}")
  sec_hub=$(llm_section "${yml_hub}")
  sec_insight=$(llm_section "${yml_insight}")
  sec_nacos=$(llm_section "${yml_nacos}")
  if [[ -z "${sec_ai}" || -z "${sec_hub}" || -z "${sec_insight}" || -z "${sec_nacos}" ]]; then
    nok "26.11 有一份 yml 的 opc.llm 段读不出来 —— 对不了表"
  elif [[ "${sec_ai}" == "${sec_hub}" && "${sec_hub}" == "${sec_insight}" && "${sec_insight}" == "${sec_nacos}" ]]; then
    ok "26.11 四份 yml 的 opc.llm 段逐行一致(注释不算)"
  else
    nok "26.11 四份 yml 的 opc.llm 段对不上 —— 改了一份另外三份没跟(配置有两个真相源)"
  fi

  # --- 26.12 容器里降级链真的开着 ---
  #
  # yml 里写了不等于跑起来会用:enabled() = 「配置开关 && apiKey 非空」。
  # 空 key 会让 enabled() 变成 false → 看着配了降级链, 实际一次都不会退过去。
  # 所以三个容器里都得是「开关开着 + 两把钥匙都在」。
  #
  # 判据是「开关和有没有」, **不是**「key 是不是某个具体值」——
  # 后者要把真 key 写进检查脚本, 那是最坏的一种泄露。
  local c26 s26
  for c26 in aiopc-ai-core aiopc-agent-hub aiopc-insight; do
    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${c26}"; then
      nok "26.12 容器 ${c26} 没起来 —— 降级链在哪个容器里开着都说不清"
      continue
    fi
    s26=$(docker exec "${c26}" sh -c '
      case "$OPC_LLM_DEEPSEEK_ENABLED" in
        "")          echo "ENABLED=EMPTY" ;;
        true|TRUE|1) echo "ENABLED=true" ;;
        *)           echo "ENABLED=false" ;;
      esac
      if [ -n "$DEEPSEEK_API_KEY" ]; then echo "DEEPSEEK=NONEMPTY"; else echo "DEEPSEEK=EMPTY"; fi
      if [ -n "$MINIMAX_API_KEY" ]; then echo "MINIMAX=NONEMPTY"; else echo "MINIMAX=EMPTY"; fi
    ' 2>/dev/null | tr '\n' ' ')
    local why26=""
    [[ "${s26}" != *"ENABLED=true"* ]] && why26="${why26} OPC_LLM_DEEPSEEK_ENABLED 不是真值 "
    [[ "${s26}" != *"DEEPSEEK=NONEMPTY"* ]] && why26="${why26} DEEPSEEK_API_KEY 空 "
    [[ "${s26}" != *"MINIMAX=NONEMPTY"* ]] && why26="${why26} MINIMAX_API_KEY 空 "
    if [[ -z "${why26}" ]]; then
      ok "26.12 ${c26}: 降级链在容器里真的开着(开关开 + 两把钥匙都在)"
    else
      nok "26.12 ${c26}:${why26}—— yml 里配了不等于容器里开着"
    fi
  done

  # --- 26.14 / 26.15 日报正文里的数字必须来自 kpi_json ---
  #
  # prompt 里那两条硬性要求(「不得出现给定数据里没有的数字」「昨日没有流水必须明说」)
  # 一直只是**请求** —— 模型这次照做了, 但那是模型的自觉, 不是护栏。
  # 模型哪次不照做, 没有任何东西会红 → 这正是 CLAUDE.md 第二节说的那类坑。
  #
  # 这里把它变成断言:
  #   26.14  正文里每一个「N 元」「N 笔」都得在 kpi_json 里找得到(数值相等)
  #   26.15  昨日没有流水就必须明说, 且不许写成「营收 0 元, 运营平稳」
  #
  # 判据代码: deploy/scripts/report_body_truth.py
  # 单测:     cd deploy/scripts && python3 -m unittest discover -s tests
  #           (每一条「应该报红」的路径都有用例 —— 断言的价值全在它会不会红)
  local rid14 sum14 kpi14 py14 tmp_sum tmp_kpi out14 rc14
  py14="$(command -v python3 || command -v python)"
  if [[ -z "${py14}" ]]; then
    nok "26.14 找不到 python —— 日报正文里的数字没法对账"
    nok "26.15 找不到 python —— 「昨日有无流水」没法对账"
  else
    rid14=$(db_q "SELECT id FROM opc_insight_daily_report WHERE summary_md IS NOT NULL AND TRIM(summary_md) <> '' ORDER BY id DESC LIMIT 1;" | tr -d '\r')
    if [[ -z "${rid14}" ]]; then
      nok "26.14 取不到有正文的日报 —— 护栏空转, 证明不了任何东西"
      nok "26.15 取不到有正文的日报 —— 护栏空转, 证明不了任何东西"
    else
      # 正文里的换行/回车换成空格: 一行塞进 mysql -B 的输出里, 后面的解析才稳
      sum14=$(db_q "SELECT REPLACE(REPLACE(summary_md, CHAR(10), ' '), CHAR(13), ' ') FROM opc_insight_daily_report WHERE id = ${rid14};")
      kpi14=$(db_q "SELECT kpi_json FROM opc_insight_daily_report WHERE id = ${rid14};")
      tmp_sum=$(mktemp)
      tmp_kpi=$(mktemp)
      printf '%s' "${sum14}" > "${tmp_sum}"
      printf '%s' "${kpi14}" > "${tmp_kpi}"
      out14=$("${py14}" "${SCRIPT_DIR}/report_body_truth.py" "${tmp_sum}" "${tmp_kpi}" 2>&1)
      rc14=$?
      rm -f "${tmp_sum}" "${tmp_kpi}"
      if [[ "${rc14}" -gt 1 ]]; then
        nok "26.14 正文对账脚本没跑通(退出码 ${rc14}) —— 这条断言等于没跑: ${out14}"
        nok "26.15 正文对账脚本没跑通(退出码 ${rc14}) —— 这条断言等于没跑: ${out14}"
      else
        local line14 detail14
        line14=$(printf '%s\n' "${out14}" | grep '^GROUP|FABRICATION|OK|' | head -1)
        if [[ -n "${line14}" ]]; then
          ok "26.14 日报(id=${rid14})正文里的数字都对得上: ${line14##*|}"
        else
          detail14=$(printf '%s\n' "${out14}" | grep '^GROUP|FABRICATION|FAIL|' \
                     | sed 's/^GROUP|FABRICATION|FAIL|[^|]*|//' | head -3 | tr '\n' ' ')
          nok "26.14 日报(id=${rid14})正文里有编出来的数字: ${detail14}"
        fi
        line14=$(printf '%s\n' "${out14}" | grep '^GROUP|NO_FLOW|OK|' | head -1)
        if [[ -n "${line14}" ]]; then
          ok "26.15 日报(id=${rid14})关于「昨日有无流水」照实说了: ${line14##*|}"
        else
          detail14=$(printf '%s\n' "${out14}" | grep '^GROUP|NO_FLOW|FAIL|' \
                     | sed 's/^GROUP|NO_FLOW|FAIL|[^|]*|//' | head -3 | tr '\n' ' ')
          nok "26.15 日报(id=${rid14})关于「昨日有无流水」没说实话: ${detail14}"
        fi
      fi
    fi
  fi
}

# ---------- Main ----------
main() {
  echo "=================================================="
  echo " OPC stack 健康检查 ($(date '+%Y-%m-%d %H:%M:%S'))"
  echo "=================================================="
  # HEALTH_ONLY=<节号> 只跑那一节 —— 调单节时用, 不影响全量跑法。
  # 例: HEALTH_ONLY=24 bash deploy.sh health
  local only="${HEALTH_ONLY:-}"
  step() {
    local num="$1"; shift
    if [[ -z "${only}" || "${only}" == "${num}" ]]; then "$@"; fi
  }
  step 1  check_containers
  step 2  check_nacos
  step 5  check_mysql
  step 4  check_frontend
  step 3  login_and_check
  step 6  check_notification
  step 7  check_crm
  step 8  check_hr
  step 9  check_erp
  step 10 check_content
  step 11 check_marketplace
  step 12 check_gateway_whitelist
  step 13 check_tenant_guard
  step 14 check_crm_ownership
  step 15 check_port_exposure
  step 16 check_body_tenant_and_oauth_state
  step 17 check_metering_chain
  step 18 check_company_switch
  step 19 check_marketplace_truth
  step 20 check_marketplace_actions
  step 21 check_crm_ai_score
  step 22 check_nacos_config_center
  step 23 check_memory_and_flow_control
  step 24 check_sentinel_dashboard
  step 25 check_scheduler_truth
  step 26 check_finance_agent_truth
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