-- OPC Notification schema (W49)
-- 4 tables: email_log / sms_log / inbox / template
-- Audit columns (create_by / update_by / update_time) added for parity with other OPC tables.

CREATE TABLE IF NOT EXISTS opc_notification_email_log (
    id BIGINT PRIMARY KEY COMMENT 'Snowflake ID',
    recipient VARCHAR(255) NOT NULL COMMENT 'to email',
    subject VARCHAR(500) NOT NULL COMMENT 'email subject',
    body MEDIUMTEXT NOT NULL COMMENT 'email body (HTML allowed)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending, 1=sent, 2=failed',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'retry attempts',
    error_msg VARCHAR(1000) DEFAULT NULL COMMENT 'last error',
    provider VARCHAR(50) NOT NULL DEFAULT 'smtp' COMMENT 'smtp/sendgrid/etc',
    sent_at DATETIME DEFAULT NULL COMMENT 'actual sent time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    create_by VARCHAR(64) DEFAULT '' COMMENT 'creator',
    update_by VARCHAR(64) DEFAULT '' COMMENT 'updater',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    INDEX idx_email_recipient (recipient),
    INDEX idx_email_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件发送日志';

CREATE TABLE IF NOT EXISTS opc_notification_sms_log (
    id BIGINT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    template_code VARCHAR(100) NOT NULL COMMENT 'provider template code',
    vars_json VARCHAR(2000) DEFAULT NULL COMMENT 'template variables JSON',
    content VARCHAR(1000) NOT NULL COMMENT 'rendered SMS content',
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    error_msg VARCHAR(1000) DEFAULT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'aliyun',
    sent_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    create_by VARCHAR(64) DEFAULT '' COMMENT 'creator',
    update_by VARCHAR(64) DEFAULT '' COMMENT 'updater',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    INDEX idx_sms_phone (phone),
    INDEX idx_sms_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送日志';

CREATE TABLE IF NOT EXISTS opc_notification_inbox (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'recipient user_id',
    type VARCHAR(50) NOT NULL COMMENT 'system/marketing/interaction',
    title VARCHAR(255) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    link VARCHAR(500) DEFAULT NULL COMMENT 'optional click target',
    read_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    create_by VARCHAR(64) DEFAULT '' COMMENT 'creator',
    update_by VARCHAR(64) DEFAULT '' COMMENT 'updater',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    INDEX idx_inbox_user_created (user_id, created_at),
    INDEX idx_inbox_user_read (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信';

CREATE TABLE IF NOT EXISTS opc_notification_template (
    id BIGINT PRIMARY KEY,
    code VARCHAR(100) NOT NULL COMMENT 'unique code e.g. order_paid',
    channel VARCHAR(20) NOT NULL COMMENT 'email/sms/inbox/all',
    subject VARCHAR(500) DEFAULT NULL,
    body TEXT NOT NULL COMMENT 'Freemarker template',
    vars_schema VARCHAR(2000) DEFAULT NULL COMMENT 'JSON schema of required vars',
    version INT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    create_by VARCHAR(64) DEFAULT '' COMMENT 'creator',
    update_by VARCHAR(64) DEFAULT '' COMMENT 'updater',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    UNIQUE KEY uk_template_code_version (code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知模板';