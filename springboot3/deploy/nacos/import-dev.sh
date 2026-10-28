#!/usr/bin/env bash
# ============================================================
# Nacos 批量导入 OPC dev 命名空间配置
#
# 命名空间：opc-dev
# 用法：
#   cd springboot3/deploy/nacos
#   ./import-dev.sh
#
# 默认连本地 127.0.0.1:8848（不需 auth）。远程：
#   NACOS_ADDR=nacos.example.com:8848 ./import-dev.sh
#
# ⚠️ 这里有一个踩过很多次的坑，脚本头再写一遍：
#
#   Nacos **配置** API 的命名空间参数叫 `tenant`，不叫 `namespaceId`。
#   `namespaceId=` 会被服务端**静默忽略**，回落到默认 tenant('')——
#   于是「推送」和「回读」都落在 public 栏，md5 还一模一样，
#   脚本一路 [OK]，但配置压根没进 opc-dev 命名空间。
#   （`namespaceId` 是**命名空间管理** API 的参数，create-namespace.sh 用的是它。）
#
#   服务端按 tenant=opc-dev 拉配置。所以本脚本推送和回读**都**用 tenant=，
#   回读不到就是真没进去，直接报错，不再假装成功。
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

# 配置类型: 按文件名后缀定。
#
# 只有一份是 json —— sentinel-ruoyi-gateway.json 是网关的 Sentinel 数据源
# (gw-flow 流控规则)读的那张表, Sentinel 那边写死 data-type: json。
# Nacos 里的 type 只是控制台编辑器的元数据, 推错类型不影响 Sentinel 解析,
# 但控制台里用 JSON 编辑器打开一份标成 yaml 的规则表是很别扭的。
type_of() {
  case "$1" in
    *.json) echo "json" ;;
    *)      echo "${TYPE}" ;;
  esac
}

# 仓库文件名 → Nacos DataID
#
# 绝大多数情况下两者同名。但 Sentinel 的流控规则表是**例外**: 网关的
# bootstrap.yml 里写死了 dataId: sentinel-ruoyi-gateway —— 不带扩展名。
# 仓库里的文件叫 .json 只是为了让编辑器把它当 JSON 打开。
#
# 这里要是图省事直接把文件名当 DataID 推上去, Nacos 里就会多出一份
# sentinel-ruoyi-gateway.json, 而网关找的是 sentinel-ruoyi-gateway ——
# 规则永远读不到, 而配置中心那几节还都显示"正常"。
dataid_of() {
  case "$1" in
    sentinel-ruoyi-gateway.json) echo "sentinel-ruoyi-gateway" ;;
    *) echo "$1" ;;
  esac
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# dev 命名空间里实际存在的 DataID
#
# 三个来源都被各服务 bootstrap.yml 的 spring.config.import 拉取：
#   application-dev.yml      全局共享
#   opc-common-dev.yml       跨服务通用
#   opc-<svc>-dev.yml        单服务私有
#
# 只有真正存在的文件才在这里 —— 曾经有个 opc-common-dev.yml 的 import 一直空转
# （文件根本不存在），现在补上了。
#
# opc-ai-core / opc-agent-hub / opc-user-center / opc-billing / opc-finance
# 没有各自的 dev 文件：这几个服务的配置（ai.* / langchain4j.* / qdrant.* 等）
# 已经由 application-dev.yml 覆盖，而它们本地 application.yml 里的值与之一致。
NAMES=(
  "application-dev.yml"
  "opc-common-dev.yml"
  "opc-notification-dev.yml"
  "opc-crm-dev.yml"
  "opc-community-dev.yml"
  "opc-marketplace-dev.yml"
  "opc-hr-dev.yml"
  "opc-erp-dev.yml"
  "opc-content-dev.yml"
  # 网关 Sentinel 的流控规则表 —— 唯一一份**不走 config.import** 的配置:
  # Sentinel 客户端自己按 dataId=sentinel-ruoyi-gateway 去 Nacos 取,
  # groupId=DEFAULT_GROUP, namespace=opc-dev, data-type=json。
  # 内容是 [] = 目前一条规则都没有(不是"忘了填",是真的没有)。
  "sentinel-ruoyi-gateway.json"
)

# ---------- 单文件推送（Python urllib + content 走 stdin）----------
import_one() {
  local name="$1"
  local path="${SCRIPT_DIR}/${name}"
  if [[ ! -f "${path}" ]]; then
    echo "[FAIL] 缺少文件：${path}" >&2
    return 1
  fi
  local ftype
  ftype="$(type_of "${name}")"
  local dataid
  dataid="$(dataid_of "${name}")"
  echo "[INFO] 导入 ${name} → dataId=${dataid} (group=${GROUP}, tenant=${NAMESPACE_ID}, type=${ftype})"
  local resp
  resp=$(python -c "
import urllib.request, urllib.parse, sys
content = sys.stdin.buffer.read().decode('utf-8')
data = urllib.parse.urlencode({
    'dataId': '${dataid}',
    'group': '${GROUP}',
    'tenant': '${NAMESPACE_ID}',
    'type': '${ftype}',
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

# ---------- 验证（按 tenant= 回读，与仓库文件逐行比对）----------
#
# 这一步以前是**假阳性**：回读也用 namespaceId=，于是读回来的是 public 栏那份
# （推送时被回落过去的同一份），md5 当然一致。现在按 tenant= 读，
# 读不到就是没进去 —— 直接失败，不再 `[WARN]` 完事。
verify_one() {
  local name="$1"
  local path="${SCRIPT_DIR}/${name}"
  local dataid
  dataid="$(dataid_of "${name}")"
  python -c "
import urllib.request, hashlib, sys
name = '${name}'
dataid = '${dataid}'
url = ('${PROTOCOL}://${NACOS_ADDR}/nacos/v1/cs/configs?dataId=' + dataid
       + '&group=${GROUP}&tenant=${NAMESPACE_ID}')
try:
    r = urllib.request.urlopen(url, timeout=10)
    nacos = r.read().decode('utf-8')
except Exception as e:
    print('  [FAIL] ' + name + ' (dataId=' + dataid + ') 回读失败 (tenant=${NAMESPACE_ID}): ' + str(e))
    sys.exit(1)
if nacos.strip() in ('config data not exist', ''):
    print('  [FAIL] ' + name + ' (dataId=' + dataid + ') 在 tenant=${NAMESPACE_ID} 下不存在 —— 推送没进去')
    sys.exit(1)
repo = sys.stdin.buffer.read().decode('utf-8')
# Nacos 服务端会标准化行尾, 用 splitlines 归一化
nacos_norm = '\n'.join(nacos.splitlines())
repo_norm = '\n'.join(repo.splitlines())
nacos_h = hashlib.md5(nacos_norm.encode('utf-8')).hexdigest()[:10]
repo_h = hashlib.md5(repo_norm.encode('utf-8')).hexdigest()[:10]
if nacos_norm == repo_norm:
    print('  [OK  ] ' + name + ' (dataId=' + dataid + ', tenant=${NAMESPACE_ID}, md5=' + repo_h + ', ' + str(len(repo)) + 'B)')
else:
    print('  [FAIL] ' + name + ' 内容已漂移! nacos=' + nacos_h + ' repo=' + repo_h)
    for i, (a, b) in enumerate(zip(nacos_norm, repo_norm)):
        if a != b:
            ctx_start = max(0, i-20)
            print('         首个差异 @ char ' + str(i) + ':')
            print('           nacos: ...' + repr(nacos_norm[ctx_start:i+20]))
            print('           repo : ...' + repr(repo_norm[ctx_start:i+20]))
            break
    sys.exit(1)
" < "${path}"
}

main() {
  # 可选：命令行指定只推这几个文件（灰度用）。
  #   ./import-dev.sh                      → 全量
  #   ./import-dev.sh opc-common-dev.yml   → 只推这一个
  local names=("${NAMES[@]}")
  if [[ $# -gt 0 ]]; then
    names=("$@")
  fi

  echo "=================================================="
  echo "OPC dev Nacos 配置批量导入"
  echo "  地址   : ${PROTOCOL}://${NACOS_ADDR}"
  echo "  命名空间: ${NAMESPACE_ID}  (tenant= 参数，不是 namespaceId=)"
  echo "  文件   : ${#names[@]}"
  echo "=================================================="

  # 命名空间必须先在 tenant_info 里真实存在，否则控制台看不见这一栏
  "${SCRIPT_DIR}/create-namespace.sh" || {
    echo "[FAIL] 命名空间创建失败，中止导入" >&2
    exit 1
  }
  echo ""

  local failed=0
  for n in "${names[@]}"; do
    if ! import_one "${n}"; then
      failed=$((failed + 1))
    fi
  done

  echo "--------------------------------------------------"
  echo "导入完成，推送失败数：${failed} / ${#names[@]}"
  echo ""
  echo "验证阶段（按 tenant= 回读，逐行比对）："
  local drift=0
  for n in "${names[@]}"; do
    if ! verify_one "${n}"; then
      drift=$((drift + 1))
    fi
  done

  if [[ "${failed}" -gt 0 || "${drift}" -gt 0 ]]; then
    echo ""
    echo "[FAIL] 推送失败 ${failed} 个 / 校验不通过 ${drift} 个 —— 配置**没有**全部落进 ${NAMESPACE_ID}" >&2
    exit 1
  fi
  echo ""
  echo "[OK] ${#names[@]} 份配置全部落在 tenant=${NAMESPACE_ID}，且与仓库文件逐行一致"
}

main "$@"
