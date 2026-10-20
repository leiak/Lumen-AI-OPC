-- =============================================================================
-- V20261021__tenant_company_lookup_index.sql
-- 登录期租户解析：确保 LoginUser.companyId 的反查索引存在
--
-- 背景：本次改动在**登录时**（ruoyi-system 的 SysUserController#info）按 userId
--       反查所属公司，把结果写进 LoginUser.companyId 随 token 下发。查询打的是
--       opc_company_member.user_id。
--       该表由 deploy/mysql-initdb.d/05-opc-main-schema.sql 创建（含 idx_user_id），
--       但 sql/flyway/V1__init_opc_schema.sql 里没有这两张公司表 —— 走 Flyway 初始化
--       的环境可能压根没有它们。因此这里只做「有则确保索引、无则跳过」，
--       不负责建表（建表仍以 05-opc-main-schema.sql / z_opc_20260903.sql 为准）。
--
-- 目标库：OPC 业务库（opc_company_member 所在的 schema，与 sys_user 同库）
-- 执行方式：Flyway 未在 ruoyi-system 启用，需运维手动执行
--     mysql -u<user> -p <opc_db> < sql/migrations/V20261021__tenant_company_lookup_index.sql
--
-- 幂等性：MySQL 8 不支持 CREATE INDEX IF NOT EXISTS（那是 MariaDB 语法），
--         沿用 V20260905__workflow_cron.sql 的 information_schema 探测 + 动态 SQL。
--         表不存在时整段跳过，可重复执行。
-- =============================================================================

DROP PROCEDURE IF EXISTS opc_v20261021_migrate;

DELIMITER $$

CREATE PROCEDURE opc_v20261021_migrate()
BEGIN
    -- 表不存在则什么都不做（公司表由 05-opc-main-schema.sql 负责创建）
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'opc_company_member') THEN

        -- 登录期按 user_id 反查默认公司，idx_user_id 是这条查询的驱动索引
        IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE()
                         AND TABLE_NAME = 'opc_company_member'
                         AND INDEX_NAME = 'idx_user_id') THEN
            ALTER TABLE `opc_company_member` ADD INDEX `idx_user_id` (`user_id`);
        END IF;

    END IF;

    -- 关联 opc_company_profile 走主键，无需额外索引；此处仅在 profile 表存在时校验
    -- user_id 上的成员唯一性不受影响（唯一键是 uk_company_user = (company_id, user_id)，
    -- 允许多对多，所以登录期可能命中多行，取「OWNER 优先 + 最小 id」保证结果稳定）。
END$$

DELIMITER ;

CALL opc_v20261021_migrate();

DROP PROCEDURE IF EXISTS opc_v20261021_migrate;