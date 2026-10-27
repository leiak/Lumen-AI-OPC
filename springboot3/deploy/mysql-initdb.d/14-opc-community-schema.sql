-- ============================================================
-- opc-community schema + seed (W52 MVP)
-- 3 表: opc_community_module / opc_community_comment / opc_community_rating
-- 11 个示例模块 (crm/finance/hr/agent/content 分类)
-- ============================================================

CREATE TABLE IF NOT EXISTS opc_community_module (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    description TEXT,
    owner_id BIGINT,
    owner_name VARCHAR(64),
    icon VARCHAR(256),
    rating DECIMAL(2,1) NOT NULL DEFAULT 0.0,
    rating_count INT NOT NULL DEFAULT 0,
    install_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    tags VARCHAR(256),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    KEY idx_category (category),
    KEY idx_status (status),
    KEY idx_install (install_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS opc_community_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    content TEXT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_module (module_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS opc_community_rating (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score DECIMAL(2,1) NOT NULL,
    review VARCHAR(512),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_module_user (module_id, user_id),
    KEY idx_module (module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============ Seed: 12 示例模块 ============
--
-- 计数器口径(rating / rating_count / install_count / comment_count):
--   这些列以前写的是 4.8/24/156 这种数字 —— 演示环境里一个都验证不了,
--   页面却拿它们当"有多少人在用"展示。现在全部按下面紧跟着插入的
--   opc_community_rating / opc_community_comment 真实行数填:
--     模块 1 → 2 条评价(5.0) + 2 条评论
--     模块 3 → 1 条评价(5.0) + 1 条评论
--     模块 8 → 1 条评价(4.5) + 1 条评论
--     模块 9 → 1 条评价(4.0) + 0 条评论
--   其余模块一律 0 —— 应用里每次评分/评论/安装都会自己维护这三个计数。
--   install_count 初始为 0:装模块要真的发生过一次,数字才该大于 0。
INSERT IGNORE INTO opc_community_module (code, name, category, description, owner_name, icon, rating, rating_count, install_count, comment_count, tags) VALUES
('crm-starter', 'CRM 入门套件', 'CRM', '客户档案 + 联系人 + 跟进记录 + 商机漏斗开箱即用', 'OPC Team', '🧲', 5.0, 1, 0, 2, '客户,商机,自动化'),
('crm-ai-score', 'CRM 智能评分', 'CRM', '基于 LLM 的商机成交概率评分,自动归类', 'AI Lab', '🤖', 0.0, 0, 0, 0, 'LLM,评分'),
('finance-voucher', '智能记账凭证', 'FINANCE', '银行流水自动生成凭证,支持发票匹配', '财务小助手', '📒', 5.0, 1, 0, 1, '记账,自动化'),
('finance-tax-bj', '北京税务申报', 'FINANCE', '增值税/附加税申报模板,一键导出 PDF', 'Tax Bot', '🏛️', 0.0, 0, 0, 0, '税务,北京'),
('hr-recruit', '招聘简历筛选', 'HR', '基于 LLM 的简历匹配,自动打分排序', 'HR AI', '👥', 0.0, 0, 0, 0, '招聘,AI'),
('hr-payroll', '工资条推送', 'HR', '每月工资条自动生成 + 微信推送', 'Payroll', '💰', 0.0, 0, 0, 0, '工资,通知'),
('agent-market', 'Agent 交易市场', 'AGENT', '浏览/租用 AI Agent,按用量计费', 'Agent Hub', '🤝', 0.0, 0, 0, 0, 'Agent,交易'),
('agent-coder', '代码助手 Agent', 'AGENT', '代码生成 + Review + 重构,Claude/GPT 双引擎', 'Dev Bot', '💻', 4.5, 1, 0, 1, '代码,AI'),
('content-poster', '社媒海报生成', 'CONTENT', '朋友圈/小红书/抖音一键生成', '创意工坊', '🎨', 4.0, 1, 0, 0, '海报,内容'),
('content-blog', '公众号排版', 'CONTENT', 'Markdown 自动排版,适配公众号 + 知乎', 'WritePro', '📝', 0.0, 0, 0, 0, '写作,排版'),
('insight-anomaly', '异常检测告警', 'INSIGHT', '财务/库存/订单异常 24h 推送', 'Insight', '🚨', 0.0, 0, 0, 0, '告警,异常'),
-- ecom-ops:开发者「明轩优选」上架的模块,marketplace 的 MODULE 商品
-- PUB-ECOM-001 挂的就是它(以前那个商品指向的是 id=1 的 'crm-starter',
-- 一个"电商运营助手"指着 CRM 模块)。owner 与 marketplace 的作者一致。
('ecom-ops', '电商运营助手', 'ECOM', '选品 + 直播排期 + 数据复盘,一人公司开箱即用', '明轩优选', '🛒', 0.0, 0, 0, 0, '电商,运营,一人公司');

-- 8 条 seed 评论
INSERT IGNORE INTO opc_community_comment (module_id, user_id, user_name, content) VALUES
(1, 1, 'admin', '非常好用,客户分级节省了大量时间!'),
(1, 1, 'admin', '希望增加微信小程序端'),
(3, 1, 'admin', '记账准确率 99%,推荐'),
(8, 1, 'admin', 'Claude 引擎质量比 GPT 高一些,期望支持 DeepSeek');

-- 4 条 seed 评分
INSERT IGNORE INTO opc_community_rating (module_id, user_id, score, review) VALUES
(1, 1, 5.0, '强烈推荐'),
(3, 1, 5.0, '财务部门最爱'),
(8, 1, 4.5, '代码建议很准'),
(9, 1, 4.0, '模板还可以再多');
