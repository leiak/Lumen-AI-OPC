# ============================================================
# Flyway 数据库迁移脚本 V1
# OPC 业务表初始化（2026-09-03）
# ============================================================
# 该脚本由 Flyway 自动加载（首次部署时）
# 完整表结构见 sql/opc_20260903.sql
# ============================================================

-- 注：完整 DDL 见 sql/opc_20260903.sql
-- 该 Flyway 版本仅作为迁移框架的占位版本，
-- 真实 DDL 在 deploy 阶段执行 sql/opc_20260903.sql

-- Flyway 历史表
CREATE TABLE IF NOT EXISTS `flyway_schema_history` (
  `installed_rank` INT NOT NULL,
  `version` VARCHAR(50) DEFAULT NULL,
  `description` VARCHAR(200) DEFAULT NULL,
  `type` VARCHAR(20) NOT NULL,
  `script` VARCHAR(1000) NOT NULL,
  `checksum` INT DEFAULT NULL,
  `installed_by` VARCHAR(100) NOT NULL,
  `installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` INT NOT NULL,
  `success` TINYINT(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `idx_succeeded` (`success`),
  KEY `idx_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 此版本仅初始化 flyway 历史表
-- 后续版本按 V2__xxx.sql、V3__xxx.sql 命名

INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES (1, '1', 'init flyway schema history', 'SQL', 'V1__init_opc_schema.sql', NULL, 'admin', 100, 1)
ON DUPLICATE KEY UPDATE installed_rank = installed_rank;
