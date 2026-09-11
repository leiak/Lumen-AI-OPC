-- V20260918__crm_schema.sql
-- OPC CRM 6 表表 + 审计列

CREATE TABLE IF NOT EXISTS opc_crm_customer (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(200) NOT NULL,
    source        VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    tags          VARCHAR(512) NOT NULL DEFAULT '',       -- 逗号分隔
    owner_id      BIGINT       NOT NULL,
    level         VARCHAR(8)   NOT NULL DEFAULT 'C',
    phone         VARCHAR(32),
    email         VARCHAR(128),
    address       VARCHAR(512),
    remark        VARCHAR(1024),
    create_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64),
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_owner_id (owner_id),
    INDEX idx_level (level),
    INDEX idx_source (source),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案';

CREATE TABLE IF NOT EXISTS opc_crm_contact (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    phone       VARCHAR(32),
    email       VARCHAR(128),
    position    VARCHAR(64),
    is_primary  TINYINT      NOT NULL DEFAULT 0,
    remark      VARCHAR(512),
    create_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   VARCHAR(64),
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户联系人';

CREATE TABLE IF NOT EXISTS opc_crm_follow_up (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT       NOT NULL,
    contact_id  BIGINT,
    type        VARCHAR(32)  NOT NULL DEFAULT 'PHONE',    -- PHONE/EMAIL/WECHAT/VISIT/OTHER
    content     VARCHAR(2048) NOT NULL,
    next_at     DATETIME,
    owner_id    BIGINT       NOT NULL,
    create_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   VARCHAR(64),
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_next_at (next_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进记录';

CREATE TABLE IF NOT EXISTS opc_crm_opportunity (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL DEFAULT 0,
    stage           VARCHAR(32)  NOT NULL DEFAULT 'LEAD',   -- LEAD/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST
    score           INT          NOT NULL DEFAULT 0,         -- 0-100 LLM 商机打分
    score_reason    VARCHAR(1024),
    expected_close  DATE,
    owner_id        BIGINT       NOT NULL,
    lost_reason     VARCHAR(512),
    create_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_stage (stage),
    INDEX idx_owner_id (owner_id),
    INDEX idx_expected_close (expected_close)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售商机';

CREATE TABLE IF NOT EXISTS opc_crm_contract (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT       NOT NULL,
    opportunity_id  BIGINT,
    contract_no     VARCHAR(64)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/ACTIVE/EXPIRED/TERMINATED
    signed_at       DATE,
    expire_at       DATE,
    file_url        VARCHAR(512),
    remark          VARCHAR(1024),
    create_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_contract_no (contract_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status),
    INDEX idx_signed_at (signed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户合同';

CREATE TABLE IF NOT EXISTS opc_crm_order (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT       NOT NULL,
    contract_id     BIGINT,
    order_no        VARCHAR(64)  NOT NULL,
    items_json      TEXT         NOT NULL,                  -- [{sku, name, qty, price}]
    total           DECIMAL(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING', -- PENDING/PAID/SHIPPED/COMPLETED/CANCELLED
    paid_at         DATETIME,
    shipped_at      DATETIME,
    remark          VARCHAR(1024),
    create_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_contract_id (contract_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户订单';
