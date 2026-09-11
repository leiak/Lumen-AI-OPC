#!/usr/bin/env bash
# ============================================================
# OPC MySQL 数据备份 / 恢复 (W48.6 固化)
#
# 用法：
#   ./backup-mysql.sh                        # 备份到 ./backups/aiopc-mysql-YYYYMMDD-HHMMSS.sql
#   ./backup-mysql.sh restore <file>         # 恢复（覆盖现有数据！）
#   ./backup-mysql.sh list                   # 列出所有备份
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${DEPLOY_DIR}/backups"
CONTAINER="aiopc-mysql"
ROOT_PWD="Opc@2026!"
DB="ry-vue-opc"

mkdir -p "${BACKUP_DIR}"

cmd="${1:-backup}"

case "${cmd}" in
  backup)
    ts=$(date +%Y%m%d-%H%M%S)
    out="${BACKUP_DIR}/aiopc-mysql-${ts}.sql.gz"
    echo "[INFO] 备份 ${DB} -> ${out}"
    docker exec "${CONTAINER}" mysqldump -uroot -p"${ROOT_PWD}" \
      --single-transaction --routines --triggers --events \
      --add-drop-database --databases "${DB}" \
      2>/dev/null | gzip > "${out}"
    size=$(ls -la "${out}" | awk '{print $5}')
    echo "[OK]   ${out} (${size} bytes)"
    echo ""
    echo "[INFO] 备份目录总大小:"
    du -sh "${BACKUP_DIR}" 2>/dev/null
    echo ""
    echo "[INFO] 旧备份清理 (>30 天):"
    find "${BACKUP_DIR}" -name "aiopc-mysql-*.sql.gz" -mtime +30 -delete -print
    ;;

  restore)
    file="${2:?Usage: $0 restore <backup-file>}"
    if [[ ! -f "${file}" ]]; then
      echo "[FAIL] 备份文件不存在：${file}" >&2
      exit 1
    fi
    echo "[WARN] 将覆盖 ${DB} 当前数据！"
    read -p "继续？(yes/no) " ans
    if [[ "${ans}" != "yes" ]]; then
      echo "[ABORT]"
      exit 1
    fi
    echo "[INFO] 恢复 ${file} -> ${DB}"
    case "${file}" in
      *.gz) gunzip -c "${file}" | docker exec -i "${CONTAINER}" mysql -uroot -p"${ROOT_PWD}" ;;
      *)    cat "${file}" | docker exec -i "${CONTAINER}" mysql -uroot -p"${ROOT_PWD}" ;;
    esac
    echo "[OK]   恢复完成"
    ;;

  list)
    ls -lht "${BACKUP_DIR}"/aiopc-mysql-*.sql.gz 2>/dev/null || echo "(无备份)"
    ;;

  list-tables)
    # 列出当前 DB 下所有表 + 关键业务表行数 (W50 固化:crm + notification)
    echo "[INFO] ${DB} 表清单:"
    docker exec "${CONTAINER}" mysql -uroot -p"${ROOT_PWD}" -N -B \
      -e "SELECT CONCAT(TABLE_SCHEMA,'.',TABLE_NAME) AS tbl, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB}' ORDER BY TABLE_ROWS DESC" 2>/dev/null
    echo ""
    echo "[INFO] 关键业务表行数:"
    for t in opc_crm_customer opc_crm_contact opc_crm_follow_up opc_crm_opportunity \
             opc_crm_contract opc_crm_order \
             opc_notification_email_log opc_notification_sms_log \
             opc_notification_template opc_notification_inbox; do
      cnt=$(docker exec "${CONTAINER}" mysql -uroot -p"${ROOT_PWD}" -N -B \
        -e "SELECT COUNT(*) FROM ${DB}.${t}" 2>/dev/null | tr -d '[:space:]')
      printf "  %-32s %s\n" "${t}" "${cnt:-N/A}"
    done
    ;;

  *)
    echo "Usage: $0 {backup|restore <file>|list|list-tables}" >&2
    exit 1
    ;;
esac
