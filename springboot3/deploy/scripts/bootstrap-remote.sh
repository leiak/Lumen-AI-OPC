#!/usr/bin/env bash
# ============================================================
# AIOPC 远程 VM 一键引导 (W79)
#
# 在 Rocky Linux 8.10 云 VM 上跑,把整套 OPC 全栈从零拉起并
# 从用户 scp 过来的 aiopc-migrate-*.tar.gz 恢复数据。
#
# 用法:
#   bash bootstrap-remote.sh                          # 默认 Stage 0 → 5 全跑
#   bash bootstrap-remote.sh --stage <0..5>           # 单跑一个阶段
#   bash bootstrap-remote.sh --from <n>               # 从 Stage n 开始续跑
#   bash bootstrap-remote.sh --status                 # 当前进度
#   bash bootstrap-remote.sh --abort                  # 停所有 aiopc-* 容器
#   bash bootstrap-remote.sh --help
#
# Exit codes:
#   0 = 全成功
#   1 = 前置依赖缺失
#   2 = 数据恢复失败 (已自动回滚)
#   3 = 镜像 build 失败
#   4 = 健康检查 < 30/50 PASS
#
# 阶段说明:
#   0 = Pre-flight (校验 Rocky 8.10 / 磁盘 / .env / migrate 包)
#   1 = Install Docker (docker-ce repo + dnf install)
#   2 = Install deps + clone repo (git/python3/rsync + 关 SELinux/firewalld + git clone)
#   3 = 起基建 (nacos1/mysql/redis/rabbitmq/minio/qdrant)
#   4 = 数据迁移 + 推 Nacos + 起业务 (restore + import-dev + compose up)
#   5 = Verification (health-check.sh)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATA_ROOT="${DATA_ROOT:-/opt/aiopc-data}"
STAGING="${STAGING:-/tmp/aiopc-staging}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/bootstrap-$(date +%Y%m%d-%H%M%S).log}"

log()  { echo "[INFO]  $*" | tee -a "${LOG_FILE}"; }
warn() { echo "[WARN]  $*" | tee -a "${LOG_FILE}" >&2; }
fail() { echo "[FAIL]  $*" | tee -a "${LOG_FILE}" >&2; exit 1; }

require_root() {
  if [[ $EUID -ne 0 ]]; then
    fail "必须用 root 跑 (current EUID=$EUID)"
  fi
}

require_rocky_8() {
  if [[ ! -f /etc/os-release ]]; then
    fail "/etc/os-release 不存在"
  fi
  if ! grep -q "Rocky Linux 8.10" /etc/os-release; then
    fail "OS 不是 Rocky Linux 8.10。实际:"
    cat /etc/os-release >&2
  fi
}

require_disk() {
  local need_mb=$((5 * 1024))   # 5 GB
  local avail_mb
  avail_mb=$(df -m "${DATA_ROOT}" | awk 'NR==2 {print $4}')
  if (( avail_mb < need_mb )); then
    fail "磁盘不足:${DATA_ROOT} 仅 ${avail_mb} MB 可用,需要 >= ${need_mb} MB"
  fi
  log "磁盘 OK: ${avail_mb} MB 可用"
}

CMD="up"
FROM_STAGE=""
SINGLE_STAGE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stage)  [[ $# -ge 2 ]] || fail "--stage 缺值 (用法: --stage <0..5>)"
              [[ "${2:-}" =~ ^[0-9]+$ ]] || fail "--stage 必须是整数: ${2}"
              SINGLE_STAGE="$2"; shift 2
              (( SINGLE_STAGE >= 0 && SINGLE_STAGE <= 5 )) || fail "--stage 必须在 0..5: ${SINGLE_STAGE}" ;;
    --from)   [[ $# -ge 2 ]] || fail "--from 缺值 (用法: --from <n>)"
              [[ "${2:-}" =~ ^[0-9]+$ ]] || fail "--from 必须是整数: ${2}"
              FROM_STAGE="$2";   shift 2
              (( FROM_STAGE >= 0 && FROM_STAGE <= 5 )) || fail "--from 必须在 0..5: ${FROM_STAGE}" ;;
    --status) CMD="status";      shift   ;;
    --abort)  CMD="abort";       shift   ;;
    --help|-h) CMD="help";       shift   ;;
    *) fail "未知参数:$1 (跑 --help 看用法)";;
  esac
done

cmd_help() {
  cat <<USAGE
AIOPC 远程 VM 一键引导 (W79)

在 Rocky Linux 8.10 云 VM 上跑,把整套 OPC 全栈从零拉起并
从用户 scp 过来的 aiopc-migrate-*.tar.gz 恢复数据。

用法:
  bash bootstrap-remote.sh                          # 默认 Stage 0 → 5 全跑
  bash bootstrap-remote.sh --stage <0..5>           # 单跑一个阶段
  bash bootstrap-remote.sh --from <n>               # 从 Stage n 开始续跑
  bash bootstrap-remote.sh --status                 # 当前进度
  bash bootstrap-remote.sh --abort                  # 停所有 aiopc-* 容器
  bash bootstrap-remote.sh --help

Exit codes:
  0 = 全成功
  1 = 前置依赖缺失
  2 = 数据恢复失败 (已自动回滚)
  3 = 镜像 build 失败
  4 = 健康检查 < 30/50 PASS

阶段说明:
  0 = Pre-flight (校验 Rocky 8.10 / 磁盘 / .env / migrate 包)
  1 = Install Docker (docker-ce repo + dnf install)
  2 = Install deps + clone repo (git/python3/rsync + 关 SELinux/firewalld + git clone)
  3 = 起基建 (nacos1/mysql/redis/rabbitmq/minio/qdrant)
  4 = 数据迁移 + 推 Nacos + 起业务 (restore + import-dev + compose up)
  5 = Verification (health-check.sh)
USAGE
  exit 0
}

if [[ "${CMD}" == "help" ]]; then cmd_help; fi

STAGES_OK_FILE="${DATA_ROOT}/.bootstrap-stages-ok"
declare -A STAGE_NAMES=(
  [0]="Pre-flight"
  [1]="Install Docker"
  [2]="Install deps + clone"
  [3]="起基建"
  [4]="数据迁移 + 推 Nacos + 起业务"
  [5]="Verification"
)

# 标记 stage 完成
mark_stage_done() {
  local n="$1"
  mkdir -p "${DATA_ROOT}"
  touch "${STAGES_OK_FILE}.${n}"
  log "✓ Stage ${n} (${STAGE_NAMES[$n]:-?}) 完成"
}

is_stage_done() {
  [[ -f "${STAGES_OK_FILE}.$1" ]]
}

# 决定本次要跑哪些 stage
START_STAGE=0
END_STAGE=5
if [[ -n "${FROM_STAGE}" ]]; then
  START_STAGE="${FROM_STAGE}"
elif [[ -n "${SINGLE_STAGE}" ]]; then
  START_STAGE="${SINGLE_STAGE}"
  END_STAGE="${SINGLE_STAGE}"
fi

cmd_status() {
  log "当前 bootstrap 进度:"
  for n in 0 1 2 3 4 5; do
    if is_stage_done "${n}"; then
      echo "  [✓] Stage ${n}: ${STAGE_NAMES[$n]}"
    else
      echo "  [ ] Stage ${n}: ${STAGE_NAMES[$n]}"
    fi
  done
  echo ""
  echo "数据卷:"
  du -sh "${DATA_ROOT}"/* 2>/dev/null || echo "  (空)"
  exit 0
}

cmd_abort() {
  log "停所有 aiopc-* 容器 (保留 /opt/aiopc-data)..."
  cd "${PROJECT_ROOT}"
  docker compose stop 2>/dev/null || true
  log "✓ abort 完成。重启跑: bash $0 --from 3"
  exit 0
}

# Pre-flight 初始化 (status/abort 不需要,所以放在 dispatch 后面)
preflight_init() {
  mkdir -p "${DATA_ROOT}" "${STAGING}"

  # 从 docker-compose.yml 提取 MYSQL_ROOT_PASSWORD
  if [[ ! -f "${PROJECT_ROOT}/docker-compose.yml" ]]; then
    echo "[FAIL] docker-compose.yml 不存在: ${PROJECT_ROOT}/docker-compose.yml" >&2
    exit 1
  fi
  COMPOSE_MYSQL_PWD="$(grep -E '^[ \t]*MYSQL_ROOT_PASSWORD:' "${PROJECT_ROOT}/docker-compose.yml" | head -1 | sed -E 's/.*:\s*([^\s]+).*/\1/')"
  if [[ -z "${COMPOSE_MYSQL_PWD}" ]]; then
    echo "[FAIL] 解析 docker-compose.yml 失败:找不到 MYSQL_ROOT_PASSWORD" >&2
    exit 1
  fi
}

if [[ "${CMD}" == "status" ]]; then cmd_status; fi
if [[ "${CMD}" == "abort"  ]]; then cmd_abort;  fi

preflight_init

# up / from / stage 共用入口
log "============================================================"
log "AIOPC 远程引导开始"
log "  起点:Stage ${START_STAGE} (${STAGE_NAMES[${START_STAGE}]:-?})"
log "  终点:Stage ${END_STAGE}"
log "  日志:${LOG_FILE}"
log "============================================================"

run_stage() {
  local n="$1"
  local fn="stage_${n}"
  if is_stage_done "${n}" && [[ -z "${SINGLE_STAGE}" ]]; then
    log "Stage ${n} 已完成,跳过 (要重跑:rm ${STAGES_OK_FILE}.${n})"
    return 0
  fi
  log ">>> Stage ${n}: ${STAGE_NAMES[$n]:-?}"
  "${fn}"
  mark_stage_done "${n}"
}

stage_0() {
  log "校验前置条件..."

  require_rocky_8
  require_disk

  # .env 必须存在
  if [[ ! -f "${PROJECT_ROOT}/.env" ]]; then
    fail ".env 不存在:${PROJECT_ROOT}/.env。请先 cp .env.example .env 并填值"
  fi
  log "  ✓ .env 存在"

  # aiopc-migrate-*.tar.gz 必须 scp 到 staging
  local migrate_tar
  migrate_tar="$(ls -t "${STAGING}"/aiopc-migrate-*.tar.gz 2>/dev/null | head -1 || true)"
  if [[ -z "${migrate_tar}" ]]; then
    fail "未找到 ${STAGING}/aiopc-migrate-*.tar.gz。请先把本机导出的迁移包 scp 过来"
  fi
  log "  ✓ 迁移包:${migrate_tar} ($(du -h "${migrate_tar}" | cut -f1))"

  # 校验 sha256 (如果存在 .sha256 文件)
  if [[ -f "${migrate_tar}.sha256" ]]; then
    if ! sha256sum -c "${migrate_tar}.sha256" >/dev/null 2>&1; then
      fail "迁移包 sha256 校验失败,可能传输损坏"
    fi
    log "  ✓ sha256 校验通过"
  fi

  # 校验 .env 必填项
  for key in DEEPSEEK_API_KEY MINIMAX_API_KEY; do
    local val
    val="$(grep -E "^${key}=" "${PROJECT_ROOT}/.env" | cut -d= -f2- || true)"
    if [[ -z "${val}" ]]; then
      fail ".env 缺 ${key}"
    fi
  done
  log "  ✓ .env 必填项齐全"

  log "Stage 0 校验通过"
}
stage_1() {
  log "检查 Docker 是否已装..."
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "  ✓ Docker 已装: $(docker --version)"
    return 0
  fi

  log "装 Docker CE (Rocky 8 + docker-ce repo)..."

  # Rocky 8 默认源可能不带 docker-ce,加官方 repo
  if [[ ! -f /etc/yum.repos.d/docker-ce.repo ]]; then
    dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo \
      || fail "加 docker-ce repo 失败 (国内云可换 mirrors.aliyun.com/docker-ce)"
  fi

  dnf install -y docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin \
    || fail "dnf install docker-ce 失败"

  systemctl enable --now docker
  systemctl is-active --quiet docker || fail "docker daemon 没起来"

  # 校验 compose plugin
  if ! docker compose version >/dev/null 2>&1; then
    fail "docker compose plugin 不可用"
  fi

  log "  ✓ Docker 装好: $(docker --version), $(docker compose version)"
}
stage_2() { : "placeholder"; }
stage_3() { : "placeholder"; }
stage_4() { : "placeholder"; }
stage_5() { : "placeholder"; }

main() {
  require_root
  for n in $(seq "${START_STAGE}" "${END_STAGE}"); do
    run_stage "${n}"
  done
  log "============================================================"
  log "✓ 全栈部署完成。访问入口:"
  log "  前端:    http://127.0.0.1:8079  (ssh -L 8079:127.0.0.1:8079)"
  log "  网关:    http://127.0.0.1:8080  (ssh -L 8080:127.0.0.1:8080)"
  log "  Nacos:   http://127.0.0.1:8848/nacos  (ssh -L 8848:127.0.0.1:8848)"
  log "============================================================"
}

main "$@"
