#!/usr/bin/env bash
# ============================================================
# Nacos 批量导入 OPC prod 命名空间配置
# 命名空间：opc-prod（需提前在 Nacos 控制台创建，namespaceId 见下方）
# 用法：
#   NACOS_ADDR=nacos.example.com:8848 \
#   NACOS_USER=nacos \
#   NACOS_PASS='xxx' \
#   NAMESPACE_ID=opc-prod-uuid \
#   ./import-prod.sh
# ============================================================
set -euo pipefail

# ---------- 参数校验 ----------
: "${NACOS_ADDR:?NACOS_ADDR 必须设置，例如 nacos.example.com:8848}"
: "${NACOS_USER:?NACOS_USER 必须设置（默认 nacos）}"
: "${NACOS_PASS:?NACOS_PASS 必须设置}"
: "${NAMESPACE_ID:?NAMESPACE_ID 必须设置（opc-prod 的 namespaceId）}"

GROUP="DEFAULT_GROUP"
TYPE="yaml"  # 文件类型标识，非必需

# ---------- 当前目录 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------- 文件清单 ----------
FILES=(
  "application-prod.yml"
  "opc-common-prod.yml"
  "opc-ai-core-prod.yml"
  "opc-agent-hub-prod.yml"
  "opc-user-center-prod.yml"
  "opc-billing-prod.yml"
  "opc-finance-prod.yml"
)

# ---------- 单文件导入 ----------
import_one() {
  local file="$1"
  local data_id="$file"
  local path="${SCRIPT_DIR}/${file}"

  if [[ ! -f "${path}" ]]; then
    echo "[FAIL] 缺少文件：${path}" >&2
    return 1
  fi

  echo "[INFO] 导入 ${data_id} (group=${GROUP}, namespace=${NAMESPACE_ID})"

  # 方式 1：通过 /v1/cs/configs POST 发布
  local resp
  resp=$(curl -sS -u "${NACOS_USER}:${NACOS_PASS}" \
    -X POST \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "namespaceId=${NAMESPACE_ID}" \
    --data-urlencode "type=${TYPE}" \
    --data-urlencode "content@${path}" \
    "http://${NACOS_ADDR}/nacos/v1/cs/configs" || true)

  if [[ "${resp}" == "true" ]]; then
    echo "[OK]   ${data_id}"
    return 0
  else
    echo "[FAIL] ${data_id} -> ${resp}" >&2
    return 1
  fi
}

# ---------- 验证导入结果 ----------
verify_one() {
  local file="$1"
  local data_id="$file"

  local resp
  resp=$(curl -sS -u "${NACOS_USER}:${NACOS_PASS}" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "namespaceId=${NAMESPACE_ID}" \
    "http://${NACOS_ADDR}/nacos/v1/cs/configs" || true)

  if [[ -z "${resp}" ]]; then
    echo "[FAIL] 验证失败：${data_id} 无内容" >&2
    return 1
  fi
  echo "[OK]   验证 ${data_id} (size=${#resp} bytes)"
}

# ---------- 主流程 ----------
main() {
  echo "=================================================="
  echo "OPC prod Nacos 配置批量导入"
  echo "  地址   : ${NACOS_ADDR}"
  echo "  命名空间: ${NAMESPACE_ID}"
  echo "  分组   : ${GROUP}"
  echo "=================================================="

  local failed=0
  for f in "${FILES[@]}"; do
    if ! import_one "${f}"; then
      failed=$((failed + 1))
    fi
  done

  echo "--------------------------------------------------"
  echo "导入完成，失败数：${failed} / ${#FILES[@]}"

  echo "--------------------------------------------------"
  echo "验证阶段："
  for f in "${FILES[@]}"; do
    verify_one "${f}" || true
  done

  if [[ "${failed}" -gt 0 ]]; then
    exit 1
  fi
}

main "$@"