-- =============================================================================
-- V20260908__opc_insight_schema.sql
-- M4 INSIGHT MVP · Task 4：创建 INSIGHT 三张业务表
--
-- 目标库：OPC 业务库（与 opc_company_member / opc_agent_workflow 同 schema）
-- 执行方式：
--     1) Flyway 启用前：mysql -u<user> -p <opc_db> < sql/migrations/V20260908__opc_insight_schema.sql
--     2) Flyway 启用后：放入 Flyway classpath:db/migration/ 自动按版本号执行
--
-- 包含的表：
--   opc_insight_daily_report  INSIGHT 日报表（KPI + 自然语言总结 + 建议）
--   opc_insight_anomaly       INSIGHT 异常表（规则代码 + 等级 + 处置状态 + LLM 置信度）
--   opc_insight_advice        INSIGHT 决策建议表（按 topic 归档、可追加）
--
-- 表设计补充说明：
--   * advice 表设计为 append-only（代码层面不执行 UPDATE），但保留 update_time 列
--     是为了未来 schema 演进（例如新增 edited_md 字段）时无需 ALTER TABLE 加列。
--   * ON UPDATE CURRENT_TIMESTAMP 在 append-only 场景下不会被触发（无 UPDATE 语句），
--     但保留该默认值可以让后续若引入"编辑/审核"流程时无需 schema 变更。
--
-- 幂等性：使用 CREATE TABLE IF NOT EXISTS，可重复执行；
--         MySQL 8 对 CREATE TABLE 的 IF NOT EXISTS 原生支持，无需 stored procedure。
--         注意 MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（MariaDB 语法），
--         后续若做字段扩展请参考 V20260905__workflow_cron.sql 的 information_schema 模式。
--
-- 与 opc_20260903.sql 的风格差异（计划 §Task 4 显式指定）：
--   * 主键 BIGINT PRIMARY KEY AUTO_INCREMENT（沿用计划）
--   * 时间字段使用 DATETIME DEFAULT CURRENT_TIMESTAMP [ON UPDATE CURRENT_TIMESTAMP]
--     （计划 §Task 4 写法；opc_20260903.sql 用 DATETIME DEFAULT NULL 由代码维护）
--   * kpi_json 使用 MySQL 8 原生 JSON 类型以便 JSON_EXTRACT / 函数索引
--     （opc_20260903.sql 用 TEXT + COMMENT 标记 JSON — 这里保留为 JSON 以利用校验）
--   * MEDIUMTEXT 用于长文本字段（summary_md / advice_md / description）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) INSIGHT 日报表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_insight_daily_report` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '主键',
  `company_id`   BIGINT       NOT NULL                               COMMENT '所属公司 ID',
  `period`       DATE         NOT NULL                               COMMENT '报告日期（按天）',
  `summary_md`   MEDIUMTEXT                                            COMMENT '自然语言总结（Markdown）',
  `kpi_json`     JSON                                                  COMMENT 'KPI 指标 JSON',
  `advice_md`    MEDIUMTEXT                                            COMMENT 'AI 建议（Markdown）',
  `llm_used`     VARCHAR(64)                                           COMMENT '生成使用的 LLM 模型 ID',
  `create_by`    VARCHAR(64)  DEFAULT NULL                            COMMENT '创建者',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT NULL                            COMMENT '更新者',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_period` (`company_id`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INSIGHT 日报表';

-- -----------------------------------------------------------------------------
-- 2) INSIGHT 异常表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_insight_anomaly` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '主键',
  `company_id`     BIGINT       NOT NULL                               COMMENT '所属公司 ID',
  `period`         DATE         NOT NULL                               COMMENT '检测日期',
  `level`          VARCHAR(8)   NOT NULL                               COMMENT '等级: INFO/WARN/ERROR',
  `rule_code`      VARCHAR(64)  NOT NULL                               COMMENT '异常规则编码（用于去重 / 排查）',
  `description`    VARCHAR(512) DEFAULT NULL                           COMMENT '异常描述',
  `create_by`      VARCHAR(64)  DEFAULT ''                              COMMENT '创建人',
  `status`         VARCHAR(16)  DEFAULT 'OPEN'                         COMMENT '处置状态: OPEN/ACK/RESOLVED/IGNORED',
  `llm_confidence` DECIMAL(3,2) DEFAULT NULL                           COMMENT 'LLM 置信度 0.00-1.00',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP               COMMENT '创建时间',
  `update_by`      VARCHAR(64)  DEFAULT ''                              COMMENT '更新人',
  `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_company_status` (`company_id`, `status`),
  KEY `idx_period` (`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INSIGHT 异常表';

-- -----------------------------------------------------------------------------
-- 3) INSIGHT 决策建议表（append-only，按 topic 归档）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_insight_advice` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT                  COMMENT '主键',
  `company_id`  BIGINT       NOT NULL                                 COMMENT '所属公司 ID',
  `topic`       VARCHAR(64)  NOT NULL                                 COMMENT '建议主题: TAX/CASHFLOW/HIRING/INVENTORY/...',
  `advice_md`   MEDIUMTEXT                                              COMMENT 'AI 建议正文（Markdown）',
  `llm_used`    VARCHAR(64)                                            COMMENT '生成使用的 LLM 模型 ID',
  `confidence`  DECIMAL(3,2) DEFAULT NULL                              COMMENT 'LLM 置信度 0.00-1.00',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP                 COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（即使 append-only 也保留，触发 ON UPDATE 不会改变值）',
  PRIMARY KEY (`id`),
  KEY `idx_company_topic_time` (`company_id`, `topic`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INSIGHT 决策建议表';

-- =============================================================================
-- 结束 — V20260908__opc_insight_schema.sql
-- =============================================================================
