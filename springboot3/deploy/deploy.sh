#!/usr/bin/env bash
# ============================================================
# AIOPC 一键全栈 deploy (W50 固化)
#
# 用法:
#   cd springboot3/deploy
#   bash deploy.sh                          # 拉起完整 aiopc stack (mysql + nacos + 业务容器)
#   bash deploy.sh status                   # 容器清单
#   bash deploy.sh stop                     # 停所有容器,保留 host 数据
#   bash deploy.sh nuke                     # ⚠️ 删 mysql/redis 数据卷重新初始化
#   bash deploy.sh rebuild crm              # 重建镜像(改完 OPC 源码后)
#   bash deploy.sh rebuild all              # 重建所有 aiopc-* 镜像
#   bash deploy.sh snapshot                 # 跑状态快照
#   bash deploy.sh health                   # 跑健康检查
#
# 设计:
#   - Windows git-bash 兼容 (forward slash 路径 + Python 替 curl)
#   - 失败立即退出 (set -euo pipefail)
#   - .env 缺失时友好提示,不强行复制
#   - 镜像 build 用 mvn 在 springboot3/ 跑,不在 deploy/ 里
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${PROJECT_ROOT}/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-C:/Program Files/Java/jdk-17.0.17.10-hotspot}"

CMD="${1:-help}"
if [[ $# -gt 0 ]]; then
  shift
fi

# ---------- helpers ----------
log()  { echo "[INFO]  $*"; }
warn() { echo "[WARN]  $*" >&2; }
fail() { echo "[FAIL]  $*" >&2; exit 1; }

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    fail "docker 未安装,请先装 Docker Desktop 24+"
  fi
  if ! docker info >/dev/null 2>&1; then
    fail "docker daemon 没在跑,启动 Docker Desktop 后重试"
  fi
}

check_env_file() {
  if [[ ! -f "${SCRIPT_DIR}/.env" ]]; then
    if [[ -f "${SCRIPT_DIR}/.env.example" ]]; then
      warn ".env 不存在,请先: cp .env.example .env && 编辑填真值"
      warn "  必填: DEEPSEEK_API_KEY + MINIMAX_API_KEY"
      if [[ -t 0 ]]; then
        read -p "  按 Enter 继续(非交互环境会跳过): "
      fi
    else
      fail ".env 和 .env.example 都不存在"
    fi
  fi
}

wait_mysql_ready() {
  log "等 MySQL 起来 (initdb 约 30-60s)..."
  local tries=60
  while (( tries > 0 )); do
    if docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' \
        -e "SELECT 1" >/dev/null 2>&1; then
      log "  MySQL OK"
      return 0
    fi
    sleep 2
    tries=$((tries - 1))
  done
  fail "MySQL 60s 内未就绪,查看 docker logs aiopc-mysql"
}

wait_nacos_ready() {
  log "等 Nacos 起来..."
  local tries=30
  while (( tries > 0 )); do
    if curl -sf -m 3 http://127.0.0.1:8848/nacos/v1/cs/health >/dev/null 2>&1; then
      log "  Nacos OK"
      return 0
    fi
    sleep 2
    tries=$((tries - 1))
  done
  fail "Nacos 60s 内未就绪,查看 docker logs aiopc-nacos-1"
}

# ---------- subcommands ----------

cmd_up() {
  require_docker
  check_env_file

  log "启动基建 (nacos + mysql + redis + 其他)..."
  cd "${SCRIPT_DIR}"
  docker compose up -d nacos1 mysql redis rabbitmq qdrant minio elasticsearch 2>&1 | tail -5
  wait_mysql_ready
  wait_nacos_ready

  log "推送 Nacos dev 配置..."
  cd "${SCRIPT_DIR}/nacos"
  ./import-dev.sh

  log "启动业务容器..."
  cd "${SCRIPT_DIR}"
  docker compose up -d 2>&1 | tail -5

  log ""
  log "=================================================="
  log " aiopc stack 已拉起 (或已运行)"
  log "=================================================="
  log "  - 前端:    http://localhost:8079"
  log "  - 网关:    http://localhost:8080"
  log "  - Nacos:   http://localhost:8848/nacos"
  log ""
  log " 跑 bash deploy.sh health 验证"
}

cmd_status() {
  require_docker
  cd "${SCRIPT_DIR}"
  docker compose ps
}

cmd_stop() {
  require_docker
  cd "${SCRIPT_DIR}"
  docker compose stop
  log "所有 aiopc-* 容器已停 (host 数据保留)"
}

cmd_nuke() {
  require_docker
  warn "⚠️  将删除以下 host 数据: deploy/mysql/data deploy/redis/data deploy/nacos/conf"
  warn "    (其它容器数据如 qdrant/minio/es 不动)"
  if [[ -t 0 ]]; then
    read -p "  确认? 输入 yes 继续: " ans
    [[ "${ans}" == "yes" ]] || { log "已取消"; exit 0; }
  fi
  cd "${SCRIPT_DIR}"
  docker compose down -v aiopc-mysql aiopc-redis aiopc-nacos-1
  rm -rf mysql/data/* redis/data/* nacos/conf/* 2>/dev/null || true
  log "数据卷已清空,跑 'bash deploy.sh up' 重建"
}

cmd_rebuild() {
  require_docker
  local target="${1:-}"
  if [[ -z "${target}" ]]; then
    fail "用法: deploy.sh rebuild <svc-name|all>     (e.g. crm / notification / ai-core / all)"
  fi

  build_one() {
    local module="$1"
    local svc_dir="${PROJECT_ROOT}/ruoyi-modules/${module}"
    if [[ ! -d "${svc_dir}" ]]; then
      warn "  模块 ${module} 不存在,跳过"
      return
    fi
    log "[${module}] mvn clean package + copy-dependencies"
    cd "${PROJECT_ROOT}"
    JAVA_HOME="${JAVA_HOME}" \
      mvn -pl "ruoyi-modules/${module}" -am clean package \
        -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -q
    JAVA_HOME="${JAVA_HOME}" \
      mvn -pl "ruoyi-modules/${module}" dependency:copy-dependencies \
        -DoutputDirectory=target/dependency -q

    log "[${module}] docker build aiopc-${module}"
    cd "${SCRIPT_DIR}"
    docker compose build "aiopc-${module}"
    log "[${module}] restart"
    docker compose up -d "aiopc-${module}"
    log "[${module}] OK"
  }

  if [[ "${target}" == "all" ]]; then
    for m in opc-crm opc-notification opc-ai-core opc-user-center \
             opc-agent-hub opc-billing opc-finance opc-insight; do
      build_one "${m}"
    done
  else
    build_one "${target}"
  fi
  log "rebuild 完成,跑 bash scripts/health-check.sh 验证"
}

cmd_snapshot() {
  cd "${SCRIPT_DIR}/scripts"
  bash snapshot-stack.sh
}

cmd_health() {
  cd "${SCRIPT_DIR}/scripts"
  bash health-check.sh
}

cmd_help() {
  cat <<USAGE
AIOPC 一键全栈 deploy (W50 固化)

用法: bash deploy.sh <command> [args]

Commands:
  up                       拉起完整 aiopc stack (mysql + nacos + 业务容器, idempotent)
  status                   docker compose ps
  stop                     停所有容器,保留 host 数据
  nuke                     ⚠️  清 mysql/redis/nacos 数据卷
  rebuild <svc|all>        重建指定服务镜像 (e.g. crm / notification / all)
  snapshot                 跑全栈状态快照
  health                   跑健康检查 (36+ 项)
  help                     显示本帮助

示例:
  bash deploy.sh                       # -> 等价 deploy.sh help
  bash deploy.sh up
  bash deploy.sh rebuild crm
  bash deploy.sh rebuild all
  bash deploy.sh snapshot
  bash deploy.sh health
USAGE
}

# ---------- dispatch ----------
case "${CMD}" in
  help|-h|--help) cmd_help ;;
  up)        cmd_up ;;
  status)    cmd_status ;;
  stop)      cmd_stop ;;
  nuke)      cmd_nuke ;;
  rebuild)   cmd_rebuild "$@" ;;
  snapshot)  cmd_snapshot ;;
  health)    cmd_health ;;
  *)
    cmd_help
    exit 1
    ;;
esac