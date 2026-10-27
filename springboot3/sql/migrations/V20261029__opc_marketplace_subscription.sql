-- ============================================================
-- V20261029__opc_marketplace_subscription.sql
--
-- 订阅表 —— 让 mp_publication.subscription_count 有真实来源
--
-- 背景:
--   subscription_count 是商品卡上唯一**完全没有写入路径**的数字。
--   MpPublicationMapper#incrementSubscriptionCount 这个方法存在,
--   但零调用方 —— 订阅这个产品动作从来没被实现过。
--   于是一个数在页面上展示着, 却不对应任何一条真实记录。
--
-- 本文件补上那张表。幂等: 可重复执行(存储过程判存在, MySQL 8 不支持
--       CREATE TABLE IF NOT EXISTS ... 不支持, 用存储过程绕开)。
--
-- 口径说明:
--   · subscription_count = COUNT(mp_subscription WHERE status='ACTIVE')
--     —— 订阅成功 +1, 退订 -1, 重新订阅 +1。健康检查第 19 节会断言这个不变量。
--   · UNIQUE(publication_id, tenant_company_id, user_id) **不含 deleted**
--     —— 与 mp_install / mp_review 同一口径: 软删也占位,
--     「每个租户每个商品一条订阅」是硬约束。
--   · plan 目前只有 'FREE'。marketplace 与 opc-billing 之间没有写单路径,
--     刻意不造付费套餐 —— 一个点不动的「付费」按钮比没有更糟。
-- ============================================================

SET NAMES utf8mb4;

DELIMITER $$
CREATE PROCEDURE create_table_if_not_exists(IN tbl_name VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = tbl_name) THEN
        SET @sql_text = ddl;
        PREPARE stmt FROM @sql_text;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------- mp_subscription ----------
CALL create_table_if_not_exists('mp_subscription', '
CREATE TABLE mp_subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    publication_id BIGINT NOT NULL,
    tenant_company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    plan VARCHAR(16) NOT NULL DEFAULT ''FREE'',
    status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'',
    start_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    end_time DATETIME(3),
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sub (publication_id, tenant_company_id, user_id),
    KEY idx_pub_status (publication_id, status),
    KEY idx_tenant (tenant_company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''订阅记录''');

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
