# opc-hr (HR / 招聘) 服务设计 Spec

- 日期：2026-09-11
- 状态：draft v1（待用户审批）
- 关联：roadmap §4.2 W71-W73 / [[opc-crm-w50]] 模板 / [[aiopc-llm-providers]] LLM 集成
- 后续：实施计划由 writing-plans skill 生成

## 1. Goal

为 OPC 用户提供「AI 增强的招聘 / 人事管理」一站式后端。**全面 v1** 范围包含：
1. 招聘需求（JD）生命周期管理
2. 候选人投递 → 评分（LLM）→ 面试 → Offer 状态机
3. 简历解析（LLM 自动提取结构化字段）
4. 候选人画像与匹配（向量召回）
5. HR 仪表盘（漏斗/转化率/平均招聘时长）

**Acceptance Criteria (5 条)**

- AC-1：创建 JD → 发布 → 收到 1 份候选人投递 → LLM 评分 → 推进到面试 → 发 Offer 全流程在 E2E 通过
- AC-2：JD 生成走 LLM（输入业务描述 + 职位类别，输出完整 JD 文本 + 技能要求列表）
- AC-3：简历解析走 LLM（PDF/DOCX/MD 输入，输出结构化 JSON：基本信息 / 教育 / 工作经历 / 技能 / 项目）
- AC-4：候选人匹配 = 「JD 技能向量召回 top-K」+ LLM 重排序
- AC-5：HR 仪表盘返回近 30 天漏斗（JD / 投递 / 面试 / Offer / 入职 5 段转化率）+ 平均招聘时长

## 2. 关键约束 / 范围

**In**：
- 6 张新表（hr_job / hr_candidate / hr_application / hr_interview / hr_offer / hr_match_score）
- ~25 REST endpoints
- 4 个 Feign 调用（notification / user-center / ai-core / crm）
- 2 个 LLM prompt（JD 生成、简历解析、候选人评分）
- Vue 3 前端 5 页（JD 列表 / JD 详情 / 候选人列表 / 候选人详情 / 仪表盘）
- 端口 9322（roadmap 保留）

**Out**（不做）：
- 与外部招聘网站（Boss 直聘 / 拉勾）对接
- 简历 PDF 生成（用 Markdown 输出 + 前端 html2canvas 渲染）
- 工资条 / 考勤 / 绩效模块（属于「人事管理」，不在 v1 招聘范围）
- 多语言（只中文）

## 3. 数据模型（6 表）

```sql
-- 招聘需求（JD）
CREATE TABLE opc_hr_job (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  created_by      BIGINT NOT NULL,
  title           VARCHAR(128) NOT NULL,
  category        VARCHAR(32) NOT NULL,        -- TECH / SALES / OPERATION / FINANCE / MARKETING
  description     TEXT NOT NULL,                -- 业务描述,LLM 改写为完整 JD
  full_jd         TEXT,                         -- LLM 生成的完整 JD
  skills_json     JSON,                         -- LLM 提取的技能要求列表
  salary_min      DECIMAL(12,2),
  salary_max      DECIMAL(12,2),
  location        VARCHAR(64),
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/OPEN/CLOSED/PAUSED
  publish_at      DATETIME,
  close_at        DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_status (company_id, status),
  UNIQUE KEY uk_company_title (company_id, title)  -- 同一公司不重名
);

-- 候选人
CREATE TABLE opc_hr_candidate (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(64) NOT NULL,
  email           VARCHAR(128),
  phone           VARCHAR(32),
  resume_url      VARCHAR(512) NOT NULL,        -- minio/oss 链接
  resume_md       MEDIUMTEXT,                   -- 上传后解析的 Markdown
  parsed_json     JSON,                         -- LLM 解析的结构化字段
  embedding       BLOB,                         -- 候选人画像向量(1536 维 float32)
  source          VARCHAR(32) NOT NULL DEFAULT 'MANUAL',  -- MANUAL/LINK/IMPORT
  tags_json       JSON,                         -- 自定义标签
  created_by      BIGINT NOT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_name (company_id, name),
  UNIQUE KEY uk_company_email (company_id, email)  -- 同一公司邮箱去重
);

-- 投递（候选人 → JD）
CREATE TABLE opc_hr_application (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  job_id          BIGINT NOT NULL,
  candidate_id    BIGINT NOT NULL,
  channel         VARCHAR(32) NOT NULL DEFAULT 'MANUAL',  -- MANUAL/LINK/RECOMMEND
  score           INT NOT NULL DEFAULT 0,        -- LLM 评分 0-100
  score_reason    TEXT,                         -- LLM 评分理由
  status          VARCHAR(16) NOT NULL DEFAULT 'NEW',     -- NEW/SCREENING/INTERVIEW/OFFER/HIRED/REJECTED
  current_stage   VARCHAR(32),                  -- 当前环节
  applied_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_job_status (company_id, job_id, status),
  UNIQUE KEY uk_job_candidate (job_id, candidate_id)
);

-- 面试记录
CREATE TABLE opc_hr_interview (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  application_id  BIGINT NOT NULL,
  round           INT NOT NULL,                  -- 第几轮
  type            VARCHAR(16) NOT NULL,          -- PHONE/ONSITE/VIDEO
  interviewer_id  BIGINT NOT NULL,
  scheduled_at    DATETIME NOT NULL,
  duration_min    INT NOT NULL DEFAULT 60,
  feedback        TEXT,
  result          VARCHAR(16),                   -- PASS/FAIL/PENDING
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_company_app (company_id, application_id)
);

-- Offer
CREATE TABLE opc_hr_offer (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  application_id  BIGINT NOT NULL,
  salary          DECIMAL(12,2) NOT NULL,
  start_date      DATE NOT NULL,
  expire_at       DATETIME NOT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING/ACCEPTED/REJECTED/EXPIRED
  sent_at         DATETIME,
  responded_at    DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_application (application_id)
);

-- 匹配打分缓存(可选,避免每次 LLM 重算)
CREATE TABLE opc_hr_match_score (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  job_id          BIGINT NOT NULL,
  candidate_id    BIGINT NOT NULL,
  score           INT NOT NULL,
  reason          TEXT,
  computed_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_job_candidate (job_id, candidate_id),
  KEY idx_company_job (company_id, job_id)
);
```

**多租户**：所有表 `company_id NOT NULL` + `idx_company_*` 索引。Service 层 `WHERE company_id = ?` 强约束（参考 [[opc-crm-w50]] 教训）。

## 4. REST API（25 endpoints）

```
JD 管理 (8)
POST   /opc/hr/job              创建 JD(DRAFT)
GET    /opc/hr/job/list         列表(分页 + 状态过滤)
GET    /opc/hr/job/{id}         详情
PUT    /opc/hr/job/{id}         更新
DELETE /opc/hr/job/{id}         删除(仅 DRAFT)
POST   /opc/hr/job/{id}/publish 发布(LLM 改写完整 JD)
POST   /opc/hr/job/{id}/close   关闭
POST   /opc/hr/job/generate-llm LLM 生成 JD(输入:title + category + description, 输出 full_jd + skills)

候选人管理 (7)
POST   /opc/hr/candidate                上传候选人(简历 URL)
GET    /opc/hr/candidate/list           列表
GET    /opc/hr/candidate/{id}           详情
POST   /opc/hr/candidate/{id}/parse     LLM 解析简历
PUT    /opc/hr/candidate/{id}           更新标签
DELETE /opc/hr/candidate/{id}           删除
POST   /opc/hr/candidate/search         语义搜索(top-K + LLM 重排序)

投递 (5)
POST   /opc/hr/application              创建投递
GET    /opc/hr/application/list         列表(按 JD 或候选人过滤)
GET    /opc/hr/application/{id}         详情
PUT    /opc/hr/application/{id}/status  推进状态(SCREENING→INTERVIEW→OFFER→HIRED)
POST   /opc/hr/application/{id}/score   LLM 评分(输入:JD + 候选人画像, 输出 0-100 + 理由)

面试 (3)
POST   /opc/hr/interview                创建面试
GET    /opc/hr/interview/list?applicationId=...
PUT    /opc/hr/interview/{id}           更新反馈 + 结果

Offer (2)
POST   /opc/hr/offer                    创建 Offer
PUT    /opc/hr/offer/{id}/respond       候选人响应(ACCEPTED/REJECTED)

仪表盘 (1)
GET    /opc/hr/dashboard                漏斗 + 转化率 + 平均招聘时长
```

## 5. Feign 依赖（4 个）

| 被调服务 | 用途 | Header |
|---|---|---|
| opc-notification | 投递通知、面试提醒、Offer 发送 | `@InnerAuth` + `from-source: INNER` |
| opc-user-center | 创建 Offer 时拉取员工档案、查部门 | `@InnerAuth` |
| opc-ai-core | LLM 调用（JD 生成 / 简历解析 / 评分 / 重排序）| `@InnerAuth` |
| opc-crm | 候选人入库时与已有 Customer 去重/关联 | `@InnerAuth` |

每个 Feign 必须带 `FallbackFactory`（roadmap §2.2 约束）。

## 6. LLM 集成（3 个 prompt）

| Prompt 名 | 输入 | 输出 | 模型 |
|---|---|---|---|
| `hr_jd_generate` | title + category + description | full_jd (text) + skills (list) | DeepSeek |
| `hr_resume_parse` | resume_md | parsed_json (基本信息 / 教育 / 工作经历 / 技能 / 项目) | DeepSeek |
| `hr_candidate_score` | full_jd + parsed_json | score (0-100) + reason (text) | DeepSeek |

**Prompt 版本**：放 `opc-ai-core` 的 prompt 仓库（参考 [[aiopc-llm-providers]] 的 prompt 版本化约定），走 Nacos 配置 + EvalRunner 验证。

**PromptGuard**：所有 HR LLM 调用前置 `PromptGuard.validate(input)`（沿用 [[aiopc-security]]）。

## 7. 状态机

### 7.1 Application 状态机

```
NEW → SCREENING → INTERVIEW → OFFER → HIRED
                ↓             ↓
              REJECTED    REJECTED
```

**转换规则**：
- NEW → SCREENING: 必触发 LLM 评分（score < 30 可直接 REJECTED）
- SCREENING → INTERVIEW: 创建 1 条 hr_interview 记录
- INTERVIEW → OFFER: 创建 hr_offer 记录 + 触发通知
- INTERVIEW → REJECTED: 必填 reason
- OFFER → HIRED: 候选人 ACCEPTED + start_date 已过

### 7.2 Job 状态机

```
DRAFT → OPEN → CLOSED
       ↓
     PAUSED → OPEN
```

## 8. 前端（5 页）

| 路由 | 文件 | 说明 |
|---|---|---|
| `/opc/hr/job` | `views/opc/hr/job/index.vue` | JD 列表 + 状态过滤 + 「AI 生成 JD」按钮 |
| `/opc/hr/job/:id` | `views/opc/hr/job/detail.vue` | JD 详情 + 投递列表 |
| `/opc/hr/candidate` | `views/opc/hr/candidate/index.vue` | 候选人列表 + 语义搜索框 |
| `/opc/hr/candidate/:id` | `views/opc/hr/candidate/detail.vue` | 候选人画像 + 投递历史 |
| `/opc/hr/dashboard` | `views/opc/hr/dashboard.vue` | 漏斗 + 转化率图 |

**响应式**：复用 [[aiopc-deployment-persistence]] 的 ResponsiveTable + responsive.scss（≥768px 表格 / <768px 卡片）。

## 9. 测试策略

- **单测（≥30）**：`OpcHrJobServiceImplTest`（15）+ `OpcHrApplicationServiceImplTest`（10）+ `OpcHrOfferServiceImplTest`（5）
- **MockedStatic**：`SecurityUtils.getUserId()` + `OpcHrFeignClient`（参考 [[opc-crm-w50]]）
- **E2E（≥15）**：tmp_e2e/e2e_hr.py 全流程：创建 JD → 发布 → 投递 → LLM 评分 → 推进 → 面试 → Offer → 入职
- **LLM 评测**：`EvalRunnerTest`（10 case 简历解析）+ RedTeam（10 case 反注入）
- **健康检查**：health-check.sh 加 8 个端点（health/list/create/detail/dashboard）

## 10. 部署

- **端口**：9322
- **Docker**：thin jar 模板（参考 [[aiopc-all-opc-stack]]）
- **Nacos**：`opc-hr-dev.yml` + `opc-hr-prod.yml` + `share-application-hr.yml`
- **DB 迁移**：`V20260911__opc_hr_schema.sql` + 5 条 seed
- **Helm**：加 `hr` service 到 `deploy/helm/opc/values.yaml` 的 services 列表
- **Gateway**：`/opc/hr/**` → `http://aiopc-hr:9322`（无 StripPrefix，controller 直接用 `/opc/hr` 前缀）
- **白名单**：`/opc/hr/dashboard` 与 `/opc/hr/job/list` GET 公开（匿名访问招聘看板，参考 CRM 落地页模式）

## 11. 验收清单

- [ ] 6 表 + seed 数据
- [ ] 25 endpoints + 单测 ≥30 + E2E ≥15
- [ ] 4 个 Feign（含 Fallback）
- [ ] 3 个 prompt + EvalRunnerTest + RedTeamRunnerTest
- [ ] 5 个 Vue 前端页（响应式）
- [ ] Dockerfile thin jar + Nacos 3 yml + 1 SQL
- [ ] Helm chart 更新
- [ ] Gateway 路由 + 白名单
- [ ] health-check.sh 端点 + 全栈 36→44 PASS
- [ ] VERIFICATION 报告：`docs/verification/week-XX/OPC-WXX-VERIFICATION-opc-hr.md`

## 12. 风险

- R-1：LLM 评分非确定性 → 缓存到 opc_hr_match_score，避免重复调用
- R-2：简历 PDF 解析 → v1 只支持 Markdown 上传（用户用工具转换），TODO 留 W3
- R-3：候选人 embedding 存储 BLOB → Qdrant 已有 collection 复用，无需新依赖
- R-4：dashboard SQL 复杂 → 用单条 SQL + GROUP BY 即可，参考 [[aiopc-deployment-tooling]]

## 13. 不在 v1

- 外部招聘网站对接
- 工资条 / 考勤 / 绩效
- Offer PDF 生成
- 多语言
- HR 移动端（RN 推迟到 Iter 3）
