-- ============================================================
-- opc-content schema (W74 · opc-content 9325)
-- 4 表: script / platform_account / publish / adapt
-- 端口 9325, namespace opc-dev
-- 注意: 列名以 MyBatis mapper XML 为准 (snake_case),
--       task 规划文档中的 `tags_json` / `external_id` / `encrypted_access_token`
--       等已被 mapper 替换为 `tags` / `external_video_id` / `access_token_enc`
--       等命名,这里 DDL 与 mapper 完全对齐
-- IF NOT EXISTS 幂等,适配 MySQL init 容器 (W48.2 教训)
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ry-vue-opc` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ry-vue-opc`;

-- -----------------------------------------------------------------------------
-- 1) 脚本主表 (含 LLM 生成内容)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_content_script` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
  `company_id`       BIGINT UNSIGNED NOT NULL                        COMMENT '所属企业',
  `user_id`          BIGINT UNSIGNED NOT NULL                        COMMENT '所属用户',
  `type`             VARCHAR(32)    NOT NULL                         COMMENT '类型:DRAMA/VIDEO/ARTICLE/ADAPTER',
  `title`            VARCHAR(255)   NOT NULL                         COMMENT '脚本标题',
  `prompt_input`     MEDIUMTEXT                                          COMMENT '生成时输入 prompt',
  `content_json`     MEDIUMTEXT                                          COMMENT '结构化内容',
  `content_md`       MEDIUMTEXT                                          COMMENT '可读 markdown 文本',
  `word_count`       INT            NOT NULL DEFAULT 0                COMMENT '字数',
  `status`           VARCHAR(32)    NOT NULL DEFAULT 'DRAFT'          COMMENT 'DRAFT/READY/PUBLISHED/FAILED/DELETED',
  `source_script_id` BIGINT UNSIGNED                                    COMMENT '适配来源脚本 ID (ADAPTER 类型专用)',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_company_status` (`company_id`, `status`),
  KEY `idx_company_type`   (`company_id`, `type`),
  KEY `idx_user_created`   (`user_id`, `created_at`),
  KEY `idx_source_script`  (`source_script_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 内容脚本';

-- -----------------------------------------------------------------------------
-- 2) 平台账号 (含加密 token)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_content_platform_account` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
  `company_id`           BIGINT UNSIGNED NOT NULL                        COMMENT '所属企业',
  `platform`             VARCHAR(32)    NOT NULL                         COMMENT 'DOUYIN/WECHAT/XIAOHONGSHU/...',
  `account_name`         VARCHAR(128)   NOT NULL                         COMMENT '账号展示名 (昵称)',
  `account_id`           VARCHAR(128)   NOT NULL                         COMMENT '平台账号 openId',
  `union_id`             VARCHAR(128)                                       COMMENT '平台 unionId (若平台有)',
  `access_token_enc`     TEXT           NOT NULL                         COMMENT 'Jasypt 加密 access_token',
  `refresh_token_enc`    TEXT           NOT NULL                         COMMENT 'Jasypt 加密 refresh_token',
  `expires_at`           DATETIME       NOT NULL                         COMMENT 'access_token 过期时间',
  `refresh_at`           DATETIME       NOT NULL                         COMMENT 'refresh_token 过期时间',
  `scope`                VARCHAR(512)                                       COMMENT '授权范围 (逗号分隔)',
  `avatar_url`           VARCHAR(512)                                       COMMENT '头像 URL',
  `status`               VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE'        COMMENT 'ACTIVE/REVOKED/EXPIRED',
  `created_at`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `bound_at`             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次绑定时间',
  `updated_at`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_account` (`platform`, `account_id`, `company_id`),
  KEY `idx_company_status` (`company_id`, `status`),
  KEY `idx_expires_at`     (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号绑定';

-- -----------------------------------------------------------------------------
-- 3) 发布记录
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_content_publish` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
  `company_id`           BIGINT UNSIGNED NOT NULL                        COMMENT '所属企业',
  `script_id`            BIGINT UNSIGNED NOT NULL                        COMMENT '脚本 ID',
  `platform_account_id`  BIGINT UNSIGNED NOT NULL                        COMMENT '平台账号 ID (FK opc_content_platform_account.id)',
  `platform`             VARCHAR(32)    NOT NULL                         COMMENT 'DOUYIN/WECHAT/...',
  `title`                VARCHAR(255)                                       COMMENT '发布标题',
  `tags`                 VARCHAR(1024)                                      COMMENT '标签 (逗号分隔字符串,非 JSON)',
  `external_video_id`    VARCHAR(256)                                       COMMENT '平台视频 ID (视频类)',
  `external_post_id`     VARCHAR(256)                                       COMMENT '平台帖子 ID (图文类)',
  `external_url`         VARCHAR(512)                                       COMMENT '内容访问 URL',
  `status`               VARCHAR(32)    NOT NULL DEFAULT 'PENDING'        COMMENT 'PENDING/SUCCESS/FAILED/CANCELLED',
  `error_code`           VARCHAR(64)                                        COMMENT '平台错误码',
  `error_message`        VARCHAR(1024)                                      COMMENT '失败原因',
  `published_at`         DATETIME                                           COMMENT '实际发布时间',
  `created_at`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_script_status`   (`script_id`, `status`),
  KEY `idx_company_status`  (`company_id`, `status`),
  KEY `idx_platform_status` (`platform`, `status`),
  KEY `idx_pa_platform`     (`platform_account_id`, `platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发布记录';

-- -----------------------------------------------------------------------------
-- 4) 平台适配 (从源脚本生成目标平台风格脚本)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_content_adapt` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
  `company_id`           BIGINT UNSIGNED NOT NULL                        COMMENT '所属企业',
  `source_script_id`     BIGINT UNSIGNED NOT NULL                        COMMENT '源脚本 ID',
  `adapted_script_id`    BIGINT UNSIGNED                                    COMMENT '适配后生成的脚本 ID (NULL = 仅生成草稿)',
  `target_platform`      VARCHAR(32)    NOT NULL                         COMMENT '目标平台',
  `tone`                 VARCHAR(64)                                        COMMENT '语气风格 (如 轻松幽默 / 专业严谨)',
  `hashtags`             VARCHAR(1024)                                      COMMENT '推荐标签',
  `note`                 VARCHAR(1024)                                      COMMENT '备注',
  `created_at`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_source_script`  (`source_script_id`),
  KEY `idx_adapted_script` (`adapted_script_id`),
  KEY `idx_company_target` (`company_id`, `target_platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台适配';
