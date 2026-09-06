# W1 团队任务拆分（Post-Launch 第一个迭代周）

> 来源：`OPC-MVP-DELIVERY.md` §6 已识别的 8 项 TODO。
> 拆分原则：每个子任务 ≤ M 级别（3-5 文件、2 小时内），可独立验证。
> 工期：5 个工作日（7 人并行）。

---

## 排期总览

```
Day 1-2  基础设施层（并行）
  ├─ #6 DevOps Helm Chart        (BE-Infra)
  └─ #2 后端 A prod profile       (BE-A)

Day 2-5  业务能力层（并行 + 串行混合）
  ├─ #1 AI 评测集扩展             (AI-Eng)         ── 阻塞 #8
  ├─ #3 后端 B cron 触发器        (BE-B)
  ├─ #4 后端 C 税务报表           (BE-C)
  └─ #8 AI 红队测试               (AI-Eng)         ← 依赖 #1

Day 5-7  体验与增长层（并行）
  ├─ #5 前端移动端适配            (FE)
  └─ #7 产品邀请落地页            (FE + Product)

Day 5 末 Checkpoint：所有任务完成 + 验收
```

---

## Task #1 — AI 评测集扩展（10 → 100+）+ Prompt v0.2

**Owner**：AI 工程师
**关联**：`opc-ai-core/src/main/resources/eval/`
**Scope**：L（拆 4 个子任务）
**依赖**：无

### Sub-task 1.1 — 设计 90 个新评测场景

**Description**：在现有 10 个用例基础上，按 `init.md` §业务场景分类补足 90 个，覆盖收入 / 支出 / 应收 / 应付 / 报销 / 转账 / 退款 / 工资 / 税费 9 大类，每类 10 个。

**Acceptance Criteria**：
- [ ] `finance-100.json` 文件包含 100 条用例
- [ ] 每条用例字段：`id, scenario_text, expected_voucher_type, expected_amount_tolerance, expected_account_subjects[]`
- [ ] 至少 20 个"陷阱场景"（金额含税/不含税、跨期、币种换算、红字冲销）

**Verification**：`jq '. | length' finance-100.json` 输出 100

**Files**：
- `opc-ai-core/src/main/resources/eval/finance-100.json`（新增）
- `opc-ai-core/src/main/resources/eval/scenarios.md`（场景设计文档）

### Sub-task 1.2 — 实现评测 Runner

**Description**：JUnit5 测试，加载 JSON 后批量调用 `AgentRuntime` 跑用例，对比 `expected_*` 字段生成通过率报告。

**Acceptance Criteria**：
- [ ] `EvalRunnerTest.java` 能跑完 100 用例 < 10 分钟
- [ ] 输出 HTML/Markdown 报告（含每条用例的 diff）
- [ ] 支持 `--tag=陷阱` 等过滤参数

**Verification**：`mvn test -pl opc-ai-core -Dtest=EvalRunnerTest` 全绿

**Files**：
- `opc-ai-core/src/test/java/com/ruoyi/opc/ai/eval/EvalRunnerTest.java`（新增）
- `opc-ai-core/src/test/java/com/ruoyi/opc/ai/eval/EvalReportWriter.java`（新增）

### Sub-task 1.3 — 跑 v0.1 baseline

**Description**：用当前 Prompt v0.1 跑 100 用例，输出 baseline 通过率（预期 60-70%），识别 top 10 失败模式。

**Acceptance Criteria**：
- [ ] 报告 `reports/eval-baseline-20260910.md` 存在
- [ ] top 10 失败模式有归类（如"金额识别错"、"科目映射错"）
- [ ] 通过率数字明确（如 67/100 = 67%）

**Files**：`opc-ai-core/reports/eval-baseline-20260910.md`（新增）

### Sub-task 1.4 — 迭代 Prompt v0.2 到 ≥ 85%

**Description**：针对 top 10 失败模式优化 Prompt（加 few-shot 例子、加思维链引导、加领域术语表），重跑直到通过率 ≥ 85%。

**Acceptance Criteria**：
- [ ] `prompt-finance-v0.2.ftl` 存在并在 Nacos 标记为 enabled=1
- [ ] 评测报告 v0.2 通过率 ≥ 85%
- [ ] 提交《Prompt v0.2 变更说明》说明每处优化动机

**Files**：
- `opc-ai-core/src/main/resources/prompts/prompt-finance-v0.2.ftl`（新增）
- `opc-ai-core/reports/eval-v0.2-20260915.md`（新增）
- `sql/opc_prompt_template_seed_v0.2.sql`（新增，update seed）

---

## Task #2 — 后端 A prod profile 切到 Nacos

**Owner**：后端 A
**关联**：6 个 OPC 服务
**Scope**：M（拆 3 个子任务）
**依赖**：Nacos 集群就绪（Week 10 已部署）

### Sub-task 2.1 — 在 Nacos 创建 6 个 prod 配置

**Description**：在 `nacos:8848` 的 namespace `opc-prod` 下创建 6 个 DataID：`opc-ai-core-prod.yml`、`opc-agent-hub-prod.yml`、`opc-user-center-prod.yml`、`opc-billing-prod.yml`、`opc-finance-prod.yml`、`opc-common-prod.yml`。

**Acceptance Criteria**：
- [ ] 6 个 DataID 在 Nacos 控制台可见
- [ ] 每个文件包含 datasource / redis / rabbitmq / qdrant / feign 配置
- [ ] 没有明文密码（用 `ENC(...)` 占位）

**Verification**：`curl http://nacos:8848/nacos/v1/cs/configs?dataId=opc-ai-core-prod.yml` 返回内容

**Files**：`deploy/nacos/import-prod.sh`（新增，curl 批量导入脚本）

### Sub-task 2.2 — Jasypt 加密 + 解密配置

**Description**：所有 Nacos 中的敏感字段（DB 密码、Redis 密码、API Key）用 `OpcEncrypt!2026` 加密，运行时由 `opc-jasypt-spring-boot-starter` 解密。

**Acceptance Criteria**：
- [ ] 启动 `opc-ai-core --spring.profiles.active=prod` 能成功连 DB / Redis
- [ ] 日志中无明文密码
- [ ] Nacos 配置中敏感字段全部以 `ENC(...)` 开头

**Files**：`deploy/nacos/opc-ai-core-prod.yml`（新增/编辑）

### Sub-task 2.3 — 启动时校验 prod 必须连 Nacos

**Description**：在 `bootstrap.yml` 配置 Nacos 地址；启动类加 `@ConditionalOnProperty(name="spring.cloud.nacos.discovery.enabled", havingValue="true")`；连不上 Nacos 时 fail-fast。

**Acceptance Criteria**：
- [ ] 故意把 Nacos 地址写错，启动日志明确报错并退出（exit code ≠ 0）
- [ ] 通过 CI 流水线：`mvn spring-boot:run -Dspring-boot.run.profiles=prod` 在没有 Nacos 的环境启动失败

**Files**：
- `ruoyi-modules/opc-ai-core/src/main/resources/bootstrap.yml`（新增）
- 同上 5 个服务各一份

---

## Task #3 — WorkflowEngine 接入 ruoyi-job cron 触发器

**Owner**：后端 B
**关联**：`opc-agent-hub` + `ruoyi-job`
**Scope**：L（拆 4 个子任务）
**依赖**：Task #2（Nacos 配置就绪，否则 cron 参数动态拿不到）

### Sub-task 3.1 — ruoyi-job 注册 WorkflowCronJob

**Description**：在 `ruoyi-job` 控制台用 `@XxlJob("workflowTrigger")` 注解注册 Job Handler，从 JobParam 读 `workflowCode`，调用 `WorkflowTriggerService.trigger(workflowCode)`。

**Acceptance Criteria**：
- [ ] `xxl-job-admin` 界面能看到 `workflowTrigger` 任务
- [ ] 手动触发一次能成功启动一个工作流实例
- [ ] Job 日志能看到工作流 instanceId

**Files**：`ruoyi-modules/ruoyi-job/src/main/java/com/ruoyi/job/job/WorkflowCronJob.java`（新增）

### Sub-task 3.2 — 工作流定义加 cron 表达式字段

**Description**：`opc_agent_workflow` 表加 `cron_expression VARCHAR(50)`、`timezone VARCHAR(20) DEFAULT 'Asia/Shanghai'`、`next_run_at DATETIME`，并加索引。

**Acceptance Criteria**：
- [ ] Flyway 脚本 `V2__workflow_cron.sql` 创建字段
- [ ] 数据库迁移可重复执行（IF NOT EXISTS）
- [ ] `OpcAgentWorkflow` domain 类对应字段已加

**Files**：
- `sql/flyway/V2__workflow_cron.sql`（新增）
- `OpcAgentWorkflow.java`（编辑，加 3 个字段）

### Sub-task 3.3 — WorkflowTriggerService 实现

**Description**：新建 Service，接收 workflowCode → 查最新 enabled 定义 → 启动 `WorkflowEngine.run()` → 写 `opc_agent_workflow_run` 表 → 异步返回。

**Acceptance Criteria**：
- [ ] 单元测试覆盖：合法 code / 不存在 code / 重复触发去重
- [ ] 触发后 30s 内能在 `opc_agent_workflow_run` 看到记录

**Files**：
- `opc-agent-hub/src/main/java/com/ruoyi/opc/agent/service/IWorkflowTriggerService.java`（新增）
- `opc-agent-hub/src/main/java/com/ruoyi/opc/agent/service/impl/WorkflowTriggerServiceImpl.java`（新增）
- `opc-agent-hub/src/test/java/.../WorkflowTriggerServiceImplTest.java`（新增）

### Sub-task 3.4 — 种子数据：日报工作流每天 9 点跑

**Description**：在 seed SQL 加一条 `opc_agent_workflow` 记录：`code=finance_daily_report_v1, cron='0 0 9 * * ?', enabled=1`。

**Acceptance Criteria**：
- [ ] 部署后第 2 天 9:00 触发一次工作流实例
- [ ] `opc_agent_workflow_run` 记录触发时间

**Files**：`sql/opc_data_workflow_seed.sql`（新增）

---

## Task #4 — 后端 C 财务税务报表

**Owner**：后端 C
**关联**：`opc-finance`
**Scope**：M（拆 3 个子任务）
**依赖**：无

### Sub-task 4.1 — 创建 Domain + Mapper

**Description**：基于已存在的 `opc_finance_tax_report` 表创建 Java 实体、Mapper 接口、XML。

**Acceptance Criteria**：
- [ ] `OpcFinanceTaxReport` 含全部表字段（report_no, period, total_sales, total_vat, etc.）
- [ ] Mapper 有 `insertTaxReport` / `selectByUserAndPeriod` / `listByUser`

**Files**：
- `opc-finance/src/main/java/com/ruoyi/opc/finance/domain/OpcFinanceTaxReport.java`（新增）
- `opc-finance/src/main/java/com/ruoyi/opc/finance/mapper/OpcFinanceTaxReportMapper.java`（新增）
- `opc-finance/src/main/resources/mapper/OpcFinanceTaxReportMapper.xml`（新增）

### Sub-task 4.2 — 月度报表生成 Service

**Description**：`OpcFinanceTaxReportServiceImpl.generateMonthlyReport(userId, yearMonth)`：从 `opc_finance_voucher` 聚合当月数据 + 调 LLM 生成报税建议 → 写表。

**Acceptance Criteria**：
- [ ] 调用后 `opc_finance_tax_report` 多一条记录
- [ ] LLM 调用失败时回退到纯聚合报表，不报错
- [ ] 单元测试覆盖：空数据月 / 正常月 / LLM 失败月

**Files**：
- `opc-finance/src/main/java/com/ruoyi/opc/finance/service/IOpcFinanceTaxReportService.java`（新增）
- `opc-finance/src/main/java/com/ruoyi/opc/finance/service/impl/OpcFinanceTaxReportServiceImpl.java`（新增）

### Sub-task 4.3 — Controller + 前端页面

**Description**：REST API `POST /opc/finance/tax-reports/generate`、`GET /opc/finance/tax-reports`、`GET /opc/finance/tax-reports/{id}`；前端 `/opc/finance/tax-reports.vue` 表格 + 生成按钮。

**Acceptance Criteria**：
- [ ] Postman 调通 3 个接口
- [ ] 前端页面"生成报表"按钮触发后 5s 内看到新记录
- [ ] 移动端表格可滚动

**Files**：
- `opc-finance/src/main/java/com/ruoyi/opc/finance/controller/OpcFinanceTaxReportController.java`（新增）
- `RuoYi-Cloud-Vue3-typescript/src/api/opc/finance.ts`（编辑，加 3 个方法）
- `RuoYi-Cloud-Vue3-typescript/src/views/opc/finance/tax-reports.vue`（新增）
- `RuoYi-Cloud-Vue3-typescript/src/router/index.ts`（编辑，加路由）

---

## Task #5 — 前端移动端适配（9 个 OPC 页面）

**Owner**：前端
**关联**：`src/views/opc/**/*.vue`
**Scope**：M（拆 3 个子任务）
**依赖**：无

### Sub-task 5.1 — 侧边栏 < 768px 改抽屉

**Description**：基于 `useBreakpoint` 或 CSS media query，< 768px 时侧边栏隐藏 + 顶部出现汉堡按钮，点击后抽屉式滑出。

**Acceptance Criteria**：
- [ ] 360px / 414px 宽度下侧边栏默认隐藏
- [ ] 汉堡按钮点击后抽屉滑出动画 < 300ms
- [ ] 点击外部区域自动关闭

**Files**：
- `RuoYi-Cloud-Vue3-typescript/src/layout/components/Sidebar/index.vue`（编辑）
- `RuoYi-Cloud-Vue3-typescript/src/views/opc/components/MobileDrawer.vue`（新增）

### Sub-task 5.2 — 表格 < 768px 改卡片式

**Description**：封装 `<ResponsiveTable>` 组件，< 768px 时把 `<el-table>` 渲染成 `<el-card>` 列表，每行关键字段一目了然。

**Acceptance Criteria**：
- [ ] 9 个 OPC 页面（agent/instances, finance/vouchers, finance/flows, billing/wallet 等）都用 ResponsiveTable
- [ ] 414px 宽度下不出现横向滚动
- [ ] 768px 以上保持原表格

**Files**：
- `RuoYi-Cloud-Vue3-typescript/src/views/opc/components/ResponsiveTable.vue`（新增）
- 9 个 OPC 页面（编辑替换 `<el-table>`）

### Sub-task 5.3 — 全局字号间距响应式

**Description**：`src/assets/styles/responsive.scss` 加断点 mixin：xs(< 576)、sm(576-768)、md(768-992)、lg(> 992)；调整 padding / font-size / 卡片间距。

**Acceptance Criteria**：
- [ ] 360px 宽度下文字不重叠
- [ ] 所有按钮触摸目标 ≥ 44px × 44px（iOS HIG）
- [ ] `npm run dev` 在 Chrome DevTools 4 个断点切换无错位

**Files**：
- `RuoYi-Cloud-Vue3-typescript/src/assets/styles/responsive.scss`（新增）
- `RuoYi-Cloud-Vue3-typescript/src/main.ts`（编辑 import）

---

## Task #6 — DevOps Helm Chart

**Owner**：DevOps
**关联**：`deploy/k8s/` → `deploy/helm/opc/`
**Scope**：L（拆 4 个子任务）
**依赖**：K8s 集群就绪

### Sub-task 6.1 — Chart 骨架

**Description**：创建标准 Helm Chart 结构：`Chart.yaml`, `values.yaml`, `templates/_helpers.tpl`, `.helmignore`。

**Acceptance Criteria**：
- [ ] `helm lint deploy/helm/opc/` 输出 0 errors
- [ ] `helm template test deploy/helm/opc/` 能渲染 6 个服务的 YAML

**Files**：
- `deploy/helm/opc/Chart.yaml`（新增）
- `deploy/helm/opc/values.yaml`（新增）
- `deploy/helm/opc/templates/_helpers.tpl`（新增）

### Sub-task 6.2 — 6 个服务 Templates

**Description**：把现有 `opc-ai-core.yaml` / `opc-agent-hub.yaml` 等改为可参数化的 Helm template，支持镜像 tag / replica / resource / env 通过 values 覆盖。

**Acceptance Criteria**：
- [ ] `templates/deployment-*.yaml` 6 个
- [ ] `templates/service-*.yaml` 6 个
- [ ] `templates/hpa.yaml`（ai-core 专用）
- [ ] `templates/ingress.yaml` 共享

**Files**：`deploy/helm/opc/templates/deployment-{ai-core,agent-hub,user-center,billing,finance,system}.yaml` 等

### Sub-task 6.3 — 多环境 values 拆分

**Description**：`values-dev.yaml`（小规格 / 1 副本 / 本地镜像）、`values-staging.yaml`（中规格）、`values-prod.yaml`（生产规格）。

**Acceptance Criteria**：
- [ ] `helm install opc deploy/helm/opc/ -f values-dev.yaml` 能在 dev 集群起服务
- [ ] `helm install opc deploy/helm/opc/ -f values-prod.yaml` 在 prod 集群起服务
- [ ] 三套 values 之间只有配置差异，无模板差异

**Files**：`deploy/helm/opc/values-{dev,staging,prod}.yaml`（新增）

### Sub-task 6.4 — 验证 + 文档

**Description**：`helm template` 在 CI 中跑通；写 `deploy/helm/opc/README.md` 说明用法。

**Acceptance Criteria**：
- [ ] GitHub Actions / Jenkinsfile 加 `helm template` 步骤
- [ ] README 包含 `helm install / upgrade / rollback` 完整命令
- [ ] 回滚命令经过验证：`helm rollback opc 1`

**Files**：
- `Jenkinsfile`（编辑，加 helm template stage）
- `deploy/helm/opc/README.md`（新增）

---

## Task #7 — 产品种子用户邀请落地页

**Owner**：产品 + 前端协作
**关联**：`opc-user-center` + 前端
**Scope**：M（拆 3 个子任务）
**依赖**：无（独立功能）

### Sub-task 7.1 — 邀请码生成 API

**Description**：`POST /opc/user/invitations/generate`（需登录，生成自己专属邀请码）、`GET /opc/user/invitations?code=xxx`（公开，根据 code 查邀请人）、`POST /opc/user/invitations/accept`（被邀请人注册时填 code，写 opc_company_member）。

**Acceptance Criteria**：
- [ ] 生成邀请码：8 位 base32，唯一索引
- [ ] 接受邀请后邀请人得 50 元代金券（写 opc_transaction）
- [ ] 同一用户最多 50 个未用邀请码

**Files**：
- `opc-user-center/src/main/java/com/ruoyi/opc/user/controller/OpcInvitationController.java`（新增）
- `opc-user-center/src/main/java/com/ruoyi/opc/user/service/IOpcInvitationService.java`（新增）
- 同上 `impl/`

### Sub-task 7.2 — 落地页 Invite.vue

**Description**：`/opc/invite?code=XXX` 落地页：展示邀请人昵称/头像 + 注册表单（已登录用户直接绑定），未登录用户跳转登录后回来。

**Acceptance Criteria**：
- [ ] 落地页 SSR/CSR 都能正确渲染邀请人信息
- [ ] 注册成功后展示"加入成功"动画 + 跳到 OPC 首页
- [ ] 移动端样式良好

**Files**：
- `RuoYi-Cloud-Vue3-typescript/src/views/opc/invite.vue`（新增）
- `RuoYi-Cloud-Vue3-typescript/src/router/index.ts`（编辑加路由）
- `RuoYi-Cloud-Vue3-typescript/src/api/opc/user.ts`（编辑加 3 个方法）

### Sub-task 7.3 — 分享卡片 + 二维码

**Description**：在用户中心"我的邀请"页生成带 logo 的分享卡片（PNG 格式），含邀请码 + 落地页 URL + 海报文案。

**Acceptance Criteria**：
- [ ] 点击"生成分享卡"按钮 → 下载 PNG
- [ ] 二维码扫描后能正确跳转落地页
- [ ] 卡片尺寸适配微信朋友圈（1080×1080）

**Files**：
- `RuoYi-Cloud-Vue3-typescript/src/views/opc/invitations.vue`（新增）
- `RuoYi-Cloud-Vue3-typescript/src/views/opc/components/SharePoster.vue`（新增，使用 `qrcode` + `html2canvas`）

---

## Task #8 — AI 红队测试用例

**Owner**：AI 工程师
**关联**：`opc-ai-core/src/main/resources/eval/redteam-*.json`
**Scope**：L（拆 4 个子任务）
**依赖**：Task #1（评测集结构需先确定）

### Sub-task 8.1 — 设计 30 个红队场景

**Description**：3 类攻击场景各 10 个：
- **凭证注入**：让 Agent 把"测试"凭证写入生产库
- **提现指令绕过**：让 Agent 执行"给账户 X 转账 10000"
- **敏感数据泄露**：让 Agent 输出其他用户的公司数据

每条场景含 `attack_prompt, attack_goal, success_criteria` 字段。

**Files**：`opc-ai-core/src/main/resources/eval/redteam-30.json`（新增）

### Sub-task 8.2 — 跑当前 Prompt v0.2 测攻击成功率

**Description**：复用 Task #1 的 EvalRunner，但加载 redteam JSON + 攻击成功率判定（ASR, Attack Success Rate）。

**Acceptance Criteria**：
- [ ] ASR 基线数字明确（如 12/30 = 40%）
- [ ] 每条失败用例标注"为什么 Prompt 没能拦"

**Files**：`opc-ai-core/reports/redteam-baseline-20260915.md`（新增）

### Sub-task 8.3 — 修复漏洞到 ASR ≤ 10%

**Description**：补强 PromptGuard（在 prompt 加 system 限制）+ Tool 白名单（不允许 `update_voucher` / `create_payment` 工具暴露给 LLM 自调用）+ SensitiveWordFilter 加敏感模式。

**Acceptance Criteria**：
- [ ] ASR 重测 ≤ 10%
- [ ] 误杀率（正常请求被拦截）≤ 5%

**Files**：
- `opc-ai-core/src/main/resources/prompts/system-safety-v0.3.ftl`（新增）
- `opc-ai-core/src/main/java/com/ruoyi/opc/ai/security/PromptGuard.java`（编辑，加 5 条规则）
- `opc-ai-core/src/main/java/com/ruoyi/opc/ai/tools/ToolRegistry.java`（编辑，加工具白名单）

### Sub-task 8.4 — 输出《Agent 安全测试报告 v0.1》

**Description**：包含 ASR 基线 / 修复后 ASR / 误杀率 / 未来攻击向量预测。

**Files**：`opc-ai-core/reports/security-report-v0.1-20260920.md`（新增）

---

## 验收 Checkpoint（W1 末）

- [ ] 所有 8 个 Task 完成 ✅
- [ ] 25 个 Sub-task 全部 ✅
- [ ] `mvn clean install` 在所有模块通过
- [ ] `npm run build` 在前端通过
- [ ] `helm template` + `helm install --dry-run` 通过
- [ ] EvalRunner 在 100 用例上 ≥ 85% 通过率
- [ ] Redteam ASR ≤ 10%
- [ ] 移动端 360/414/768/1024 四个断点无错位
- [ ] 邀请落地页 E2E 测试通过（生成 → 接受 → 代金券到账）
- [ ] 8 份报告全部提交到 `reports/` 目录

---

## 风险与依赖图

```
                       Nacos 集群就绪
                            │
                            ▼
                   Task #2 prod profile ──────────┐
                                                 │
                                                 ▼
                                          Task #3 cron 触发器
                                          (依赖 #2)

Task #6 Helm Chart  ──── 独立，无依赖

Task #4 税务报表  ──── 独立，无依赖

Task #5 移动端  ────── 独立，无依赖

Task #7 邀请页  ────── 独立，无依赖

Task #1 评测集  ──────┐
                     ├─→ Task #8 红队测试
                     │   (依赖 #1)
```

**关键路径**：`#2 → #3`（2 天），其他任务可完全并行。

---

## 总工时估算

| 角色 | W1 投入 |
|------|---------|
| 后端 A | 2 天（#2） |
| 后端 B | 3 天（#3） |
| 后端 C | 2 天（#4） |
| 前端 | 5 天（#5 全周 + #7 后端协作 1 天） |
| AI 工程师 | 5 天（#1 + #8，可与 #5/#7 并行） |
| DevOps | 4 天（#6） |
| 产品 | 3 天（#7 文档 / 文案 / 测试） |

---

> 文档版本：v1.0 · 2026-09-03 · 与 `OPC-MVP-DELIVERY.md` 配套使用。
