# OPC (一人公司 + N 个数字员工)

> 一站式智能财务 SaaS。基于 RuoYi Cloud Spring Boot 3 + Vue 3 + TypeScript，
> 让"一人公司"以**自然语言**驱动 7×24 的 AI 数字员工团队完成记账、报税、对账、报销、风控。

[![W1 验收](https://img.shields.io/badge/W1-8%20Task%2F25%20Sub--task-brightgreen)](./OPC-W1-TASK-BREAKDOWN.md)
[![安全](https://img.shields.io/badge/ASR-3.3%25%20%7C%20FP-0%25-success)](./OPC-SECURITY-REPORT-v0.1.md)

---

## 这是什么

OPC = **One-Person Company**，核心假设是未来大量"一人公司"会涌现，
而创办者不可能同时是财务、人事、销售、运营专家。我们提供 **"老板 + N 个 AI 数字员工"**
的模式，把财务记账、税务申报、客户管理、文档处理等环节交给训练有素 + 受严格内控约束的 AI。

### 业务场景（来自 `init.md` §业务场景）

| # | 场景 | 对应 Agent |
|---|------|-----------|
| 1 | 智能记账：从银行流水自动生成会计凭证 | `finance-extract` / `finance-daily` |
| 2 | 自动报税：测算月度税额 + 申报建议 | `finance-tax-report` |
| 3 | 对账平账：多账户对账 + 差异告警 | （规划中） |
| 4 | 报表生成：日报 / 月报 / 年报 | `finance-daily` |
| 5 | 异常预警：识别异常交易 | 内置 risk-scoring |
| 6 | 凭证审核：财务复核（人机协同） | `finance-extract` + need_review 标记 |
| 7 | 客户管理：跟进 + 标签 + 画像 | `sales-crm` |
| 8 | 简历解析：HR 场景 | `hr-resume` |
| 9 | 数据查询：自然语言 → SQL | `text2sql` |

---

## 技术栈

### 后端（`springboot3/`）

- **Spring Boot 3.2** + Spring Cloud 2023 + Spring Cloud Alibaba 2022.0.0.0
- **Nacos** 配置中心 + 服务发现（namespace `opc-prod`）
- **MySQL 8** + MyBatis-Plus 3.5
- **Redis** + Caffeine 双层缓存
- **RabbitMQ** 异步事件
- **Qdrant** 向量库（Agent 长期记忆）
- **Sentinel** 限流熔断
- **Quartz** 定时任务（RuoYi 原生 `ruoyi-job`，不走 XXL-Job）
- **OpenFeign** 微服务调用（带 Sentinel 降级）
- **Jasypt** 配置加密（口令 `OpcEncrypt!2026`）

### 前端（`vue3-typescript/`）

- **Vue 3.5** + Composition API + TypeScript 5.6
- **Element Plus 2.13** UI
- **Vite 6** 构建
- **Pinia 3** 状态管理
- **Vue Router 4** + 路由级权限
- **Sass** + 全局响应式 mixin（xs / sm / md / lg 四档断点）
- **qrcode** 邀请海报二维码

### AI 能力（`opc-ai-core`）

- 多 LLM Provider 适配：**OpenAI / DeepSeek / 文心一言**
- Token 用量计量 + 单租户/单用户配额
- Prompt 版本化 + 红队评测 + AB 测试
- **PromptGuard** + **SensitiveWordFilter** 多层安全防护（详见 `OPC-SECURITY-REPORT-v0.1.md`）
- **AgentRuntime** ReAct 循环（Reasoning + Acting，最多 8 步）

---

## 模块布局

```
opc-ai-core      (9301) AI 中台  ── LLM gateway / agent runtime / 评测 / 安全
opc-user-center  (9302) 用户中心  ── 注册 / 公司 / 成员 / 邀请
opc-agent-hub    (9303) Agent 调度 ── Workflow Engine + 多 Agent 编排
opc-billing      (9304) 计费     ── 钱包 / 代金券 / 流水
opc-finance      (9305) 财务     ── 凭证 / 流水 / 报税报表

RuoYi 内置模块：
  ruoyi-gateway     (9200) Spring Cloud Gateway + Sentinel 流控
  ruoyi-auth        (9201) OAuth2 统一认证
  ruoyi-system      (9202) 用户 / 角色 / 菜单 / 字典
  ruoyi-job         (9203) Quartz 调度（任务注册在 sys_job 表）
  ruoyi-file        (9204) 文件存储抽象
  ruoyi-gen         (9205) 代码生成器
```

---

## 部署架构

```
                ┌─────────────────────┐
                │  用户浏览器 (Vue 3)  │
                └──────────┬──────────┘
                           │ HTTPS
                ┌──────────▼──────────┐
                │ Nginx / Cloudflare │
                └──────────┬──────────┘
                           │
                ┌──────────▼──────────┐
                │  ruoyi-gateway:9200  │ ◄── Sentinel 流控
                │  AuthFilter (JWT)    │
                └──────────┬──────────┘
                           │  内部 RPC (OpenFeign)
        ┌──────────┬───────┼────────┬──────────┐
        ▼          ▼       ▼        ▼          ▼
  ai-core    user-center  agent-hub  billing   finance
  (9301)      (9302)      (9303)     (9304)    (9305)

                ▲                       ▲
                │ cron trigger           │ Nacos config
                │                        │
          ┌─────┴───────┐         ┌──────┴──────┐
          │ ruoyi-job   │         │   Nacos     │
          │ (Quartz)    │         │  opc-prod   │
          └─────────────┘         └─────────────┘
```

完整 K8s Helm Chart 在 `springboot3/deploy/helm/opc/`（7 服务 + HPA + PDB + Ingress）。

---

## W1（Post-Launch 第一个迭代周）交付总览

8 个 Task × 25 个 Sub-task 全部完成。详见 [`OPC-W1-TASK-BREAKDOWN.md`](./OPC-W1-TASK-BREAKDOWN.md)。

| Task | 主题 | 关键产出 | 验证报告 |
|------|------|---------|---------|
| #1 | AI 评测集扩展（10 → 119） | `finance-100.json` + 19 条扩展 | [task1-eval-set](./docs/verification/week-1/OPC-W1-VERIFICATION-task1-eval-set.md) |
| #2 | 后端 prod profile 切 Nacos | 6 个 DataID + Jasypt + fail-fast | [nacos-prod](./docs/verification/week-1/OPC-W1-VERIFICATION-nacos-prod.md) |
| #3 | WorkflowEngine 接入 Quartz cron | `WorkflowCronJob` + Feign + 16 文件 | [task3-workflow-cron](./docs/verification/week-1/OPC-W1-VERIFICATION-task3-workflow-cron.md) |
| #4 | 财务税务报表 | Tax Report Service + Controller + 移动端页 | [tax-report-service](./docs/verification/week-1/OPC-W1-VERIFICATION-tax-report-service.md) / [controller](./docs/verification/week-1/OPC-W1-VERIFICATION-tax-report-controller.md) |
| #5 | 移动端适配（9 页） | Sidebar 抽屉 + ResponsiveTable + 响应式 SCSS | [mobile-drawer](./docs/verification/week-1/OPC-W1-VERIFICATION-mobile-drawer.md) / [responsive-table](./docs/verification/week-1/OPC-W1-VERIFICATION-responsive-table.md) / [responsive-scss](./docs/verification/week-1/OPC-W1-VERIFICATION-responsive-scss.md) |
| #6 | Helm Chart（7 服务 + HPA + PDB + CI） | Chart + 多环境 values + Jenkinsfile | [helm-chart](./docs/verification/week-1/OPC-W1-VERIFICATION-helm-chart.md) / [templates-v2](./docs/verification/week-1/OPC-W1-VERIFICATION-helm-templates-v2.md) / [values-split](./docs/verification/week-1/OPC-W1-VERIFICATION-values-split.md) / [helm-ci](./docs/verification/week-1/OPC-W1-VERIFICATION-helm-ci.md) |
| #7 | 邀请落地页 + 分享海报 | API + Invite.vue + SharePoster 1080² | [invite-flow](./docs/verification/week-1/OPC-W1-VERIFICATION-invite-flow.md) / [invitation-api-7.1](./docs/verification/week-1/OPC-W1-VERIFICATION-invitation-api-7.1.md) / [invite-landing-7.2](./docs/verification/week-1/OPC-W1-VERIFICATION-invite-landing-7.2.md) / [invite-poster-7.3](./docs/verification/week-1/OPC-W1-VERIFICATION-invite-poster-7.3.md) |
| #8 | AI 红队测试 + 加固 | 30 用例 + PromptGuard 7 类 + 8 条安全红线 | [task8-redteam](./docs/verification/week-1/OPC-W1-VERIFICATION-task8-redteam.md) / [SECURITY-REPORT](./OPC-SECURITY-REPORT-v0.1.md) |

### Task #8 安全成果

| 指标 | v0.2 baseline | v0.3 hardened | 目标 |
|------|-------------|--------------|------|
| ASR（攻击成功率） | 30.0% | **3.3%** | ≤ 10% ✅ |
| 合法请求误杀率 | — | **0.0%** | ≤ 5% ✅ |
| 正则层拦截率 | 3.3% | **90.0%** | ≥ 80% ✅ |
| CRITICAL 攻击 ASR | 25.0% | **0.0%** | — |

**单测**：`mvn -pl ruoyi-modules/opc-ai-core test` → 10/10 通过，BUILD SUCCESS。

---

## 本地开发（本机限制说明）

| 环境 | 状态 | 说明 |
|------|------|------|
| JDK 1.8（系统默认） | ⚠️ Spring Boot 3 需要 JDK 17 | 本机只能跑 JDK 8 应用；Spring Boot 3 模块需要切换到 `C:\Program Files\Java\jdk-17.0.17.10-hotspot` |
| MySQL / Nacos / Redis | ❌ 未部署 | 后端集成测试只能 CI 上验证 |
| Helm v4.2.4 | ✅ 已装 | 可 `helm lint` / `helm template` 验证 K8s manifest |
| Node.js | ❌ `node_modules` 不存在 | 前端构建只能 CI 上验证 |

**唯一可本机跑全的单测**：`opc-ai-core`（不需要 Spring 上下文 / DB / Nacos）。
命令：`JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" mvn -pl ruoyi-modules/opc-ai-core test`

---

## 关键约定（务必先看）

| 主题 | 规则 | 详情 |
|------|------|------|
| 配置中心 | 所有配置走 Nacos（prod） / 本地 yml（dev） | 不要在 application.yml 写死敏感字段 |
| 敏感字段 | 全部 `ENC(...)` 包裹 | prod 启动时由 Jasypt 解密；`JasyptEnvironmentPosture` fail-fast on 明文 |
| 数据隔离 | 任何 SQL 必须带 `company_id` 过滤 | 多租户强约束；越权查询立即 fail |
| Agent 工具调用 | 必须在 `PromptGuard.ALLOWED_TOOLS` 白名单内 | `validateToolName()` 在 Runtime 强制 |
| Job Handler | 必须放在 `com.ruoyi.job.task` 包下 | `ScheduleUtils.whiteList()` 强制 |
| Cron 触发 | 走 Quartz `sys_job`，不引 XXL-Job | 新增 cron 任务见 `opc_agent_workflow` 表设计 |
| 邀请码 | 31 字符 base32（去 I/L/O/0/1），8 位 | 见 `OpcCodeGenerator.inviteCode()` |
| 响应式断点 | xs(<576) / sm(576-768) / md(768-992) / lg(>992) | 来自 `src/assets/styles/responsive.scss` |
| 触摸目标 | ≥ 44×44 px（iOS HIG） | el-button `size="large"` 自动满足 |
| Prompt 评测 | 任何 prompt 变更必须跑 `EvalRunnerTest` + `RedTeamRunnerTest` | CI 安全门禁 |

---

## 常用命令速查

```bash
# 后端编译 + 单测（opc-ai-core 是唯一本机可全跑的模块）
mvn -pl ruoyi-modules/opc-ai-core test

# 后端编译某个模块
mvn -pl ruoyi-modules/opc-user-center -am compile -DskipTests

# 前端
cd vue3-typescript
npm install
npm run type-check   # vue-tsc --noEmit
npm run build        # vite build
npm run dev          # 本地开发服务器

# Helm
helm lint springboot3/deploy/helm/opc
helm template test springboot3/deploy/helm/opc -f values-dev.yaml | less

# 安全门禁（建议加入 CI）
mvn -pl ruoyi-modules/opc-ai-core test -Dtest=RedTeamRunnerTest,PromptGuardHardeningTest
# ASR ≤ 10% / 误杀率 = 0 / 正则拦截 ≥ 80%，任一不满足即 fail
```

---

## 文档索引

### 项目级
- `init.md` — 原始需求（业务场景 / 9 类）
- `OPC-MVP-DELIVERY.md` — MVP 交付总结
- `OPC-W1-TASK-BREAKDOWN.md` — W1 任务拆分（8 Task / 25 Sub-task）
- `OPC-INVITATION-FLOW.md` — 邀请业务完整流程文档

### 安全
- `OPC-SECURITY-REPORT-v0.1.md` — **Task #8 完整安全报告**（必读）

### 部署 / DevOps
- `springboot3/deploy/nacos/` — Nacos 配置管理
- `springboot3/deploy/helm/opc/` — K8s Helm Chart
- `springboot3/Jenkinsfile` — CI 流水线
- `vue3-typescript/package.json` — 前端依赖与脚本

---

## 团队与角色

| 角色 | 主要负责模块 |
|------|------------|
| 后端 A | `opc-user-center` / `ruoyi-auth` / `ruoyi-system` |
| 后端 B | `opc-agent-hub` / `ruoyi-job` |
| 后端 C | `opc-finance` / `opc-billing` |
| AI 工程师 | `opc-ai-core`（LLM / Agent / PromptGuard / 评测） |
| 前端 | `vue3-typescript/` |
| DevOps | Helm / Jenkins / Nacos |
| 产品 | 邀请 / 分享文案 / 验收测试 |

---

## 状态

- **MVP**：已上线（参考 `OPC-MVP-DELIVERY.md`）
- **W1**：8 Task / 25 Sub-task 全部完成（2026-09-06）
- **下一阶段**：W2 待规划

---

> 最后更新：2026-09-06 · 维护者：OPC Team