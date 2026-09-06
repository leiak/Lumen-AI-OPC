#!/usr/bin/env bash
# ============================================================
# Nacos 配置导出（备份 opc-prod 命名空间）
# 用法：./export-prod.sh [out_dir]
# 输出：out_dir/<dataId>.yml
# ============================================================
set -euo pipefail

: "${NACOS_ADDR:?NACOS_ADDR 必须设置}"
: "${NACOS_USER:?NACOS_USER 必须设置}"
: "${NACOS_PASS:?NACOS_PASS 必须设置}"
: "${NAMESPACE_ID:?NAMESPACE_ID 必须设置}"

OUT_DIR="${1:-./backup-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "${OUT_DIR}"

GROUP="DEFAULT_GROUP"
FILES=(
  "application-prod.yml"
  "opc-common-prod.yml"
  "opc-ai-core-prod.yml"
  "opc-agent-hub-prod.yml"
  "opc-user-center-prod.yml"
  "opc-billing-prod.yml"
  "opc-finance-prod.yml"
)

for data_id in "${FILES[@]}"; do
  echo "[INFO] 导出 ${data_id}"
  curl -sS -u "${NACOS_USER}:${NACOS_PASS}" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "namespaceId=${NAMESPACE_ID}" \
    "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
    -o "${OUT_DIR}/${data_id}"
done

echo "[OK] 备份目录：${OUT_DIR}"
ls -la "${OUT_DIR}"