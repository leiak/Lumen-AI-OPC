#!/usr/bin/env bash
# ============================================================
# AIOPC 本机迁移包导出 (W79)
#
# 在用户本机 (Windows git-bash / Mac / Linux) 跑,打包 MySQL dump
# + Nacos 配置 → aiopc-migrate-<date>.tar.gz,供 scp 到远程 VM。
#
# 用法:
#   bash backup-mysql-local.sh                  # 默认输出到 /tmp/aiopc-migrate-YYYYMMDD-HHMMSS.tar.gz
#   bash backup-mysql-local.sh /path/to/out.tar.gz
#
# 依赖:
#   - 本机 Docker Desktop / docker engine 在跑
#   - aiopc-mysql 容器存在且 healthy
#   - nacos/*.yml 在 springboot3/deploy/nacos/ 下
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
NACOS_DIR="${DEPLOY_DIR}/nacos"
CONTAINER="aiopc-mysql"
ROOT_PWD="Opc@2026!"
DB="ry-vue-opc"

OUT_FILE="${1:-/tmp/aiopc-migrate-$(date +%Y%m%d-%H%M%S).tar.gz}"
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "${STAGE_DIR}"' EXIT

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

require_mysql_container() {
  if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
    fail "容器 ${CONTAINER} 不存在或未运行。请先在本地跑 deploy.sh up"
  fi
}

require_nacos_dir() {
  if [[ ! -d "${NACOS_DIR}" ]] || [[ -z "$(ls -A "${NACOS_DIR}/"*.yml 2>/dev/null)" ]]; then
    fail "Nacos 配置目录为空: ${NACOS_DIR}"
  fi
}

export_mysql_dump() {
  log "导出 MySQL dump from ${CONTAINER}.${DB} ..."
  docker exec "${CONTAINER}" mysqldump -uroot -p"${ROOT_PWD}" \
    --default-character-set=utf8mb4 --routines --triggers --events \
    --single-transaction \
    --databases "${DB}" \
    2>/dev/null | gzip > "${STAGE_DIR}/mysql-dump.sql.gz"
  local size
  size=$(du -h "${STAGE_DIR}/mysql-dump.sql.gz" | cut -f1)
  log "  ✓ mysql-dump.sql.gz (${size})"
}

export_nacos_configs() {
  log "打包 Nacos dev/prod 配置 from ${NACOS_DIR} ..."
  cd "${NACOS_DIR}"
  tar -czf "${STAGE_DIR}/nacos-configs.tgz" *.yml
  local size
  size=$(du -h "${STAGE_DIR}/nacos-configs.tgz" | cut -f1)
  log "  ✓ nacos-configs.tgz (${size})"
  cd - >/dev/null
}

build_migrate_tar() {
  log "打包最终迁移包 -> ${OUT_FILE}"
  tar -czf "${OUT_FILE}" -C "${STAGE_DIR}" .
  sha256sum "${OUT_FILE}" > "${OUT_FILE}.sha256"
  local size
  size=$(du -h "${OUT_FILE}" | cut -f1)
  log "  ✓ ${OUT_FILE} (${size})"
  log "  ✓ checksum: ${OUT_FILE}.sha256"
}

main() {
  require_docker
  require_mysql_container
  require_nacos_dir

  export_mysql_dump
  export_nacos_configs
  build_migrate_tar

  cat <<NEXT

============================================================
✓ 迁移包已生成:

  $(realpath "${OUT_FILE}")

下一步:
  scp ${OUT_FILE} root@<VM_IP>:/tmp/aiopc-staging/
  (在 VM 上) bash /opt/aiopc/springboot3/deploy/scripts/bootstrap-remote.sh
============================================================
NEXT
}

main "$@"
