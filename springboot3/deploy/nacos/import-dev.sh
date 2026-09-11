#!/usr/bin/env bash
# ============================================================
# Nacos 批量导入 OPC dev 命名空间配置 (W48.6 固化)
#
# 命名空间：opc-dev
# 用法：
#   cd springboot3/deploy/nacos
#   ./import-dev.sh
#
# 默认连本地 127.0.0.1:8848（不需 auth）。远程：
#   NACOS_ADDR=nacos.example.com:8848 ./import-dev.sh
#
# 注意：用 Python urllib (不用 curl content@file) 避免 Windows
# git-bash 中文 / 行尾编码坑。
# ============================================================
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NAMESPACE_ID="${NAMESPACE_ID:-opc-dev}"
GROUP="DEFAULT_GROUP"
TYPE="yaml"
PROTOCOL="http"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# dev 命名空间实际被 Spring 拉取的 yml 清单
# (ai-core/finance/billing/... 各服务 bootstrap.yml spring.config.import
#  会拉 application-dev.yml + opc-<svc>-dev.yml，dev 命名空间只放了
# 共享的 application-dev.yml，各服务的 opc-<svc>-dev.yml 是空的)
NAMES=(
  "application-dev.yml"
  "opc-notification-dev.yml"
  "opc-crm-dev.yml"
  "opc-community-dev.yml"
)

# ---------- 单文件推送（Python urllib + content 走 stdin）----------
import_one() {
  local name="$1"
  local path="${SCRIPT_DIR}/${name}"
  if [[ ! -f "${path}" ]]; then
    echo "[FAIL] 缺少文件：${path}" >&2
    return 1
  fi
  echo "[INFO] 导入 ${name} (group=${GROUP}, namespace=${NAMESPACE_ID})"
  local resp
  resp=$(python -c "
import urllib.request, urllib.parse, sys
content = sys.stdin.buffer.read().decode('utf-8')
data = urllib.parse.urlencode({
    'dataId': '${name}',
    'group': '${GROUP}',
    'namespaceId': '${NAMESPACE_ID}',
    'type': '${TYPE}',
    'content': content
}).encode('utf-8')
req = urllib.request.Request('${PROTOCOL}://${NACOS_ADDR}/nacos/v1/cs/configs', data=data, method='POST')
try:
    r = urllib.request.urlopen(req, timeout=15)
    print(r.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print('HTTP', e.code, e.read().decode('utf-8', errors='replace'))
    sys.exit(1)
" < "${path}" 2>&1) || true
  if [[ "${resp}" == "true" ]]; then
    echo "[OK]   ${name}"
    return 0
  else
    echo "[FAIL] ${name} -> ${resp}" >&2
    return 1
  fi
}

# ---------- 验证（行尾归一化后 md5 对比, repo 内容走 stdin 避开 Windows path 坑）----------
verify_one() {
  local name="$1"
  local path="${SCRIPT_DIR}/${name}"
  python -c "
import urllib.request, hashlib, sys
name = '${name}'
r = urllib.request.urlopen(
    '${PROTOCOL}://${NACOS_ADDR}/nacos/v1/cs/configs?dataId=' + name + '&group=${GROUP}&namespaceId=${NAMESPACE_ID}',
    timeout=10)
nacos = r.read().decode('utf-8')
repo = sys.stdin.buffer.read().decode('utf-8')
# Nacos 服务端会标准化行尾, 用 splitlines 归一化
nacos_norm = '\n'.join(nacos.splitlines())
repo_norm = '\n'.join(repo.splitlines())
nacos_h = hashlib.md5(nacos_norm.encode('utf-8')).hexdigest()[:10]
repo_h = hashlib.md5(repo_norm.encode('utf-8')).hexdigest()[:10]
if nacos_norm == repo_norm:
    print('  [OK  ] ' + name + ' (md5=' + repo_h + ', nacos=' + str(len(nacos)) + 'B repo=' + str(len(repo)) + 'B)')
else:
    print('  [WARN] ' + name + ' nacos=' + nacos_h + ' repo=' + repo_h + ' - 内容已漂移！')
    for i, (a, b) in enumerate(zip(nacos_norm, repo_norm)):
        if a != b:
            ctx_start = max(0, i-20)
            print('         首个差异 @ char ' + str(i) + ':')
            print('           nacos: ...' + repr(nacos_norm[ctx_start:i+20]))
            print('           repo : ...' + repr(repo_norm[ctx_start:i+20]))
            break
    sys.exit(1)
" < "${path}" || return 1
}

main() {
  echo "=================================================="
  echo "OPC dev Nacos 配置批量导入"
  echo "  地址   : ${PROTOCOL}://${NACOS_ADDR}"
  echo "  命名空间: ${NAMESPACE_ID}"
  echo "  文件   : ${#NAMES[@]}"
  echo "=================================================="

  local failed=0
  for n in "${NAMES[@]}"; do
    if ! import_one "${n}"; then
      failed=$((failed + 1))
    fi
  done

  echo "--------------------------------------------------"
  echo "导入完成，失败数：${failed} / ${#NAMES[@]}"
  echo ""
  echo "验证阶段："
  for n in "${NAMES[@]}"; do
    verify_one "${n}" || true
  done

  if [[ "${failed}" -gt 0 ]]; then
    exit 1
  fi
}

main "$@"
