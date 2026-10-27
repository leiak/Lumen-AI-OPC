-- ============================================================
-- opc-marketplace schema (initdb 副本)
--
-- 这是 sql/migrations/V20261020__opc_marketplace_schema.sql 的
-- initdb.d 副本 —— 空库首次启动时 MySQL 容器会自动跑这个目录。
--
-- 为什么要副本:V20261020 那份挂在 Flyway 迁移链上,而 Flyway 目前
-- 没在这些业务服务里启用;只靠它的话,全新起一套 docker stack
-- 根本没有 mp_* 这 6 张表,marketplace 服务起来直接报表不存在。
--
-- 改动纪律:两份文件的 DDL 必须一致 —— 改 V20261020 就同步改这里
-- (反之亦然),否则"新环境"和"存量环境"的表结构会分叉。
--
-- 幂等:存储过程 create_table_if_not_exists 探测 information_schema,
--       种子数据用 INSERT IGNORE —— 可重复执行。
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
    UNIQUE KEY uk_author_company (author_company_id),
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
    UNIQUE KEY uk_pub_code (code),
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
    UNIQUE KEY uk_install (publication_id, tenant_company_id, user_id),
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
    UNIQUE KEY uk_review (publication_id, user_id),
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
    UNIQUE KEY uk_cat_code (product_type, code),
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
    UNIQUE KEY uk_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''标签''');

-- ---------- mp_subscription ----------
-- 让 mp_publication.subscription_count 有真实来源: 此前这个数字在页面上
-- 展示着, 底下却没有任何一条记录支撑它(incrementSubscriptionCount 零调用方)。
-- 口径: subscription_count = COUNT(mp_subscription WHERE status='ACTIVE')。
-- UNIQUE 不含 deleted —— 与 mp_install / mp_review 同一口径, 软删也占位。
-- plan 目前只有 'FREE': marketplace 与 opc-billing 之间没有写单路径,
-- 刻意不造付费套餐(一个点不动的「付费」按钮比没有更糟)。
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

-- ---------- 种子数据（幂等 INSERT IGNORE）----------
INSERT IGNORE INTO mp_category (product_type, code, name, sort_order, icon) VALUES
  ('AGENT', 'office',  '办公助手',  1, 'Briefcase'),
  ('AGENT', 'finance', '财务助手',  2, 'Money'),
  ('AGENT', 'sales',   '销售助手',  3, 'TrendCharts'),
  ('MODULE','data',    '数据集成',  1, 'DataLine'),
  ('MODULE','integration', '系统集成', 2, 'Connection'),
  ('MODULE','tool',    '效率工具',  3, 'Tools'),
  -- 电商运营:MODULE 商品 PUB-ECOM-001 挂的是社区里的 'ecom-ops' 模块,
  -- 以前它的 category_code='ECOM' 在这张表里查不到任何一行 → 点 MODULE 的
  -- 任何分类页签都是空的。code 用小写,理由见 opc_demo_ecommerce_seed.sql 里的说明。
  ('MODULE','ecom',    '电商运营',  4, 'ShoppingCart');

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
