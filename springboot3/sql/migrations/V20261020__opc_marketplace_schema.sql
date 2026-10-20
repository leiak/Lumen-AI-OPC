-- ============================================================
-- V20261020__opc_marketplace_schema.sql
-- OPC Marketplace P1 (W80) — 6 张核心表 + 类目/标签种子数据
--   mp_author      作者档案
--   mp_publication 上架商品 (AGENT / MODULE)
--   mp_install     安装记录
--   mp_review      评价
--   mp_category    类目树
--   mp_tag         标签
-- 幂等：可重复执行（W3 workflow_cron 经验 — 用存储过程替代
--        MySQL 8 不支持的 ALTER TABLE ... ADD COLUMN IF NOT EXISTS）
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

-- ---------- mp_author ----------
CALL create_table_if_not_exists('mp_author', '
CREATE TABLE mp_author (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_company_id BIGINT NOT NULL,
    author_name VARCHAR(128) NOT NULL,
    contact_email VARCHAR(128) NOT NULL,
    contact_phone VARCHAR(32),
    intro TEXT,
    avatar_url VARCHAR(512),
    level VARCHAR(16) NOT NULL DEFAULT ''NORMAL'',
    total_agents INT NOT NULL DEFAULT 0,
    total_subscribers INT NOT NULL DEFAULT 0,
    total_revenue DECIMAL(15,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'',
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_author_company (author_company_id, deleted),
    KEY idx_level_status (level, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''作者档案''');

-- ---------- mp_publication ----------
CALL create_table_if_not_exists('mp_publication', '
CREATE TABLE mp_publication (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_type VARCHAR(16) NOT NULL,
    agent_definition_id BIGINT,
    module_id BIGINT,
    author_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category_code VARCHAR(32) NOT NULL,
    sub_category_code VARCHAR(32),
    summary VARCHAR(512),
    long_description TEXT,
    icon_url VARCHAR(512),
    screenshots JSON,
    tags VARCHAR(512),
    status VARCHAR(16) NOT NULL DEFAULT ''DRAFT'',
    published_at DATETIME(3),
    audit_message VARCHAR(512),
    view_count INT NOT NULL DEFAULT 0,
    install_count INT NOT NULL DEFAULT 0,
    subscription_count INT NOT NULL DEFAULT 0,
    avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_pub_code (code, deleted),
    KEY idx_status_pub (status, published_at DESC),
    KEY idx_author (author_id),
    KEY idx_category (category_code, status, published_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''上架商品''');

-- ---------- mp_install ----------
CALL create_table_if_not_exists('mp_install', '
CREATE TABLE mp_install (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    publication_id BIGINT NOT NULL,
    tenant_company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    agent_instance_id BIGINT,
    install_source VARCHAR(32) NOT NULL DEFAULT ''MARKET'',
    status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'',
    installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    uninstalled_at DATETIME(3),
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_install (publication_id, tenant_company_id, user_id, deleted),
    KEY idx_user_status (user_id, status),
    KEY idx_tenant (tenant_company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''安装记录''');

-- ---------- mp_review ----------
CALL create_table_if_not_exists('mp_review', '
CREATE TABLE mp_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    publication_id BIGINT NOT NULL,
    tenant_company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    content VARCHAR(1024),
    status VARCHAR(16) NOT NULL DEFAULT ''VISIBLE'',
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_review (publication_id, user_id, deleted),
    KEY idx_pub_status (publication_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''评价''');

-- ---------- mp_category ----------
CALL create_table_if_not_exists('mp_category', '
CREATE TABLE mp_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    product_type VARCHAR(16) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    icon VARCHAR(64),
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_cat_code (product_type, code, deleted),
    KEY idx_parent (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''类目树''');

-- ---------- mp_tag ----------
CALL create_table_if_not_exists('mp_tag', '
CREATE TABLE mp_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(32) NOT NULL,
    category VARCHAR(32),
    sort_order INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tag_name (name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''标签''');

-- ---------- 种子数据（幂等 INSERT IGNORE）----------
INSERT IGNORE INTO mp_category (product_type, code, name, sort_order, icon) VALUES
  ('AGENT', 'office',  '办公助手',  1, 'Briefcase'),
  ('AGENT', 'finance', '财务助手',  2, 'Money'),
  ('AGENT', 'sales',   '销售助手',  3, 'TrendCharts'),
  ('MODULE','data',    '数据集成',  1, 'DataLine'),
  ('MODULE','integration', '系统集成', 2, 'Connection'),
  ('MODULE','tool',    '效率工具',  3, 'Tools');

INSERT IGNORE INTO mp_tag (name, category, sort_order) VALUES
  ('高评分', 'quality', 1),
  ('新发布', 'quality', 2),
  ('中文', 'lang', 1),
  ('多语言', 'lang', 2),
  ('企业级', 'scale', 1),
  ('轻量', 'scale', 2),
  ('免费试用', 'billing', 1),
  ('付费订阅', 'billing', 2),
  ('代码生成', 'domain', 1),
  ('内容创作', 'domain', 2);

DROP PROCEDURE IF EXISTS create_table_if_not_exists;