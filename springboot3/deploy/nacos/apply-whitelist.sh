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
#   NACOS_NAMESPACE 默认 opc-dev  ← ⚠️ 必须与网关读的命名空间一致
#   DRY_RUN        默认 0;设 1 只打印,不推送
#   PYTHON_BIN     默认自动挑一个装了 PyYAML 的 python
#
# 前置:
#   该脚本会先 GET 现有 application-{env}.yml,与本目录的
#   whitelist-opc-invite.yml 做合并,然后 POST 回 Nacos。
#   合并策略 (W80 修复): security.ignore.whites 取【并集】——
#   远端条目在前(保留运维手工加的),本地补丁去重追加在后。
#   其余 security.* / 非 security 节点一律原样保留远端。
#
#   但纯并集只增不减, 有两个洞, 所以并集之外补了两条删除规则 (2026-10-24):
#   A) 显式移除: snippet 里 security.ignore.whitesRemove 列出的条目, 从远端删掉。
#      (并集不会删"从 snippet 里删掉"的条目 —— W80 把 /opc/user/invitations/*
#      换成两条精确条目后, 那个通配仍可能留在 Nacos 上, 而它会放行
#      POST /opc/user/invitations/generate)
#   B) 覆盖: snippet 里带 method 前缀的条目 (GET:/x/**), 会顶掉远端同路径的
#      裸条目 (x/**) —— 裸条目对所有 method 放行, 正是匿名可写的根。
#   两条规则都只删【本地 snippet 明确点名的】条目, 运维手工加的其它条目一律保留。
#   重复执行幂等 (已实测 run1 == run2)。
#
# ⚠️ 为什么 snippet 必须是 ruoyi-gateway application.yml 白名单的【超集】:
#   Spring Cloud 里 Nacos 配置优先级高于 jar 内 application.yml。一旦 Nacos
#   存在 security.ignore.whites,jar 内那份 fallback 就被整体盖掉。所以
#   snippet 少一条 = 线上少一条, 而不是"回落到本地默认值"。
#   改网关白名单时, application.yml 和本文件必须一起改。
#   脚本每次跑都会做这个超集检查, 不通过直接退出。
#
# ⚠️ 顺序要求: 网关**先**支持白名单的 method 前缀语法, 再推这份带前缀的白名单。
#   反过来的话, 老网关看不懂 "GET:/opc/erp/product/**", 会当成普通路径,
#   该前缀下的公开读入口会一起被关掉。
# ============================================================

set -euo pipefail

# Windows console 默认 GBK, merge 的 diff 行含中文, 不设会被破坏成乱码。
# (与 deploy/scripts/health-check.sh 同一处理)
export PYTHONIOENCODING=utf-8

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
PYTHON_BIN="${PYTHON_BIN:-}"
# ⚠️ 命名空间必须带上!
#   网关从 opc-dev 命名空间读配置 (docker-compose.yml:
#   SPRING_CLOUD_NACOS_CONFIG_NAMESPACE: opc-dev)。
#   Nacos v1 API 不传 namespaceId 就落到 public 命名空间 —— 于是脚本"推成功了",
#   网关却根本读不到, 白名单改了等于没改。
#   (2026-10-24 发现: 这个脚本一直没传过 namespaceId。)
NACOS_NAMESPACE="${NACOS_NAMESPACE:-opc-dev}"

DATA_ID="application-${ENV}.yml"
SNIPPET_FILE="$(dirname "$0")/whitelist-opc-invite.yml"
# jar 内 fallback 白名单 —— 必须是 snippet 的子集 (见下方 superset 检查)
GATEWAY_APP_YML="$(cd "$(dirname "$0")/../.." && pwd)/ruoyi-gateway/src/main/resources/application.yml"

if [[ ! -f "$SNIPPET_FILE" ]]; then
  echo "[ERROR] 找不到 snippet: $SNIPPET_FILE"
  exit 1
fi

# ---------- 找一个装了 PyYAML 的 python ----------
# 合并要用 yaml 解析 Nacos 那份 application-{env}.yml。这台机器上默认的
# `python3` (miniconda base) **没装 PyYAML**, 写死 python3 会直接报
# ModuleNotFoundError —— 而报错发生在连 Nacos 之前, 看起来像"脚本坏了"。
# 所以: 优先用 PYTHON_BIN; 否则找第一个 import yaml 成功的解释器。
pick_python() {
  local candidates=()
  [[ -n "$PYTHON_BIN" ]] && candidates+=("$PYTHON_BIN")
  candidates+=(python3 python /usr/bin/python3)
  # conda 环境里常有装了 PyYAML 的解释器 (本机: envs/zhishiku)
  local c
  for c in /root/miniconda3/envs/*/bin/python /opt/conda/envs/*/bin/python; do
    [[ -x "$c" ]] && candidates+=("$c")
  done
  for c in "${candidates[@]}"; do
    if command -v "$c" >/dev/null 2>&1 && "$c" -c 'import yaml' >/dev/null 2>&1; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

if ! PY="$(pick_python)"; then
  echo "[ERROR] 找不到装了 PyYAML 的 python。"
  echo "        装一个:  pip install pyyaml"
  echo "        或指定:  PYTHON_BIN=/path/to/python $0 $ENV"
  exit 1
fi

echo "=========================================="
echo "[apply-whitelist] env      = $ENV"
echo "[apply-whitelist] dataId   = $DATA_ID"
echo "[apply-whitelist] nacos    = $NACOS_ADDR"
echo "[apply-whitelist] namespace= $NACOS_NAMESPACE"
echo "[apply-whitelist] snippet  = $SNIPPET_FILE"
echo "[apply-whitelist] python   = $PY"
echo "[apply-whitelist] dry_run  = $DRY_RUN"
echo "=========================================="

# ---------- 工具: yq 或 python 做 YAML 合并 ----------
merge_yaml() {
  local remote="$1"
  local patch="$2"
  "$PY" - "$remote" "$patch" <<'PY'
import sys, yaml

remote_path, patch_path = sys.argv[1], sys.argv[2]

def load(p):
    if p == "-" or not p:
        return {}
    with open(p, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}

remote = load(remote_path)
patch  = load(patch_path)

# 合并策略:security.ignore.whites 取【并集】, 不是整体替换。
#
# W80 修复 —— 旧实现是:
#     remote["security"]["ignore"]["whites"] = patch["security"]["ignore"]["whites"]
# 那是整体替换, 会静默抹掉 Nacos 里已有的【全部】白名单条目, 而当时
# whitelist-opc-invite.yml 里只有 13 条 (邀请 + RuoYi 既有)。实测会连带干掉:
#   - W48.5 收紧过的邀请正则 /opc/user/invitations/{code:[...]{8}}
#   - notification WebSocket 的 /opc/notification/ws*  (token 走 URL, 不走 Authorization 头)
#   - community / hr / erp / content / marketplace 的全部公开读入口
#   - 顶层 /login /logout /refresh /register /unlockscreen /captchaImage
# 结果 = 这些模块的公网入口一次性全部变成需登录, 且因为远端本来就没这条 key,
# 写回后本地 application.yml 的安全网 fallback 也被 Nacos 覆盖掉, 回滚很难。
#
# 现在: 远端顺序在前(保留运维手工加的条目), 本地补丁去重追加在后。
#
# ⚠️ 但纯并集只增不减, 有两个洞:
#   1) 条目被从 snippet 里【删掉】时, 它还留在 Nacos 上。
#      实例: W80 把 `/opc/user/invitations/*` 换成两条精确条目, 但并集不会删,
#      那个通配仍可能活着 —— 而它会放行 POST /opc/user/invitations/generate
#      (正是 W48.5 要堵的口)。
#   2) 白名单条目从裸路径换成带 method 前缀的写法 (GET:/x/**) 时,
#      旧的裸条目 (对所有 method 放行) 不会被顶掉, 加了 GET: 等于没加。
#
# 所以并集之外补两条规则:
#   A) 显式移除: snippet 里的 security.ignore.whitesRemove 列出的条目, 从远端删掉。
#   B) 覆盖: snippet 里带 method 前缀的条目 (GET:/x/**), 会顶掉远端同路径的裸条目。
#      —— 因为裸条目对所有 method 放行, 正是 3c 那个洞的根。
# 两条规则都只删【本地 snippet 明确点名的】条目, 运维手工加的其它条目一律保留。
#
# 注: whitesRemove 只被本脚本读取, 不会写回 Nacos —— 合并只写 security.ignore.whites。
#     所以它不需要出现在网关运行时的配置里, 也不需要 IgnoreWhiteProperties 加字段。

HTTP_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}


def split_entry(entry):
    """把白名单条目拆成 (method 或 None, path)。"""
    if not isinstance(entry, str):
        return (None, entry)
    colon = entry.find(":")
    if colon > 0:
        head = entry[:colon].strip().upper()
        if head in HTTP_METHODS:
            return (head, entry[colon + 1:].strip())
    return (None, entry)


existing = (remote.get("security") or {}).get("ignore", {}) or {}
existing = existing.get("whites") or []
if not isinstance(existing, list):
    print("WARN: 远端 security.ignore.whites 不是列表, 按空处理", file=sys.stderr)
    existing = []

incoming = patch["security"]["ignore"]["whites"]
remove = (patch["security"].get("ignore") or {}).get("whitesRemove") or []
remove_set = set(remove)

# --- 规则 A: 显式移除 ---
dropped_explicit = [e for e in existing if e in remove_set]
existing = [e for e in existing if e not in remove_set]

# --- 规则 B: 带 method 前缀的补丁条目顶掉远端同路径的裸条目 ---
scoped_paths = {path for (m, path) in map(split_entry, incoming) if m is not None}
dropped_superseded = [
    e for e in existing
    if split_entry(e)[0] is None and split_entry(e)[1] in scoped_paths
]
existing = [e for e in existing
            if not (split_entry(e)[0] is None and split_entry(e)[1] in scoped_paths)]

merged, seen = [], set()
for item in list(existing) + list(incoming):
    if item not in seen:
        seen.add(item)
        merged.append(item)

remote.setdefault("security", {}).setdefault("ignore", {})["whites"] = merged
print("[merge] whites: 远端 %d 条 -> 并集 %d 条 (新增 %d, 移除 %d = 显式 %d + 被覆盖 %d)"
      % (len(existing) + len(dropped_explicit) + len(dropped_superseded),
         len(merged), len(merged) - (len(existing) + len(dropped_explicit) + len(dropped_superseded)),
         len(dropped_explicit) + len(dropped_superseded),
         len(dropped_explicit), len(dropped_superseded)),
      file=sys.stderr)
if dropped_explicit:
    print("[merge] 显式移除: %s" % ", ".join(map(str, dropped_explicit)), file=sys.stderr)
if dropped_superseded:
    print("[merge] 被 method 前缀条目覆盖: %s" % ", ".join(map(str, dropped_superseded)), file=sys.stderr)

yaml.dump(remote, sys.stdout, default_flow_style=False, allow_unicode=True, sort_keys=False)
PY
}

# ---------- 超集检查: snippet 必须是 jar 内 fallback 的超集 ----------
#
# 为什么必须查: Spring Cloud 里 Nacos 配置优先级高于 jar 内 application.yml。
# Nacos 里一旦存在 security.ignore.whites, jar 内那份 fallback 就被**整体盖掉** ——
# 不是"逐条覆盖", 是整份换掉。所以 jar 里多写一条、Nacos 里少一条 = 线上直接少一条,
# 不会"回落到本地默认值"。
#
# 这条检查以前只是写在注释里(声称"已断言"), 实际没有代码。补上。
check_superset() {
  if [[ ! -f "$GATEWAY_APP_YML" ]]; then
    echo "[WARN] 找不到 jar 内 fallback: $GATEWAY_APP_YML —— 跳过超集检查"
    echo "       (在只部署了 deploy/ 的机器上跑是正常的)"
    return 0
  fi
  local missing
  missing="$("$PY" - "$GATEWAY_APP_YML" "$SNIPPET_FILE" <<'PY'
import sys, yaml

def whites_of(path):
    with open(path, "r", encoding="utf-8") as f:
        doc = yaml.safe_load(f) or {}
    node = (doc.get("security") or {}).get("ignore") or {}
    return node.get("whites") or []

jar = whites_of(sys.argv[1])
snippet = set(whites_of(sys.argv[2]))
missing = [e for e in jar if e not in snippet]
print("\n".join(str(m) for m in missing))
PY
)"
  if [[ -n "$missing" ]]; then
    echo "[ERROR] snippet 不是 jar 内 fallback 的超集 —— 以下条目会【只】存在于 jar 里,"
    echo "        而 Nacos 的 security.ignore.whites 会把 jar 那份整体盖掉 → 线上直接少这几条:"
    echo "$missing" | sed 's/^/          /'
    exit 1
  fi
  echo "[OK] 超集检查通过 (snippet ⊇ jar 内 fallback)"
}

# ---------- 1. 拉取远端现有配置 ----------
echo "[1/3] GET 远端 $DATA_ID ..."
REMOTE_TMP="$(mktemp).yml"
HTTP_CODE=$(curl -sS -o "$REMOTE_TMP" -w "%{http_code}" \
  --get "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "group=${NACOS_GROUP}" \
  --data-urlencode "namespaceId=${NACOS_NAMESPACE}" \
  --data-urlencode "username=${NACOS_USER}" \
  --data-urlencode "password=${NACOS_PASS}" || echo "000")

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "[WARN] 远端配置不存在或拉取失败(HTTP $HTTP_CODE),将以 snippet 为基准创建"
  REMOTE_TMP="$(mktemp).yml"; echo "" > "$REMOTE_TMP"
fi

# ---------- 2. 合并 ----------
echo "[2/3] 合并本地 snippet ..."
check_superset
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
  --data-urlencode "namespaceId=${NACOS_NAMESPACE}" \
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
