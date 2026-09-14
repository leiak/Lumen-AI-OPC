-- ============================================================
-- opc-content seed (W74 · dev only,生产不导入)
-- 3 脚本 + 1 平台账号 + 1 发布 + 1 适配
-- 列名对齐 mapper XML (tags / external_video_id / access_token_enc / source_script_id / target_platform)
-- id 固定 1-6, 用 INSERT IGNORE 幂等,后续 dev 重置时不会冲突
-- ============================================================

USE `ry-vue-opc`;

-- 3 个示例脚本 (DRAMA/VIDEO/ARTICLE 各 1)
INSERT IGNORE INTO `opc_content_script`
  (`id`, `company_id`, `user_id`, `type`, `title`, `prompt_input`, `content_json`, `content_md`, `word_count`, `status`)
VALUES
  (1, 1, 1, 'DRAMA',   '示例短剧-都市奇缘',     '写一个 3 幕都市短剧',
   '{"synopsis":"都市青年奇遇","characters":[{"name":"林晓","age":28}],"scenes":3}',
   '# 都市奇缘\n## 第一幕 相遇...', 1200, 'READY'),
  (2, 1, 1, 'VIDEO',   '示例视频脚本-产品介绍', '生成 30 秒产品介绍视频脚本',
   '{"hook":"5 秒开场抓眼球","body":[{"shot":1,"duration_sec":5,"voiceover":"..."}]}',
   '# 30 秒产品介绍\n## 镜头 1...', 280, 'DRAFT'),
  (3, 1, 1, 'ARTICLE', '示例文章-Spring Boot 3 新特性', '写一篇 Spring Boot 3 升级博客',
   '{"summary":"Spring Boot 3 升级要点","sections":[]}',
   '# Spring Boot 3 新特性\n...', 1800, 'PUBLISHED');

-- 1 个 mock 平台账号 (dev 用,生产走真 OAuth)
INSERT IGNORE INTO `opc_content_platform_account`
  (`id`, `company_id`, `platform`, `account_name`, `account_id`, `union_id`,
   `access_token_enc`, `refresh_token_enc`, `expires_at`, `refresh_at`, `scope`, `status`)
VALUES
  (1, 1, 'DOUYIN', '测试抖音号', 'mock-douyin-account-001', 'mock-union-001',
   'ENC_PLACEHOLDER_ACCESS', 'ENC_PLACEHOLDER_REFRESH',
   DATE_ADD(NOW(), INTERVAL 2 HOUR),
   DATE_ADD(NOW(), INTERVAL 30 DAY),
   'video.create,video.upload', 'ACTIVE');

-- 1 个示例发布 (脚本 3 已 PUBLISHED)
INSERT IGNORE INTO `opc_content_publish`
  (`id`, `company_id`, `script_id`, `platform_account_id`, `platform`,
   `title`, `tags`, `external_video_id`, `external_url`, `status`, `published_at`)
VALUES
  (1, 1, 3, 1, 'DOUYIN', 'Spring Boot 3 新特性分享',
   'Java,Spring,Tech', 'mock-ext-video-001', 'https://www.douyin.com/video/mock-001',
   'SUCCESS', NOW());

-- 1 个示例适配 (脚本 1 → DOUYIN 风格,未生成草稿)
INSERT IGNORE INTO `opc_content_adapt`
  (`id`, `company_id`, `source_script_id`, `adapted_script_id`, `target_platform`,
   `tone`, `hashtags`, `note`)
VALUES
  (1, 1, 1, NULL, 'DOUYIN', '轻松幽默',
   '#都市奇缘,#短剧推荐,#都市情感', '抖音节奏优化,前 3 秒钩子强化');
