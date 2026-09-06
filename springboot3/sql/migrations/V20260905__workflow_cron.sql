-- =============================================================================
-- V20260905__workflow_cron.sql
-- Task #3 Sub-task 3.2：opc_agent_workflow 增加 cron 调度所需的 3 个字段
--
-- 目标库：OPC 业务库（opc_agent_workflow 所在的 schema）
-- 执行方式：Flyway 尚未在 opc-agent-hub 启用，需运维手动执行
--     mysql -u<user> -p <opc_db> < sql/migrations/V20260905__workflow_cron.sql
--
-- 幂等性：MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（那是 MariaDB
--         语法），因此这里用 information_schema 探测 + 动态 SQL 实现，可重复执行。
-- =============================================================================

DROP PROCEDURE IF EXISTS opc_v20260905_migrate;

DELIMITER $$

CREATE PROCEDURE opc_v20260905_migrate()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'opc_agent_workflow'
                     AND COLUMN_NAME = 'cron_expression') THEN
        ALTER TABLE `opc_agent_workflow`
            ADD COLUMN `cron_expression` VARCHAR(50) DEFAULT NULL
            COMMENT 'Quartz cron 表达式，trigger_type=CRON 时生效' AFTER `enabled`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'opc_agent_workflow'
                     AND COLUMN_NAME = 'timezone') THEN
        ALTER TABLE `opc_agent_workflow`
            ADD COLUMN `timezone` VARCHAR(20) NOT NULL DEFAULT 'Asia/Shanghai'
            COMMENT 'cron 求值时区' AFTER `cron_expression`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'opc_agent_workflow'
                     AND COLUMN_NAME = 'next_run_at') THEN
        ALTER TABLE `opc_agent_workflow`
            ADD COLUMN `next_run_at` DATETIME DEFAULT NULL
            COMMENT '下次预计执行时间（由 cron_expression 推算）' AFTER `timezone`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'opc_agent_workflow'
                     AND INDEX_NAME = 'idx_next_run_at') THEN
        ALTER TABLE `opc_agent_workflow` ADD INDEX `idx_next_run_at` (`next_run_at`);
    END IF;
END$$

DELIMITER ;

CALL opc_v20260905_migrate();

DROP PROCEDURE IF EXISTS opc_v20260905_migrate;
