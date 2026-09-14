-- ============================================================
-- opc-hr seed (W71 · initdb 第 97 步)
-- 5 JD + 5 候选人 + 5 投递 + 2 面试 + 1 offer
-- company_id=1 (admin 默认公司)
-- ============================================================

INSERT IGNORE INTO opc_hr_job (id, company_id, created_by, title, category, description, full_jd, skills_json, salary_min, salary_max, location, status, publish_at) VALUES
(100001, 1, 1, '高级 Java 工程师', 'TECH',
 '负责 OPC 微服务后端开发，Spring Boot 3 + Nacos + MyBatis',
 '岗位职责：\n1. 负责 OPC 微服务平台后端架构设计与开发\n2. 基于 Spring Boot 3 + Spring Cloud 构建高可用服务\n3. MyBatis 数据层设计与优化\n4. 配合 AI 团队完成 Agent 接入\n\n任职要求：\n1. 5 年以上 Java 后端开发经验\n2. 熟悉 Spring Boot、Spring Cloud、Nacos、Sentinel\n3. 熟悉 MyBatis、Redis、MySQL\n4. 有微服务/分布式系统实战经验',
 '["Java","Spring Boot","Spring Cloud","MyBatis","MySQL","Redis","Nacos"]',
 25000, 45000, '北京',
 'OPEN', DATE_SUB(NOW(), INTERVAL 7 DAY)),

(100002, 1, 1, '资深前端工程师 (Vue3)', 'TECH',
 '负责 OPC 管理后台前端开发，Vue 3 + TypeScript + Element Plus',
 '岗位职责：\n1. Vue 3 + TypeScript 后台系统开发\n2. Element Plus 组件库深度使用\n3. 移动端响应式适配 (768px breakpoint)\n4. 与后端联调 REST API\n\n任职要求：\n1. 4 年以上 Vue 开发经验\n3. 熟悉 TypeScript、Vite、Pinia\n4. 熟悉 Element Plus',
 '["Vue 3","TypeScript","Element Plus","Vite","Pinia","Responsive"]',
 22000, 40000, '上海',
 'OPEN', DATE_SUB(NOW(), INTERVAL 5 DAY)),

(100003, 1, 1, 'AI 工程师 (LLM 应用)', 'TECH',
 '负责 Agent / Prompt 工程 / RAG 系统开发',
 '岗位职责：\n1. LLM 应用开发（DeepSeek / MiniMax / Claude）\n2. Prompt 设计与 Few-shot 调优\n3. RAG 检索增强生成\n4. 红队测试与安全加固\n\n任职要求：\n1. 3 年以上 NLP/LLM 经验\n2. 熟悉 LangChain / Spring AI\n3. 有 Eval 集与 Red Team 经验',
 '["LLM","DeepSeek","Prompt","RAG","LangChain","Red Team"]',
 30000, 60000, '北京',
 'OPEN', DATE_SUB(NOW(), INTERVAL 3 DAY)),

(100004, 1, 1, '销售经理 (SaaS)', 'SALES',
 '负责 OPC SaaS 产品销售，对接中小客户',
 '岗位职责：\n1. 客户开发与拜访\n2. 销售漏斗管理\n3. 合同谈判与签单\n\n任职要求：\n1. 3 年以上 SaaS 销售经验\n2. 熟悉 CRM 系统',
 '["SaaS销售","CRM","客户开发","合同谈判"]',
 15000, 30000, '深圳',
 'OPEN', DATE_SUB(NOW(), INTERVAL 2 DAY)),

(100005, 1, 1, '财务顾问', 'FINANCE',
 '负责税务报表生成与凭证审核',
 '岗位职责：\n1. 增值税月度申报\n2. 凭证审核与记账\n3. 财务报表分析\n\n任职要求：\n1. 财务相关专业本科\n2. 熟悉金税系统',
 '["增值税","金税","凭证","财务报表"]',
 12000, 20000, '广州',
 'DRAFT', NULL),

(100006, 1, 1, '社区运营', 'MARKETING',
 '负责 OPC 模块市场运营',
 '岗位职责：\n1. 模块市场内容运营\n2. 用户社区维护\n3. KOL 合作',
 '["社区运营","KOL","内容运营"]',
 10000, 18000, '杭州',
 'CLOSED', DATE_SUB(NOW(), INTERVAL 30 DAY));

INSERT IGNORE INTO opc_hr_candidate (id, company_id, name, email, phone, resume_url, source, tags_json, created_by) VALUES
(200001, 1, '张明', 'zhangming@example.com', '13800001001',
 'https://resume.opc.local/zhangming.pdf', 'WEBSITE',
 '["Java","Spring Cloud","5年经验"]', 1),

(200002, 1, '李雪', 'lixue@example.com', '13800001002',
 'https://resume.opc.local/lixue.pdf', 'REFERRAL',
 '["Vue 3","TypeScript","Element Plus"]', 1),

(200003, 1, '王伟', 'wangwei@example.com', '13800001003',
 'https://resume.opc.local/wangwei.pdf', 'LINKEDIN',
 '["LLM","DeepSeek","Prompt"]', 1),

(200004, 1, '陈丽', 'chenli@example.com', '13800001004',
 'https://resume.opc.local/chenli.pdf', 'BOSS',
 '["SaaS销售","CRM"]', 1),

(200005, 1, '刘强', 'liuqiang@example.com', '13800001005',
 'https://resume.opc.local/liuqiang.pdf', 'LAGOU',
 '["财务","金税"]', 1);

-- 5 applications (one per candidate)
INSERT IGNORE INTO opc_hr_application (id, company_id, job_id, candidate_id, channel, score, score_reason, status, applied_at) VALUES
(300001, 1, 100001, 200001, 'WEBSITE', 85, 'Java 经验丰富，匹配度高', 'INTERVIEW', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(300002, 1, 100002, 200002, 'REFERRAL', 90, 'Vue 3 技能突出', 'OFFER', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(300003, 1, 100003, 200003, 'LINKEDIN', 78, 'LLM 背景良好但缺 RAG 项目', 'SCREENING', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(300004, 1, 100004, 200004, 'BOSS', 70, '有 SaaS 经验', 'NEW', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(300005, 1, 100005, 200005, 'LAGOU', 65, '财务背景匹配', 'NEW', NOW());

-- 2 interviews
INSERT IGNORE INTO opc_hr_interview (id, company_id, application_id, round, type, interviewer_id, scheduled_at, duration_min, feedback, result) VALUES
(400001, 1, 300001, 1, 'VIDEO', 1, DATE_ADD(NOW(), INTERVAL 2 DAY), 60, '技术扎实，沟通良好', 'PASS'),
(400002, 1, 300002, 1, 'VIDEO', 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 45, 'Vue 3 经验 5 年', 'PASS');

-- 1 offer
INSERT IGNORE INTO opc_hr_offer (id, company_id, application_id, salary, start_date, expire_at, status, sent_at) VALUES
(500001, 1, 300002, 32000.00, DATE_ADD(CURDATE(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 'PENDING', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 3 match scores
INSERT IGNORE INTO opc_hr_match_score (id, company_id, job_id, candidate_id, score, reason) VALUES
(600001, 1, 100001, 200001, 85, 'Java + Spring Cloud 5 年经验，匹配度高'),
(600002, 1, 100002, 200002, 90, 'Vue 3 + TypeScript + Element Plus 深度经验'),
(600003, 1, 100003, 200003, 78, 'LLM 背景良好但缺 RAG 实战');