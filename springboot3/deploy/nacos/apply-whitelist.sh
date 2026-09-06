#!/usr/bin/env bash
# ============================================================
# OPC 网关白名单补丁 — 一键应用到 Nacos
# Apply OPC Invite Whitelist Patch to Nacos
# ============================================================
# 用法:
#   ./apply-whitelist.sh <env>           # env ∈ {dev,staging,prod}
#   ./apply-whitelist.sh dev             # 应用到 application-dev.yml
#
# 环境变量:
#   NACOS_ADDR     默认 127.0.0.1:8848
#   NACOS_USER     默认 nacos
#   NACOS_PASS     默认 nacos
#   NACOS_GROUP    默认 DEFAULT_GROUP
#   DRY_RUN        默认 0;设 1 只打印,不推送
#
# 前置:
#   该脚本会先 GET 现有 application-{env}.yml,与本目录的
#   whitelist-opc-invite.yml 做合并,然后 POST 回 Nacos。
#   合并策略:以本地补丁的 security.ignore.whites 为准,
#           合并远端的其余 security.* 配置。
# ============================================================

set -euo pipefail

# ---------- 参数解析 ----------
ENV="${1:-}"
if [[ -z "$ENV" ]]; then
  echo "用法: $0 <env>   (env ∈ {dev,staging,prod})"
  exit 1
fi
if [[ ! "$ENV" =~ ^(dev|staging|prod)$ ]]; then
  echo "[ERROR] env 必须是 dev/staging/prod,得到: $ENV"
  exit 1
fi

# ---------- 配置 ----------
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASS="${NACOS_PASS:-nacos}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
DRY_RUN="${DRY_RUN:-0}"

DATA_ID="application-${ENV}.yml"
SNIPPET_FILE="$(dirname "$0")/whitelist-opc-invite.yml"

if [[ ! -f "$SNIPPET_FILE" ]]; then
  echo "[ERROR] 找不到 snippet: $SNIPPET_FILE"
  exit 1
fi

echo "=========================================="
echo "[apply-whitelist] env      = $ENV"
echo "[apply-whitelist] dataId   = $DATA_ID"
echo "[apply-whitelist] nacos    = $NACOS_ADDR"
echo "[apply-whitelist] snippet  = $SNIPPET_FILE"
echo "[apply-whitelist] dry_run  = $DRY_RUN"
echo "=========================================="

# ---------- 工具: yq 或 python 做 YAML 合并 ----------
merge_yaml() {
  local remote="$1"
  local patch="$2"
  python3 - "$remote" "$patch" <<'PY'
import sys, yaml

remote_path, patch_path = sys.argv[1], sys.argv[2]

def load(p):
    if p == "-" or not p:
        return {}
    with open(p, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}

remote = load(remote_path)
patch  = load(patch_path)

# 合并策略:patch.security.ignore.whites 整体替换(以本补丁为准),
# 其他节点保留远端(防误删其他团队配置)。
if "security" not in remote: remote["security"] = {}
if "ignore"   not in remote["security"]: remote["security"]["ignore"] = {}
remote["security"]["ignore"]["whites"] = patch["security"]["ignore"]["whites"]

yaml.dump(remote, sys.stdout, default_flow_style=False, allow_unicode=True, sort_keys=False)
PY
}

# ---------- 1. 拉取远端现有配置 ----------
echo "[1/3] GET 远端 $DATA_ID ..."
REMOTE_TMP="$(mktemp).yml"
HTTP_CODE=$(curl -sS -o "$REMOTE_TMP" -w "%{http_code}" \
  --get "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "group=${NACOS_GROUP}" \
  --data-urlencode "username=${NACOS_USER}" \
  --data-urlencode "password=${NACOS_PASS}" || echo "000")

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "[WARN] 远端配置不存在或拉取失败(HTTP $HTTP_CODE),将以 snippet 为基准创建"
  REMOTE_TMP="$(mktemp).yml"; echo "" > "$REMOTE_TMP"
fi

# ---------- 2. 合并 ----------
echo "[2/3] 合并本地 snippet ..."
MERGED_TMP="$(mktemp).yml"
merge_yaml "$REMOTE_TMP" "$SNIPPET_FILE" > "$MERGED_TMP"

if [[ "${DRY_RUN}" == "1" ]]; then
  echo "[DRY-RUN] 合并后内容预览:"
  echo "----------------------------------------"
  cat "$MERGED_TMP"
  echo "----------------------------------------"
  rm -f "$REMOTE_TMP" "$MERGED_TMP"
  exit 0
fi

# ---------- 3. POST 回 Nacos ----------
echo "[3/3] POST 回 Nacos ..."
PUB_CODE=$(curl -sS -o /tmp/nacos-pub-$$.log -w "%{http_code}" \
  -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "group=${NACOS_GROUP}" \
  --data-urlencode "username=${NACOS_USER}" \
  --data-urlencode "password=${NACOS_PASS}" \
  --data-urlencode "content@${MERGED_TMP}")

if [[ "$PUB_CODE" == "200" ]]; then
  echo "[OK] $DATA_ID 已更新。Nacos 通常 30s 内推送到 gateway 实例。"
else
  echo "[ERROR] Nacos 发布失败(HTTP $PUB_CODE):"
  cat /tmp/nacos-pub-$$.log
  rm -f "$REMOTE_TMP" "$MERGED_TMP" /tmp/nacos-pub-$$.log
  exit 2
fi

rm -f "$REMOTE_TMP" "$MERGED_TMP" /tmp/nacos-pub-$$.log
echo "✅ Done."
