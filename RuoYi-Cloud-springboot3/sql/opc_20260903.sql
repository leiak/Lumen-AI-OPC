-- ============================================================
-- OAC (OPC-Agent-Community) 数据库初始化脚本
-- 日期: 2026-09-03
-- 说明: 在 RuoYi 基础表（sys_*）之上扩展 OPC 业务表
-- 原则: sys_* 不动，opc_* 新建
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 一、用户与公司
-- ============================================================

-- OPC 用户画像（扩展自 sys_user，user_type='OPC'）
DROP TABLE IF EXISTS `opc_user_profile`;
CREATE TABLE `opc_user_profile` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT     COMMENT '主键',
  `user_id`         BIGINT       NOT NULL                     COMMENT '关联 sys_user.user_id',
  `user_type`       VARCHAR(16)  NOT NULL DEFAULT 'ENTREPRENEUR' COMMENT '用户类型: ENTREPRENEUR-创业者, SERVICE-服务商, OWNER-企业主, INVESTOR-投资者',
  `real_name`       VARCHAR(64)  DEFAULT NULL                 COMMENT '真实姓名',
  `id_card_no`      VARCHAR(32)  DEFAULT NULL                 COMMENT '身份证号（AES 加密）',
  `mobile`          VARCHAR(20)  DEFAULT NULL                 COMMENT '手机号',
  `avatar_url`      VARCHAR(255) DEFAULT NULL                 COMMENT '头像URL',
  `bio`             VARCHAR(512) DEFAULT NULL                 COMMENT '个人简介',
  `industry`        VARCHAR(64)  DEFAULT NULL                 COMMENT '所在行业',
  `city`            VARCHAR(64)  DEFAULT NULL                 COMMENT '所在城市',
  `invitation_code` VARCHAR(32)  DEFAULT NULL                 COMMENT '邀请码',
  `inviter_id`      BIGINT       DEFAULT NULL                 COMMENT '邀请人 user_id',
  `verified`        TINYINT(1)   NOT NULL DEFAULT 0           COMMENT '是否实名认证 0-否 1-是',
  `verified_time`   DATETIME     DEFAULT NULL                 COMMENT '认证时间',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'    COMMENT '状态: ACTIVE-正常, FROZEN-冻结, CLOSED-注销',
  `create_by`       VARCHAR(64)  DEFAULT ''                   COMMENT '创建者',
  `create_time`     DATETIME     DEFAULT NULL                 COMMENT '创建时间',
  `update_by`       VARCHAR(64)  DEFAULT ''                   COMMENT '更新者',
  `update_time`     DATETIME     DEFAULT NULL                 COMMENT '更新时间',
  `remark`          VARCHAR(500) DEFAULT NULL                 COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_invitation_code` (`invitation_code`),
  KEY `idx_user_type` (`user_type`),
  KEY `idx_inviter_id` (`inviter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OPC 用户画像';

-- 一人公司档案
DROP TABLE IF EXISTS `opc_company_profile`;
CREATE TABLE `opc_company_profile` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT       COMMENT '主键',
  `company_code`      VARCHAR(32)  NOT NULL                       COMMENT '公司编码（业务用）',
  `owner_user_id`     BIGINT       NOT NULL                       COMMENT '所属主 user_id',
  `company_name`      VARCHAR(128) NOT NULL                       COMMENT '公司名称',
  `company_type`      VARCHAR(16)  NOT NULL DEFAULT 'INDIVIDUAL'  COMMENT '类型: INDIVIDUAL-个体户, PERSONAL_SOLE-个人独资, LLP-有限合伙',
  `business_license`  VARCHAR(64)  DEFAULT NULL                   COMMENT '营业执照号（加密）',
  `license_pic_url`   VARCHAR(255) DEFAULT NULL                   COMMENT '营业执照图片URL',
  `tax_no`            VARCHAR(64)  DEFAULT NULL                   COMMENT '税号',
  `industry_code`     VARCHAR(32)  DEFAULT NULL                   COMMENT '行业代码',
  `industry_name`     VARCHAR(64)  DEFAULT NULL                   COMMENT '行业名称',
  `registered_capital` DECIMAL(18,2) DEFAULT NULL                 COMMENT '注册资本',
  `establish_date`    DATE         DEFAULT NULL                   COMMENT '成立日期',
  `province`          VARCHAR(32)  DEFAULT NULL                   COMMENT '省',
  `city`              VARCHAR(32)  DEFAULT NULL                   COMMENT '市',
  `district`          VARCHAR(32)  DEFAULT NULL                   COMMENT '区',
  `address`           VARCHAR(255) DEFAULT NULL                   COMMENT '详细地址',
  `legal_person`      VARCHAR(64)  DEFAULT NULL                   COMMENT '法人',
  `phone`             VARCHAR(20)  DEFAULT NULL                   COMMENT '联系电话',
  `email`             VARCHAR(128) DEFAULT NULL                   COMMENT '邮箱',
  `logo_url`          VARCHAR(255) DEFAULT NULL                   COMMENT '公司Logo',
  `introduction`      TEXT         DEFAULT NULL                   COMMENT '公司介绍',
  `scale`             VARCHAR(16)  DEFAULT 'SMALL'                COMMENT '规模: SMALL, MEDIUM, LARGE',
  `verified`          TINYINT(1)   NOT NULL DEFAULT 0             COMMENT '认证状态 0-未认证 1-已认证',
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'      COMMENT '状态: NORMAL-正常, LOCKED-锁定, CLOSED-注销',
  `create_by`         VARCHAR(64)  DEFAULT ''                     COMMENT '创建者',
  `create_time`       DATETIME     DEFAULT NULL                   COMMENT '创建时间',
  `update_by`         VARCHAR(64)  DEFAULT ''                     COMMENT '更新者',
  `update_time`       DATETIME     DEFAULT NULL                   COMMENT '更新时间',
  `remark`            VARCHAR(500) DEFAULT NULL                   COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_code` (`company_code`),
  KEY `idx_owner_user_id` (`owner_user_id`),
  KEY `idx_industry_code` (`industry_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一人公司档案';

-- 公司成员
DROP TABLE IF EXISTS `opc_company_member`;
CREATE TABLE `opc_company_member` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT       COMMENT '主键',
  `company_id`  BIGINT       NOT NULL                     COMMENT '公司ID',
  `user_id`     BIGINT       NOT NULL                     COMMENT '成员 user_id',
  `role`        VARCHAR(16)  NOT NULL DEFAULT 'STAFF'     COMMENT '角色: OWNER-老板, ADMIN-管理员, STAFF-员工',
  `department`  VARCHAR(64)  DEFAULT NULL                 COMMENT '部门',
  `position`    VARCHAR(64)  DEFAULT NULL                 COMMENT '职位',
  `joined_at`   DATETIME     DEFAULT NULL                 COMMENT '加入时间',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'    COMMENT '状态: ACTIVE-在职, LEFT-离职',
  `create_by`   VARCHAR(64)  DEFAULT ''                   COMMENT '创建者',
  `create_time` DATETIME     DEFAULT NULL                 COMMENT '创建时间',
  `update_by`   VARCHAR(64)  DEFAULT ''                   COMMENT '更新者',
  `update_time` DATETIME     DEFAULT NULL                 COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_user` (`company_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司成员';

-- ============================================================
-- 二、Agent 体系
-- ============================================================

-- Agent 定义（模板，公开市场用）
DROP TABLE IF EXISTS `opc_agent_definition`;
CREATE TABLE `opc_agent_definition` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `agent_code`        VARCHAR(64)  NOT NULL                         COMMENT 'Agent编码',
  `name`              VARCHAR(128) NOT NULL                         COMMENT 'Agent名称',
  `version`           VARCHAR(16)  NOT NULL DEFAULT 'v1.0'          COMMENT '版本',
  `category`          VARCHAR(32)  NOT NULL                         COMMENT '分类: FINANCE-财务, ERP-进销存, CRM-客户, HR-人力, ECOM-电商, CONTENT-内容',
  `description`       VARCHAR(512) DEFAULT NULL                     COMMENT '简介',
  `capabilities`      TEXT         DEFAULT NULL                     COMMENT '能力清单 JSON',
  `system_prompt`     MEDIUMTEXT   DEFAULT NULL                     COMMENT '系统Prompt',
  `tools_config`      TEXT         DEFAULT NULL                     COMMENT '工具配置 JSON',
  `primary_model`     VARCHAR(32)  NOT NULL DEFAULT 'deepseek-v3'   COMMENT '主模型',
  `fallback_model`    VARCHAR(32)  DEFAULT 'gpt-4o-mini'            COMMENT '备用模型',
  `prompt_version`    VARCHAR(32)  DEFAULT NULL                     COMMENT 'Prompt 版本号',
  `memory_type`       VARCHAR(16)  DEFAULT 'redis'                  COMMENT '记忆类型: redis/qdrant',
  `price_monthly`     DECIMAL(10,2) DEFAULT 0.00                    COMMENT '月租价格（元）',
  `price_yearly`      DECIMAL(10,2) DEFAULT 0.00                    COMMENT '年租价格（元）',
  `token_price_input` DECIMAL(10,6) DEFAULT 0.001000               COMMENT '输入 Token 单价（元/1k）',
  `token_price_output`DECIMAL(10,6) DEFAULT 0.002000               COMMENT '输出 Token 单价（元/1k）',
  `free_quota`        INT          DEFAULT 1000                     COMMENT '免费 Token 额度',
  `icon_url`          VARCHAR(255) DEFAULT NULL                     COMMENT '图标',
  `tags`              VARCHAR(255) DEFAULT NULL                     COMMENT '标签逗号分隔',
  `published`         TINYINT(1)   NOT NULL DEFAULT 0               COMMENT '是否上架 0-否 1-是',
  `sort_order`        INT          DEFAULT 0                        COMMENT '排序',
  `downloads`         INT          DEFAULT 0                        COMMENT '下载/雇佣数',
  `rating`            DECIMAL(3,2) DEFAULT 5.00                     COMMENT '平均评分',
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'        COMMENT '状态',
  `create_by`         VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`       DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`         VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`       DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  `remark`            VARCHAR(500) DEFAULT NULL                     COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_code_version` (`agent_code`, `version`),
  KEY `idx_category` (`category`),
  KEY `idx_published` (`published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 定义模板';

-- Agent 实例（用户雇佣后产生）
DROP TABLE IF EXISTS `opc_agent_instance`;
CREATE TABLE `opc_agent_instance` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `instance_code`       VARCHAR(64)  NOT NULL                         COMMENT '实例编码',
  `company_id`          BIGINT       NOT NULL                         COMMENT '所属公司ID',
  `definition_id`       BIGINT       NOT NULL                         COMMENT '关联 Agent 定义 ID',
  `nickname`            VARCHAR(64)  DEFAULT NULL                     COMMENT '用户给实例起的昵称',
  `hire_type`           VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY'       COMMENT '雇佣类型: MONTHLY-月, QUARTERLY-季, YEARLY-年, TRIAL-试用',
  `hire_duration`       INT          NOT NULL DEFAULT 1               COMMENT '雇佣时长（与 hire_type 单位对应）',
  `start_time`          DATETIME     DEFAULT NULL                     COMMENT '起始时间',
  `expire_time`         DATETIME     DEFAULT NULL                     COMMENT '到期时间',
  `auto_renew`          TINYINT(1)   NOT NULL DEFAULT 0               COMMENT '是否自动续费',
  `config_override`     TEXT         DEFAULT NULL                     COMMENT '配置覆盖 JSON（用户级个性化）',
  `memory_namespace`    VARCHAR(64)  DEFAULT NULL                     COMMENT '记忆命名空间',
  `token_used`          BIGINT       NOT NULL DEFAULT 0               COMMENT '累计消耗 Token',
  `token_quota`         BIGINT       DEFAULT NULL                     COMMENT 'Token 配额',
  `task_count`          INT          NOT NULL DEFAULT 0               COMMENT '累计任务数',
  `last_active_time`    DATETIME     DEFAULT NULL                     COMMENT '最近活跃时间',
  `status`              VARCHAR(16)  NOT NULL DEFAULT 'RUNNING'       COMMENT '状态: RUNNING-运行, PAUSED-暂停, EXPIRED-过期, REVOKED-退订',
  `create_by`           VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`         DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`           VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`         DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  `remark`              VARCHAR(500) DEFAULT NULL                     COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_code` (`instance_code`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_definition_id` (`definition_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 实例';

-- Agent 雇佣记录（订阅订单）
DROP TABLE IF EXISTS `opc_agent_hire_record`;
CREATE TABLE `opc_agent_hire_record` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `record_code`     VARCHAR(64)  NOT NULL                         COMMENT '记录号',
  `company_id`      BIGINT       NOT NULL                         COMMENT '公司ID',
  `definition_id`   BIGINT       NOT NULL                         COMMENT '定义ID',
  `instance_id`     BIGINT       DEFAULT NULL                     COMMENT '实例ID',
  `hire_type`       VARCHAR(16)  NOT NULL                         COMMENT '雇佣类型',
  `hire_duration`   INT          NOT NULL                         COMMENT '时长',
  `amount`          DECIMAL(10,2) NOT NULL                        COMMENT '订单金额',
  `discount_amount` DECIMAL(10,2) DEFAULT 0.00                    COMMENT '优惠金额',
  `paid_amount`     DECIMAL(10,2) NOT NULL                        COMMENT '实付金额',
  `pay_method`      VARCHAR(16)  DEFAULT 'WALLET'                 COMMENT '支付方式: WALLET/ALIPAY/WECHAT',
  `pay_status`      VARCHAR(16)  NOT NULL DEFAULT 'PENDING'       COMMENT '支付状态: PENDING/PAID/REFUNDED',
  `pay_time`        DATETIME     DEFAULT NULL                     COMMENT '支付时间',
  `pay_trade_no`    VARCHAR(64)  DEFAULT NULL                     COMMENT '支付流水号',
  `order_id`        BIGINT       DEFAULT NULL                     COMMENT '计费订单ID',
  `expire_time`     DATETIME     DEFAULT NULL                     COMMENT '到期时间',
  `create_by`       VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`     DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`       VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`     DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_code` (`record_code`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_instance_id` (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 雇佣记录';

-- Agent 编排工作流
DROP TABLE IF EXISTS `opc_agent_workflow`;
CREATE TABLE `opc_agent_workflow` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `workflow_code` VARCHAR(64)  NOT NULL                         COMMENT '工作流编码',
  `name`          VARCHAR(128) NOT NULL                         COMMENT '名称',
  `company_id`    BIGINT       DEFAULT NULL                     COMMENT '所属公司（NULL 表示系统模板）',
  `definition_id` BIGINT       DEFAULT NULL                     COMMENT '基于的 Agent 定义',
  `trigger_type`  VARCHAR(16)  NOT NULL DEFAULT 'MANUAL'        COMMENT '触发器: MANUAL-手动, CRON-定时, EVENT-事件',
  `trigger_config`TEXT         DEFAULT NULL                     COMMENT '触发器配置 JSON',
  `dag_json`      MEDIUMTEXT   NOT NULL                         COMMENT '工作流 DAG JSON',
  `enabled`       TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '启用',
  `cron_expression` VARCHAR(50) DEFAULT NULL                    COMMENT '/* V20260905 */ Quartz cron 表达式，trigger_type=CRON 时生效',
  `timezone`      VARCHAR(20)  NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '/* V20260905 */ cron 求值时区',
  `next_run_at`   DATETIME     DEFAULT NULL                     COMMENT '/* V20260905 */ 下次预计执行时间',
  `last_run_time` DATETIME     DEFAULT NULL                     COMMENT '上次运行',
  `run_count`     INT          NOT NULL DEFAULT 0               COMMENT '累计运行',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'         COMMENT '状态: DRAFT/PUBLISHED/ARCHIVED',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`   DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  `remark`        VARCHAR(500) DEFAULT NULL                     COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_code` (`workflow_code`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_definition_id` (`definition_id`),
  KEY `idx_next_run_at` (`next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 编排工作流';

-- Agent 工作流执行历史
DROP TABLE IF EXISTS `opc_agent_workflow_run`;
CREATE TABLE `opc_agent_workflow_run` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `run_code`        VARCHAR(64)  NOT NULL                         COMMENT '执行编号',
  `workflow_id`     BIGINT       NOT NULL                         COMMENT '工作流ID',
  `company_id`      BIGINT       NOT NULL                         COMMENT '公司ID',
  `trigger_type`    VARCHAR(16)  NOT NULL                         COMMENT '触发器类型',
  `trigger_source`  VARCHAR(128) DEFAULT NULL                     COMMENT '触发来源',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'       COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  `input_params`    TEXT         DEFAULT NULL                     COMMENT '输入参数 JSON',
  `output_result`   MEDIUMTEXT   DEFAULT NULL                     COMMENT '输出结果 JSON',
  `error_message`   TEXT         DEFAULT NULL                     COMMENT '错误信息',
  `step_logs`       MEDIUMTEXT   DEFAULT NULL                     COMMENT '每步执行日志 JSON',
  `token_used`      INT          NOT NULL DEFAULT 0               COMMENT '总 Token',
  `cost`            DECIMAL(10,4) DEFAULT 0.0000                  COMMENT '本次花费',
  `start_time`      DATETIME     DEFAULT NULL                     COMMENT '开始时间',
  `end_time`        DATETIME     DEFAULT NULL                     COMMENT '结束时间',
  `duration_ms`     INT          DEFAULT NULL                     COMMENT '耗时毫秒',
  `create_by`       VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`     DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`       VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`     DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_code` (`run_code`),
  KEY `idx_workflow_id` (`workflow_id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行历史';

-- Agent 任务记录
DROP TABLE IF EXISTS `opc_agent_task`;
CREATE TABLE `opc_agent_task` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `task_code`       VARCHAR(64)  NOT NULL                         COMMENT '任务编号',
  `company_id`      BIGINT       NOT NULL                         COMMENT '公司ID',
  `instance_id`     BIGINT       NOT NULL                         COMMENT 'Agent 实例ID',
  `definition_id`   BIGINT       NOT NULL                         COMMENT 'Agent 定义ID',
  `task_type`       VARCHAR(32)  NOT NULL                         COMMENT '任务类型',
  `session_id`      VARCHAR(64)  DEFAULT NULL                     COMMENT '会话ID',
  `input`           MEDIUMTEXT   DEFAULT NULL                     COMMENT '输入内容',
  `output`          MEDIUMTEXT   DEFAULT NULL                     COMMENT '输出内容',
  `tool_calls`      TEXT         DEFAULT NULL                     COMMENT '工具调用 JSON',
  `steps`           TEXT         DEFAULT NULL                     COMMENT '中间步骤 JSON',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'       COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/REVIEW',
  `error_message`   TEXT         DEFAULT NULL                     COMMENT '错误信息',
  `token_input`     INT          NOT NULL DEFAULT 0               COMMENT '输入Token',
  `token_output`    INT          NOT NULL DEFAULT 0               COMMENT '输出Token',
  `token_total`     INT          NOT NULL DEFAULT 0               COMMENT '总Token',
  `cost`            DECIMAL(10,4) DEFAULT 0.0000                  COMMENT '本次花费',
  `review_required` TINYINT(1)   NOT NULL DEFAULT 0               COMMENT '是否需要人工审核',
  `reviewed_by`     VARCHAR(64)  DEFAULT NULL                     COMMENT '审核人',
  `reviewed_time`   DATETIME     DEFAULT NULL                     COMMENT '审核时间',
  `review_opinion`  VARCHAR(500) DEFAULT NULL                     COMMENT '审核意见',
  `start_time`      DATETIME     DEFAULT NULL                     COMMENT '开始时间',
  `end_time`        DATETIME     DEFAULT NULL                     COMMENT '结束时间',
  `duration_ms`     INT          DEFAULT NULL                     COMMENT '耗时毫秒',
  `create_by`       VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`     DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`       VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`     DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务记录';

-- Agent Token 消耗
DROP TABLE IF EXISTS `opc_agent_token_usage`;
CREATE TABLE `opc_agent_token_usage` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `usage_code`    VARCHAR(64)  NOT NULL                         COMMENT '消耗编号',
  `company_id`    BIGINT       NOT NULL                         COMMENT '公司ID',
  `user_id`       BIGINT       NOT NULL                         COMMENT '用户ID',
  `instance_id`   BIGINT       DEFAULT NULL                     COMMENT '实例ID',
  `task_id`       BIGINT       DEFAULT NULL                     COMMENT '任务ID',
  `model`         VARCHAR(32)  NOT NULL                         COMMENT '模型',
  `model_type`    VARCHAR(16)  NOT NULL DEFAULT 'CHAT'          COMMENT '模型类型: CHAT/EMBEDDING',
  `token_input`  INT          NOT NULL DEFAULT 0               COMMENT '输入Token',
  `token_output` INT          NOT NULL DEFAULT 0               COMMENT '输出Token',
  `token_total`  INT          NOT NULL DEFAULT 0               COMMENT '总Token',
  `unit_price_input`  DECIMAL(10,6) DEFAULT 0.001000           COMMENT '输入单价',
  `unit_price_output` DECIMAL(10,6) DEFAULT 0.002000           COMMENT '输出单价',
  `cost`          DECIMAL(10,4) DEFAULT 0.0000                  COMMENT '花费',
  `latency_ms`    INT          DEFAULT NULL                     COMMENT '耗时',
  `success`       TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '是否成功',
  `error_message` VARCHAR(500) DEFAULT NULL                     COMMENT '错误信息',
  `request_id`    VARCHAR(64)  DEFAULT NULL                     COMMENT '请求ID',
  `biz_date`      DATE         DEFAULT NULL                     COMMENT '业务日期（按天汇总）',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_usage_code` (`usage_code`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_model` (`model`),
  KEY `idx_biz_date` (`biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token 消耗记录';

-- Agent 权限
DROP TABLE IF EXISTS `opc_agent_permission`;
CREATE TABLE `opc_agent_permission` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `instance_id`   BIGINT       NOT NULL                         COMMENT '实例ID',
  `permission`    VARCHAR(64)  NOT NULL                         COMMENT '权限标识',
  `resource_type` VARCHAR(32)  NOT NULL                         COMMENT '资源类型',
  `resource_id`   VARCHAR(64)  DEFAULT NULL                     COMMENT '资源ID',
  `granted`       TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '是否授予',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 权限';

-- ============================================================
-- 三、社区 / 模块市场
-- ============================================================

-- 业务模块分类
DROP TABLE IF EXISTS `opc_module_category`;
CREATE TABLE `opc_module_category` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `category_code`VARCHAR(64)  NOT NULL                         COMMENT '分类编码',
  `name`         VARCHAR(64)  NOT NULL                         COMMENT '分类名',
  `icon`         VARCHAR(255) DEFAULT NULL                     COMMENT '图标',
  `sort_order`   INT          DEFAULT 0                        COMMENT '排序',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '启用',
  `create_by`    VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`  DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`  DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块分类';

-- 业务模块
DROP TABLE IF EXISTS `opc_business_module`;
CREATE TABLE `opc_business_module` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `module_code`   VARCHAR(64)  NOT NULL                         COMMENT '模块编码',
  `name`          VARCHAR(128) NOT NULL                         COMMENT '模块名',
  `category_id`   BIGINT       NOT NULL                         COMMENT '分类ID',
  `description`   VARCHAR(512) DEFAULT NULL                     COMMENT '简介',
  `cover_url`     VARCHAR(255) DEFAULT NULL                     COMMENT '封面',
  `version`       VARCHAR(16)  NOT NULL DEFAULT 'v1.0'          COMMENT '版本',
  `author_id`     BIGINT       DEFAULT NULL                     COMMENT '作者 user_id',
  `price`         DECIMAL(10,2) DEFAULT 0.00                   COMMENT '价格',
  `is_free`       TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '是否免费',
  `downloads`     INT          NOT NULL DEFAULT 0               COMMENT '下载数',
  `rating`        DECIMAL(3,2) DEFAULT 5.00                    COMMENT '评分',
  `published`     TINYINT(1)   NOT NULL DEFAULT 0               COMMENT '上架',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'        COMMENT '状态',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`   DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  `remark`        VARCHAR(500) DEFAULT NULL                     COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_code` (`module_code`),
  KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块';

-- 模块订阅
DROP TABLE IF EXISTS `opc_module_subscription`;
CREATE TABLE `opc_module_subscription` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `company_id`   BIGINT       NOT NULL                         COMMENT '公司ID',
  `module_id`    BIGINT       NOT NULL                         COMMENT '模块ID',
  `plan`         VARCHAR(16)  NOT NULL DEFAULT 'FREE'          COMMENT '套餐',
  `start_time`   DATETIME     DEFAULT NULL                     COMMENT '起始',
  `expire_time`  DATETIME     DEFAULT NULL                     COMMENT '到期',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'        COMMENT '状态',
  `create_by`    VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`  DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`  DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_company_module` (`company_id`, `module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块订阅';

-- 模块评价
DROP TABLE IF EXISTS `opc_module_review`;
CREATE TABLE `opc_module_review` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `module_id`   BIGINT       NOT NULL                         COMMENT '模块ID',
  `user_id`     BIGINT       NOT NULL                         COMMENT '评价人',
  `rating`      INT          NOT NULL                         COMMENT '评分 1-5',
  `content`     TEXT         DEFAULT NULL                     COMMENT '评价内容',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED'     COMMENT '状态',
  `create_by`   VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time` DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_module_id` (`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块评价';

-- ============================================================
-- 四、财务 Agent
-- ============================================================

-- 财务凭证
DROP TABLE IF EXISTS `opc_finance_voucher`;
CREATE TABLE `opc_finance_voucher` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `voucher_code`   VARCHAR(64)  NOT NULL                         COMMENT '凭证编号',
  `company_id`     BIGINT       NOT NULL                         COMMENT '公司ID',
  `voucher_date`   DATE         NOT NULL                         COMMENT '凭证日期',
  `period`         VARCHAR(8)   NOT NULL                         COMMENT '会计期间 YYYY-MM',
  `summary`        VARCHAR(255) NOT NULL                         COMMENT '摘要',
  `source_type`    VARCHAR(32)  DEFAULT 'MANUAL'                COMMENT '来源: MANUAL/BANK_FLOW/INVOICE/AGENT',
  `source_ref_id`  VARCHAR(64)  DEFAULT NULL                     COMMENT '来源关联ID',
  `total_debit`    DECIMAL(18,2) NOT NULL DEFAULT 0.00           COMMENT '借方合计',
  `total_credit`   DECIMAL(18,2) NOT NULL DEFAULT 0.00           COMMENT '贷方合计',
  `entries_json`   TEXT         NOT NULL                         COMMENT '分录 JSON',
  `attachments`    TEXT         DEFAULT NULL                     COMMENT '附件JSON',
  `agent_task_id`  BIGINT       DEFAULT NULL                     COMMENT '生成该凭证的 Agent 任务ID',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'         COMMENT 'DRAFT/REVIEW/POSTED/REJECTED',
  `reviewed_by`    VARCHAR(64)  DEFAULT NULL                     COMMENT '审核人',
  `reviewed_time`  DATETIME     DEFAULT NULL                     COMMENT '审核时间',
  `posted_by`      VARCHAR(64)  DEFAULT NULL                     COMMENT '入账人',
  `posted_time`    DATETIME     DEFAULT NULL                     COMMENT '入账时间',
  `create_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  `remark`         VARCHAR(500) DEFAULT NULL                     COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voucher_code` (`voucher_code`),
  KEY `idx_company_period` (`company_id`, `period`),
  KEY `idx_voucher_date` (`voucher_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务凭证';

-- 银行流水（用于 AI 提取凭证）
DROP TABLE IF EXISTS `opc_finance_bank_flow`;
CREATE TABLE `opc_finance_bank_flow` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `flow_code`      VARCHAR(64)  NOT NULL                         COMMENT '流水编号',
  `company_id`     BIGINT       NOT NULL                         COMMENT '公司ID',
  `bank_account`   VARCHAR(64)  DEFAULT NULL                     COMMENT '银行账号',
  `bank_name`      VARCHAR(64)  DEFAULT NULL                     COMMENT '银行',
  `trade_time`     DATETIME     NOT NULL                         COMMENT '交易时间',
  `direction`      VARCHAR(8)   NOT NULL                         COMMENT 'IN-收入, OUT-支出',
  `amount`         DECIMAL(18,2) NOT NULL                        COMMENT '金额',
  `currency`       VARCHAR(8)   DEFAULT 'CNY'                    COMMENT '币种',
  `counter_party`  VARCHAR(128) DEFAULT NULL                     COMMENT '交易对手',
  `memo`           VARCHAR(255) DEFAULT NULL                     COMMENT '备注',
  `raw_text`       TEXT         DEFAULT NULL                     COMMENT '原始流水文本',
  `extracted`      TINYINT(1)   NOT NULL DEFAULT 0               COMMENT '是否已 AI 抽取',
  `voucher_id`     BIGINT       DEFAULT NULL                     COMMENT '关联凭证ID',
  `agent_task_id`  BIGINT       DEFAULT NULL                     COMMENT '抽取该流的 Agent 任务ID',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'IMPORTED'      COMMENT '状态: IMPORTED/PROCESSED/IGNORED',
  `create_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_code` (`flow_code`),
  KEY `idx_company_trade_time` (`company_id`, `trade_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='银行流水';

-- 报税记录
DROP TABLE IF EXISTS `opc_finance_tax_report`;
CREATE TABLE `opc_finance_tax_report` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `report_code`    VARCHAR(64)  NOT NULL                         COMMENT '申报编号',
  `company_id`     BIGINT       NOT NULL                         COMMENT '公司ID',
  `tax_type`       VARCHAR(32)  NOT NULL                         COMMENT '税种',
  `period`         VARCHAR(8)   NOT NULL                         COMMENT '所属期 YYYY-MM',
  `declaration_no` VARCHAR(64)  DEFAULT NULL                     COMMENT '申报流水号',
  `taxable_amount` DECIMAL(18,2) DEFAULT 0.00                   COMMENT '应税金额',
  `tax_amount`     DECIMAL(18,2) DEFAULT 0.00                   COMMENT '税额',
  `pay_amount`     DECIMAL(18,2) DEFAULT 0.00                   COMMENT '应缴',
  `paid_amount`    DECIMAL(18,2) DEFAULT 0.00                   COMMENT '已缴',
  `due_date`       DATE         DEFAULT NULL                     COMMENT '申报截止',
  `submit_time`    DATETIME     DEFAULT NULL                     COMMENT '提交时间',
  `agent_task_id`  BIGINT       DEFAULT NULL                     COMMENT 'Agent 任务',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'         COMMENT 'DRAFT/SUBMITTED/PAID',
  `attachments`    TEXT         DEFAULT NULL                     COMMENT '附件',
  `create_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_code` (`report_code`),
  KEY `idx_company_period` (`company_id`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报税记录';

-- ============================================================
-- 五、计费
-- ============================================================

-- 钱包
DROP TABLE IF EXISTS `opc_wallet`;
CREATE TABLE `opc_wallet` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `company_id`    BIGINT       NOT NULL                         COMMENT '公司ID',
  `user_id`       BIGINT       NOT NULL                         COMMENT '用户ID',
  `balance`       DECIMAL(18,4) NOT NULL DEFAULT 0.0000         COMMENT '余额（元）',
  `frozen`        DECIMAL(18,4) NOT NULL DEFAULT 0.0000         COMMENT '冻结金额',
  `total_recharge`DECIMAL(18,4) NOT NULL DEFAULT 0.0000         COMMENT '累计充值',
  `total_consume` DECIMAL(18,4) NOT NULL DEFAULT 0.0000         COMMENT '累计消费',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'        COMMENT '状态',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`   DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_user` (`company_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包';

-- 计费订单
DROP TABLE IF EXISTS `opc_billing_order`;
CREATE TABLE `opc_billing_order` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `order_no`      VARCHAR(64)  NOT NULL                         COMMENT '订单号',
  `company_id`    BIGINT       NOT NULL                         COMMENT '公司ID',
  `user_id`       BIGINT       NOT NULL                         COMMENT '用户ID',
  `order_type`    VARCHAR(32)  NOT NULL                         COMMENT '类型: RECHARGE/SUBSCRIPTION/CONSUME/REFUND',
  `biz_type`      VARCHAR(32)  DEFAULT NULL                     COMMENT '业务类型: AGENT_HIRE/MODULE_SUB/TOKEN_PACK',
  `biz_id`        BIGINT       DEFAULT NULL                     COMMENT '业务ID',
  `title`         VARCHAR(255) NOT NULL                         COMMENT '订单标题',
  `amount`        DECIMAL(18,2) NOT NULL                        COMMENT '订单金额',
  `discount`      DECIMAL(18,2) DEFAULT 0.00                    COMMENT '优惠',
  `paid_amount`   DECIMAL(18,2) NOT NULL                        COMMENT '实付',
  `pay_method`    VARCHAR(16)  DEFAULT 'WALLET'                 COMMENT '支付方式',
  `pay_status`    VARCHAR(16)  NOT NULL DEFAULT 'PENDING'       COMMENT '支付状态',
  `pay_time`      DATETIME     DEFAULT NULL                     COMMENT '支付时间',
  `pay_trade_no`  VARCHAR(64)  DEFAULT NULL                     COMMENT '支付流水',
  `expire_time`   DATETIME     DEFAULT NULL                     COMMENT '订单过期',
  `closed_time`   DATETIME     DEFAULT NULL                     COMMENT '关闭时间',
  `refund_amount` DECIMAL(18,2) DEFAULT 0.00                    COMMENT '已退款',
  `invoice_id`    BIGINT       DEFAULT NULL                     COMMENT '发票ID',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`   DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  `remark`        VARCHAR(500) DEFAULT NULL                     COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_pay_status` (`pay_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费订单';

-- 交易流水
DROP TABLE IF EXISTS `opc_transaction`;
CREATE TABLE `opc_transaction` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `tx_code`        VARCHAR(64)  NOT NULL                         COMMENT '流水号',
  `wallet_id`      BIGINT       NOT NULL                         COMMENT '钱包ID',
  `company_id`     BIGINT       NOT NULL                         COMMENT '公司ID',
  `tx_type`        VARCHAR(16)  NOT NULL                         COMMENT '类型: RECHARGE/CONSUME/REFUND/FREEZE/UNFREEZE',
  `amount`         DECIMAL(18,4) NOT NULL                        COMMENT '金额',
  `balance_before` DECIMAL(18,4) NOT NULL                        COMMENT '变动前余额',
  `balance_after`  DECIMAL(18,4) NOT NULL                        COMMENT '变动后余额',
  `biz_type`       VARCHAR(32)  DEFAULT NULL                     COMMENT '业务类型',
  `biz_id`         BIGINT       DEFAULT NULL                     COMMENT '业务ID',
  `description`    VARCHAR(255) DEFAULT NULL                     COMMENT '描述',
  `create_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tx_code` (`tx_code`),
  KEY `idx_wallet_id` (`wallet_id`),
  KEY `idx_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水';

-- 发票
DROP TABLE IF EXISTS `opc_billing_invoice`;
CREATE TABLE `opc_billing_invoice` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `invoice_no`  VARCHAR(64)  NOT NULL                         COMMENT '发票号',
  `company_id`  BIGINT       NOT NULL                         COMMENT '公司ID',
  `order_ids`   VARCHAR(512) NOT NULL                         COMMENT '订单ID集合（逗号分隔）',
  `invoice_type`VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'        COMMENT '类型: NORMAL/SPECIAL',
  `title`       VARCHAR(255) NOT NULL                         COMMENT '发票抬头',
  `tax_no`      VARCHAR(64)  DEFAULT NULL                     COMMENT '税号',
  `amount`      DECIMAL(18,2) NOT NULL                        COMMENT '金额',
  `tax_amount`  DECIMAL(18,2) DEFAULT 0.00                    COMMENT '税额',
  `total_amount`DECIMAL(18,2) NOT NULL                        COMMENT '价税合计',
  `email`       VARCHAR(128) DEFAULT NULL                     COMMENT '接收邮箱',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'PENDING'       COMMENT '状态: PENDING/ISSUED/FAILED',
  `issued_time` DATETIME     DEFAULT NULL                     COMMENT '开具时间',
  `pdf_url`     VARCHAR(255) DEFAULT NULL                     COMMENT '电子发票PDF',
  `create_by`   VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time` DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`   VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time` DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票';

-- 分佣记录
DROP TABLE IF EXISTS `opc_commission_record`;
CREATE TABLE `opc_commission_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `record_code`    VARCHAR(64)  NOT NULL                         COMMENT '分佣编号',
  `source_user_id` BIGINT       NOT NULL                         COMMENT '消费方用户ID',
  `source_company_id` BIGINT    NOT NULL                         COMMENT '消费方公司ID',
  `beneficiary_id` BIGINT       NOT NULL                         COMMENT '受益人ID（邀请人/服务商）',
  `order_id`       BIGINT       NOT NULL                         COMMENT '订单ID',
  `order_amount`   DECIMAL(18,2) NOT NULL                        COMMENT '订单金额',
  `commission_rate`DECIMAL(5,4) NOT NULL                        COMMENT '分佣比例',
  `commission_amount` DECIMAL(18,2) NOT NULL                    COMMENT '分佣金额',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING'       COMMENT '状态: PENDING/SETTLED/REVOKED',
  `settle_time`    DATETIME     DEFAULT NULL                     COMMENT '结算时间',
  `create_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`      VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_code` (`record_code`),
  KEY `idx_beneficiary_id` (`beneficiary_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分佣记录';

-- ============================================================
-- 六、运营 / 安全
-- ============================================================

-- 通知
DROP TABLE IF EXISTS `opc_notification`;
CREATE TABLE `opc_notification` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `user_id`     BIGINT       NOT NULL                         COMMENT '接收用户',
  `company_id`  BIGINT       DEFAULT NULL                     COMMENT '所属公司',
  `type`        VARCHAR(32)  NOT NULL                         COMMENT '类型: SYSTEM/AGENT/BILLING/FINANCE/SECURITY',
  `title`       VARCHAR(255) NOT NULL                         COMMENT '标题',
  `content`     TEXT         DEFAULT NULL                     COMMENT '内容',
  `link_url`    VARCHAR(255) DEFAULT NULL                     COMMENT '跳转链接',
  `priority`   VARCHAR(16)   DEFAULT 'NORMAL'                 COMMENT '优先级: LOW/NORMAL/HIGH/URGENT',
  `read_flag`   TINYINT(1)   NOT NULL DEFAULT 0               COMMENT '是否已读',
  `read_time`   DATETIME     DEFAULT NULL                     COMMENT '已读时间',
  `create_by`   VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time` DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `read_flag`),
  KEY `idx_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知';

-- 审计日志
DROP TABLE IF EXISTS `opc_audit_log`;
CREATE TABLE `opc_audit_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `log_code`     VARCHAR(64)  NOT NULL                         COMMENT '日志编号',
  `company_id`   BIGINT       DEFAULT NULL                     COMMENT '公司ID',
  `user_id`      BIGINT       DEFAULT NULL                     COMMENT '操作用户',
  `user_name`    VARCHAR(64)  DEFAULT NULL                     COMMENT '操作人',
  `module`       VARCHAR(64)  NOT NULL                         COMMENT '模块',
  `action`       VARCHAR(64)  NOT NULL                         COMMENT '动作',
  `biz_type`     VARCHAR(32)  DEFAULT NULL                     COMMENT '业务类型',
  `biz_id`       VARCHAR(64)  DEFAULT NULL                     COMMENT '业务ID',
  `request_url`  VARCHAR(255) DEFAULT NULL                     COMMENT '请求URL',
  `request_method` VARCHAR(8) DEFAULT NULL                    COMMENT 'HTTP方法',
  `request_param` TEXT        DEFAULT NULL                     COMMENT '请求参数',
  `response_summary` TEXT      DEFAULT NULL                     COMMENT '响应摘要',
  `ip`           VARCHAR(64)  DEFAULT NULL                     COMMENT 'IP',
  `user_agent`   VARCHAR(512) DEFAULT NULL                     COMMENT 'UA',
  `risk_level`   VARCHAR(16)  DEFAULT 'LOW'                    COMMENT '风险等级: LOW/MIDDLE/HIGH',
  `success`      TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '是否成功',
  `error_message` TEXT        DEFAULT NULL                     COMMENT '错误信息',
  `duration_ms`  INT          DEFAULT NULL                     COMMENT '耗时',
  `create_time`  DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_code` (`log_code`),
  KEY `idx_company_user` (`company_id`, `user_id`),
  KEY `idx_module_action` (`module`, `action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- 邀请码
DROP TABLE IF EXISTS `opc_invitation`;
CREATE TABLE `opc_invitation` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `invite_code`   VARCHAR(32)  NOT NULL                         COMMENT '邀请码',
  `inviter_id`    BIGINT       NOT NULL                         COMMENT '邀请人',
  `invitee_id`    BIGINT       DEFAULT NULL                     COMMENT '被邀请人',
  `invitee_mobile`VARCHAR(20)  DEFAULT NULL                     COMMENT '被邀请手机号',
  `max_uses`      INT          NOT NULL DEFAULT 1               COMMENT '最大使用次数',
  `used_count`    INT          NOT NULL DEFAULT 0               COMMENT '已使用',
  `expire_time`   DATETIME     DEFAULT NULL                     COMMENT '过期',
  `used_time`     DATETIME     DEFAULT NULL                     COMMENT '使用时间',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'        COMMENT '状态',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`   DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_inviter_id` (`inviter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码';

-- ============================================================
-- 七、Prompt 与评测
-- ============================================================

-- Prompt 模板版本
DROP TABLE IF EXISTS `opc_prompt_template`;
CREATE TABLE `opc_prompt_template` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `template_code`VARCHAR(64)  NOT NULL                         COMMENT '模板编码',
  `agent_code`   VARCHAR(64)  DEFAULT NULL                     COMMENT '关联 Agent 编码',
  `scene`        VARCHAR(64)  NOT NULL                         COMMENT '场景: SYSTEM/EXTRACT/SUMMARIZE/CLASSIFY/CHAT',
  `version`      VARCHAR(16)  NOT NULL                         COMMENT '版本号',
  `content`      MEDIUMTEXT   NOT NULL                         COMMENT '模板内容',
  `variables`    VARCHAR(512) DEFAULT NULL                     COMMENT '变量名列表',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '启用',
  `published`    TINYINT(1)   NOT NULL DEFAULT 0               COMMENT '发布',
  `test_score`   DECIMAL(5,2) DEFAULT NULL                    COMMENT '评测得分',
  `create_by`    VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`  DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT ''                       COMMENT '更新者',
  `update_time`  DATETIME     DEFAULT NULL                     COMMENT '更新时间',
  `remark`       VARCHAR(500) DEFAULT NULL                     COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_scene_version` (`template_code`, `scene`, `version`),
  KEY `idx_agent_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板';

-- Agent 评测集
DROP TABLE IF EXISTS `opc_agent_eval_case`;
CREATE TABLE `opc_agent_eval_case` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `case_code`   VARCHAR(64)  NOT NULL                         COMMENT '用例编码',
  `agent_code`  VARCHAR(64)  NOT NULL                         COMMENT 'Agent 编码',
  `scene`       VARCHAR(64)  NOT NULL                         COMMENT '场景',
  `input`       TEXT         NOT NULL                         COMMENT '输入',
  `expected`    TEXT         DEFAULT NULL                     COMMENT '期望输出',
  `difficulty`  VARCHAR(16)  DEFAULT 'NORMAL'                 COMMENT '难度: EASY/NORMAL/HARD',
  `tags`        VARCHAR(255) DEFAULT NULL                     COMMENT '标签',
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1               COMMENT '启用',
  `create_by`   VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time` DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_case_code` (`case_code`),
  KEY `idx_agent_scene` (`agent_code`, `scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测用例';

-- 评测结果
DROP TABLE IF EXISTS `opc_agent_eval_result`;
CREATE TABLE `opc_agent_eval_result` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键',
  `eval_code`     VARCHAR(64)  NOT NULL                         COMMENT '评测编号',
  `agent_code`    VARCHAR(64)  NOT NULL                         COMMENT 'Agent 编码',
  `model`         VARCHAR(32)  NOT NULL                         COMMENT '使用模型',
  `prompt_version`VARCHAR(32)  NOT NULL                         COMMENT 'Prompt 版本',
  `total`         INT          NOT NULL                         COMMENT '总数',
  `passed`        INT          NOT NULL                         COMMENT '通过数',
  `pass_rate`     DECIMAL(5,4) NOT NULL                        COMMENT '通过率',
  `avg_latency_ms`INT          DEFAULT NULL                     COMMENT '平均耗时',
  `total_tokens`  INT          DEFAULT NULL                     COMMENT '总Token',
  `total_cost`    DECIMAL(10,4) DEFAULT 0.0000                  COMMENT '总花费',
  `detail_json`   MEDIUMTEXT   DEFAULT NULL                     COMMENT '详细结果',
  `start_time`    DATETIME     DEFAULT NULL                     COMMENT '开始',
  `end_time`      DATETIME     DEFAULT NULL                     COMMENT '结束',
  `operator`      VARCHAR(64)  DEFAULT NULL                     COMMENT '操作人',
  `create_by`     VARCHAR(64)  DEFAULT ''                       COMMENT '创建者',
  `create_time`   DATETIME     DEFAULT NULL                     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_eval_code` (`eval_code`),
  KEY `idx_agent_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测结果';

-- ============================================================
-- 八、字典 / 系统配置
-- ============================================================

-- 字典（继承自 sys_dict_type / sys_dict_data，扩展 OPC 专属字典）

-- ============================================================
-- 初始数据：Agent 模板、Prompt 模板、字典
-- ============================================================

-- Agent 定义模板（财务 Agent）
INSERT INTO `opc_agent_definition`
  (`agent_code`, `name`, `version`, `category`, `description`, `capabilities`, `system_prompt`, `primary_model`, `fallback_model`, `price_monthly`, `price_yearly`, `free_quota`, `tags`, `published`, `sort_order`, `status`, `create_by`, `create_time`)
VALUES
  ('finance-agent', '财务数字员工-小财', 'v1.0', 'FINANCE',
   '为一人公司提供智能记账、自动凭证、对账、报税、日报等全流程财务服务。',
   '["智能记账","自动生成凭证","对账平账","报税测算","日报生成","异常预警"]',
   '你是一位专业、严谨、值得信赖的财务数字员工，名叫"小财"。\n你的职责是帮助一人公司主完成日常财务工作，包括但不限于：\n1. 识别银行/支付流水并生成会计凭证\n2. 提供月度/季度税务测算建议\n3. 生成每日财务经营简报\n4. 识别异常交易并预警\n\n工作准则：\n- 涉及金额必须精确到分\n- 不确定时务必标注"待人工审核"\n- 严禁凭空捏造凭证号、税额\n- 输出请使用结构化 JSON',
   'deepseek-v3', 'gpt-4o-mini',
   99.00, 999.00, 5000, '财务,记账,AI,OPC', 1, 100, 'ACTIVE', 'admin', NOW()),

  ('finance-agent-pro', '财务数字员工-小财 Pro', 'v1.0', 'FINANCE',
   'Pro 版财务数字员工：增加多账户对账、年度汇算清缴、行业税务筹划。',
   '["智能记账","自动凭证","多账户对账","年度汇算","税务筹划","风险扫描","日报生成"]',
   '你是"小财 Pro"——高级财务数字员工。具备 CPA 与税务师双重知识背景。\n除标准记账/凭证能力外，擅长多账户对账、年度汇算清缴、行业税务筹划、风险扫描。',
   'deepseek-v3', 'gpt-4o-mini',
   299.00, 2999.00, 20000, '财务,Pro,对账,税务筹划', 1, 101, 'ACTIVE', 'admin', NOW()),

  ('erp-agent', 'ERP 数字员工-小仓', 'v1.0', 'ERP',
   '进销存数字员工：出入库、库存预警、采购建议。',
   '["智能出入库","库存预警","采购建议","盘点辅助"]',
   '你是"小仓"——进销存数字员工。负责出入库登记、库存预警、采购建议。',
   'deepseek-v3', 'gpt-4o-mini',
   69.00, 699.00, 3000, 'ERP,进销存,库存', 1, 200, 'ACTIVE', 'admin', NOW()),

  ('crm-agent', 'CRM 数字员工-小客', 'v1.0', 'CRM',
   '客户管理数字员工：客户画像、销售跟进、流失预警。',
   '["客户画像","销售跟进","智能提醒","流失预警"]',
   '你是"小客"——客户管理数字员工。帮助老板管理客户、跟进商机、识别流失风险。',
   'deepseek-v3', 'gpt-4o-mini',
   69.00, 699.00, 3000, 'CRM,客户,销售', 1, 300, 'ACTIVE', 'admin', NOW());

-- 模块分类
INSERT INTO `opc_module_category` (`category_code`, `name`, `sort_order`, `enabled`, `create_by`, `create_time`)
VALUES
  ('finance', '财务税务', 1, 1, 'admin', NOW()),
  ('erp', '进销存', 2, 1, 'admin', NOW()),
  ('crm', '客户营销', 3, 1, 'admin', NOW()),
  ('hr', '人力服务', 4, 1, 'admin', NOW()),
  ('ecom', '跨境电商', 5, 1, 'admin', NOW()),
  ('content', '内容创作', 6, 1, 'admin', NOW()),
  ('voice', '语音外呼', 7, 1, 'admin', NOW()),
  ('insight', '数据洞察', 8, 1, 'admin', NOW());

-- Prompt 模板
INSERT INTO `opc_prompt_template` (`template_code`, `agent_code`, `scene`, `version`, `content`, `variables`, `enabled`, `published`, `create_by`, `create_time`)
VALUES
  ('finance-system', 'finance-agent', 'SYSTEM', 'v1.0',
   '# 角色\n你是"小财"，一人公司的财务数字员工。\n\n# 工作准则\n1. 所有金额精确到分（保留两位小数）\n2. 不确定的科目/税率必须标注"待人工审核"\n3. 严禁伪造凭证号、税额、合同号\n4. 输出结构化 JSON 便于系统解析\n\n# 输出格式\n```json\n{"summary":"...", "entries":[...], "confidence":0.0-1.0, "need_review":true/false}\n```',
   '', 1, 1, 'admin', NOW()),

  ('finance-extract', 'finance-agent', 'EXTRACT', 'v1.0',
   '请从以下银行流水中提取会计要素：\n```\n{{raw_text}}\n```\n\n要求：\n- direction: IN / OUT\n- amount: 精确金额\n- counter_party: 对方户名或公司\n- subject_code: 参考科目（如 1001 库存现金、1002 银行存款、1122 应收账款、2202 应付账款、6001 主营业务收入、6401 主营业务成本 等）\n- tax_rate: 若涉及增值税给出税率\n- summary: 摘要\n- confidence: 置信度 0-1\n- need_review: 是否需要人工复核',
   '{{raw_text}}', 1, 1, 'admin', NOW()),

  ('finance-daily', 'finance-agent', 'SUMMARIZE', 'v1.0',
   '请基于以下数据生成 {{date}} 的财务日报：\n今日收入：{{today_revenue}} 元\n今日支出：{{today_expense}} 元\n本月累计收入：{{month_revenue}} 元\n本月累计支出：{{month_expense}} 元\n待审核凭证：{{pending_review}} 张\n异常交易：{{anomaly_count}} 笔\n\n要求 Markdown 输出，包含：\n1. 核心指标（4 个）\n2. 今日亮点（最多 3 条）\n3. 风险预警（最多 3 条）\n4. 明日建议（最多 3 条）',
   '{{date}},{{today_revenue}},{{today_expense}},{{month_revenue}},{{month_expense}},{{pending_review}},{{anomaly_count}}',
   1, 1, 'admin', NOW());

-- 字典数据：Agent 分类
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('Agent 分类', 'opc_agent_category', '0', 'admin', NOW(), 'Agent 业务分类');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (1, '财务税务', 'FINANCE', 'opc_agent_category', '', 'primary', 'N', '0', 'admin', NOW(), ''),
  (2, '进销存', 'ERP',     'opc_agent_category', '', 'success', 'N', '0', 'admin', NOW(), ''),
  (3, '客户营销', 'CRM',    'opc_agent_category', '', 'warning', 'N', '0', 'admin', NOW(), ''),
  (4, '人力服务', 'HR',     'opc_agent_category', '', 'info',    'N', '0', 'admin', NOW(), ''),
  (5, '跨境电商', 'ECOM',   'opc_agent_category', '', 'danger',  'N', '0', 'admin', NOW(), ''),
  (6, '内容创作', 'CONTENT','opc_agent_category', '', 'primary', 'N', '0', 'admin', NOW(), ''),
  (7, '语音外呼', 'VOICE',  'opc_agent_category', '', 'success', 'N', '0', 'admin', NOW(), ''),
  (8, '数据洞察', 'INSIGHT','opc_agent_category', '', 'warning', 'N', '0', 'admin', NOW(), '');

INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('用户类型', 'opc_user_type', '0', 'admin', NOW(), 'OPC 用户画像类型');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (1, '创业者', 'ENTREPRENEUR', 'opc_user_type', '', 'primary', 'Y', '0', 'admin', NOW(), ''),
  (2, '服务商', 'SERVICE',      'opc_user_type', '', 'success', 'N', '0', 'admin', NOW(), ''),
  (3, '企业主', 'OWNER',        'opc_user_type', '', 'warning', 'N', '0', 'admin', NOW(), ''),
  (4, '投资者', 'INVESTOR',     'opc_user_type', '', 'info',    'N', '0', 'admin', NOW(), '');

INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('订阅类型', 'opc_hire_type', '0', 'admin', NOW(), 'Agent 雇佣类型');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (1, '月付', 'MONTHLY',    'opc_hire_type', '', 'primary', 'Y', '0', 'admin', NOW(), ''),
  (2, '季付', 'QUARTERLY', 'opc_hire_type', '', 'success', 'N', '0', 'admin', NOW(), ''),
  (3, '年付', 'YEARLY',    'opc_hire_type', '', 'warning', 'N', '0', 'admin', NOW(), ''),
  (4, '试用', 'TRIAL',     'opc_hire_type', '', 'info',    'N', '0', 'admin', NOW(), '');

-- 评测用例（10 个示例）
INSERT INTO `opc_agent_eval_case` (`case_code`, `agent_code`, `scene`, `input`, `expected`, `difficulty`, `tags`, `enabled`, `create_by`, `create_time`)
VALUES
  ('eval-001', 'finance-agent', 'EXTRACT',
   '2026-09-03 10:23 支付宝收款 客户A有限公司 转账 12680.00 元 备注 货款',
   '{"direction":"IN","amount":12680.00,"counter_party":"客户A有限公司","subject_code":"1122","summary":"收客户A货款"}',
   'EASY', '收入,货款', 1, 'admin', NOW()),
  ('eval-002', 'finance-agent', 'EXTRACT',
   '2026-09-03 14:55 微信支出 -1500.00 深圳市南山区税务局 代扣个税',
   '{"direction":"OUT","amount":1500.00,"counter_party":"税务局","subject_code":"2221","summary":"代扣个税"}',
   'EASY', '支出,税费', 1, 'admin', NOW()),
  ('eval-003', 'finance-agent', 'EXTRACT',
   '2026-09-03 09:00 银联收款 京东商城 退货款 3280.50',
   '{"direction":"IN","amount":3280.50,"counter_party":"京东商城","subject_code":"1122","summary":"京东退货款"}',
   'NORMAL', '退货', 1, 'admin', NOW()),
  ('eval-004', 'finance-agent', 'EXTRACT',
   '2026-09-03 16:30 转出 85000.00 对方 深圳市某某咨询有限公司 用途 服务费',
   '{"direction":"OUT","amount":85000.00,"counter_party":"深圳市某某咨询有限公司","subject_code":"6602","summary":"支付服务费","need_review":true}',
   'HARD', '大额,服务费', 1, 'admin', NOW()),
  ('eval-005', 'finance-agent', 'EXTRACT',
   '2026-09-03 11:11 银行收款 100.00 红包',
   '{"direction":"IN","amount":100.00,"subject_code":"6603","summary":"收到红包","need_review":true}',
   'NORMAL', '小额,红包', 1, 'admin', NOW()),
  ('eval-006', 'finance-agent', 'SUMMARIZE',
   '{"date":"2026-09-03","today_revenue":126800,"today_expense":45200,"month_revenue":1234500,"month_expense":876200,"pending_review":3,"anomaly_count":2}',
   NULL,
   'NORMAL', '日报', 1, 'admin', NOW()),
  ('eval-007', 'finance-agent', 'EXTRACT',
   '2026-09-03 19:30 信用卡还款 -3200.00',
   '{"direction":"OUT","amount":3200.00,"subject_code":"6602","summary":"信用卡还款","need_review":true}',
   'EASY', '信用卡', 1, 'admin', NOW()),
  ('eval-008', 'finance-agent', 'EXTRACT',
   '2026-09-03 08:00 工资发放 -25000.00 备注 9月工资',
   '{"direction":"OUT","amount":25000.00,"subject_code":"2211","summary":"发放9月工资"}',
   'NORMAL', '工资', 1, 'admin', NOW()),
  ('eval-009', 'finance-agent', 'EXTRACT',
   '2026-09-03 15:45 收到 滴滴出行 充值退款 88.00',
   '{"direction":"IN","amount":88.00,"counter_party":"滴滴出行","subject_code":"6603","summary":"滴滴退款"}',
   'NORMAL', '退款', 1, 'admin', NOW()),
  ('eval-010', 'finance-agent', 'EXTRACT',
   '2026-09-03 22:00 支付宝转出 999999.00',
   '{"direction":"OUT","amount":999999.00,"subject_code":"","summary":"异常大额转出","need_review":true,"confidence":0.3}',
   'HARD', '异常,大额', 1, 'admin', NOW());

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 结束
-- ============================================================
