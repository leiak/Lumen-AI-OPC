#!/usr/bin/env bash
# ============================================================
# 确保 opc-dev 命名空间在 Nacos 里真实存在（幂等）
#
# 用法：
#   cd springboot3/deploy/nacos
#   ./create-namespace.sh
#   NACOS_ADDR=nacos.example.com:8848 ./create-namespace.sh
#
# 命名空间在 Nacos 里有**两个地方**：
#   1) tenant_info         —— 命名空间的「户口本」，控制台的命名空间列表读它
#   2) config_info.tenant_id —— 配置实际存哪一栏
#
# 服务端按 tenant=opc-dev 拉配置，只要 config_info 里有 tenant_id='opc-dev' 的行
# 就能拉到；但控制台和命名空间列表读的是 tenant_info —— 那张表空着的话，
# 「配置中心在管着服务」这件事在控制台上一个字都看不见（历史遗留：老脚本用
# namespaceId= 推配置，被服务端静默回落到默认 tenant，配置其实落在 public 栏）。
#
# 这个脚本补的就是 tenant_info 那一行。只动户口本，不碰配置内容。
# ============================================================
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NAMESPACE_ID="${NAMESPACE_ID:-opc-dev}"
NAMESPACE_NAME="${NAMESPACE_NAME:-OPC Dev}"
NAMESPACE_DESC="${NAMESPACE_DESC:-OPC 演示/开发环境（compose 全栈共用）}"
PROTOCOL="http"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------- 查询命名空间列表 ----------
list_namespaces() {
  python -c "
import urllib.request, json, sys
try:
    r = urllib.request.urlopen('${PROTOCOL}://${NACOS_ADDR}/nacos/v1/console/namespaces', timeout=10)
    d = json.loads(r.read().decode('utf-8'))
except Exception as e:
    print('ERR', e)
    sys.exit(1)
for ns in d.get('data') or []:
    print(ns.get('namespace', ''))
"
}

# ---------- 新建命名空间 ----------
create_namespace() {
  python -c "
import urllib.request, urllib.parse, sys
data = urllib.parse.urlencode({
    'customNamespaceId': '${NAMESPACE_ID}',
    'namespaceName': '${NAMESPACE_NAME}',
    'namespaceDesc': '${NAMESPACE_DESC}'
}).encode('utf-8')
req = urllib.request.Request('${PROTOCOL}://${NACOS_ADDR}/nacos/v1/console/namespaces', data=data, method='POST')
try:
    r = urllib.request.urlopen(req, timeout=15)
    print(r.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print('HTTP', e.code, e.read().decode('utf-8', errors='replace'))
    sys.exit(1)
"
}

main() {
  echo "[INFO] 检查命名空间 ${NAMESPACE_ID} ..."

  local existing
  existing="$(list_namespaces)"
  if grep -qx -- "${NAMESPACE_ID}" <<< "${existing}"; then
    echo "[OK]   命名空间 ${NAMESPACE_ID} 已存在，跳过创建"
    return 0
  fi

  echo "[INFO] 命名空间 ${NAMESPACE_ID} 不存在，正在创建 ..."
  local resp
  resp="$(create_namespace)"
  if [[ "${resp}" != "true" ]]; then
    echo "[FAIL] 创建命名空间失败 -> ${resp}" >&2
    return 1
  fi
  echo "[OK]   命名空间 ${NAMESPACE_ID} 创建完成"
}

main "$@"
