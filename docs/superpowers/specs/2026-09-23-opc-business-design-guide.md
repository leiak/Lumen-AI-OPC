# OPC 业务设计说明（OPC Business Design Guide）

> 元信息
> - 版本：v1.0
> - 日期：2026-09-23
> - 维护者：OPC 平台架构组
> - 目标读者：**业务设计师 / 后端开发 / 前端开发 / 测试工程师 / DevOps**
> - 配套阅读：
>   - [`init.md`](../../init.md)（原始需求）
>   - [`OPC-MVP-DELIVERY.md`](../../OPC-MVP-DELIVERY.md)（MVP 交付总览）
>   - [`OPC-W1-TASK-BREAKDOWN.md`](../../OPC-W1-TASK-BREAKDOWN.md)（W1 任务拆分模板）
>   - [`OPC-SECURITY-REPORT-v0.1.md`](../../OPC-SECURITY-REPORT-v0.1.md)（AI 安全红队报告）
>   - [`docs/superpowers/specs/2026-09-10-opc-roadmap-expansion-design.md`](2026-09-10-opc-roadmap-expansion-design.md)（扩展路线图 W49-W90）
>   - `springboot3/deploy/RECOVERY.md`（部署恢复）

---

## 0. 文档目的

OPC（One-Person Company）是一站式智能 SaaS 平台，**让"一人公司"以自然语言驱动 N 个受严格内控约束的 AI 数字员工完成日常经营**。本文档不重复"代码是什么"，而是回答**"为什么这样设计、下一步怎么落地、常见坑在哪"**。

读完本文，你应该能：
- 知道 OPC 当前的**业务全景**与**模块边界**
- 按模板**新增一个业务服务**（已有 12 个 OPC 服务是模板）
- 在**设计阶段**就避坑（N+1、越权、并发、Prompt 注入、批次并发扣减等）
- 在**开发阶段**直接复用现有约定（包路径、Feign、租户隔离、PromptGuard 集成）
- 在**测试阶段**知道该跑哪些套件（单测 + E2E + 红队 + 评测 + 突变测试）
- 在**部署阶段**知道走哪条流水线（Nacos → Docker thin jar → Helm → 健康检查 → RECOVERY）

---

## 1. 项目概览

### 1.1 产品定位

OPC = **One-Person Company**，核心假设是未来大量"一人公司"会涌现，而创办者不可能同时是财务、人事、销售、运营专家。我们提供**"老板 + N 个 AI 数字员工"**模式，把日常经营（记账、客户管理、内容营销、招聘、独立站运营、数据洞察）交给**训练有素 + 受严格内控约束**的 AI。

### 1.2 目标用户

| 角色 | 痛点 |
|------|------|
| 创业者 / 一人公司老板 | 不可能每个环节都是专家，要 AI 替他干活 |
| 小微企业主 | 想节流，财务/HR/进销存希望"自动化 + 审计友好" |
| 服务商 / 园区 | 引流 + 分佣，希望标准化接入 |
| AI 数字员工开发者 | 上架自定义 Agent 到模块市场 |

### 1.3 9 类核心业务场景（来自 `init.md`）

| # | 场景 | 当前 Agent |
|---|------|-----------|
| 1 | 智能记账（银行流水 → 凭证） | `finance-extract` / `finance-daily` |
| 2 | 自动报税（月度税额 + 申报建议） | `finance-tax-report` |
| 3 | 对账平账 | （占位） |
| 4 | 报表生成（日/季/年） | `finance-daily` |
| 5 | 异常预警（异常交易识别） | 内置 `risk-scoring` |
| 6 | 凭证审核（人机协同） | `finance-extract` + `need_review` 标记 |
| 7 | 客户管理（跟进 + 标签 + 画像） | `sales-crm` |
| 8 | 简历解析 | `hr-resume` |
| 9 | 自然语言查询数据 | `text2sql` |

### 1.4 商业模式画布

- **Agent 订阅/租赁**：按数字员工类型、时长收费
- **Token 消耗分成**：按 LLM 调用量计费抽成
- **模块市场抽成**：第三方 Agent 上架分佣
- **园区分佣**：线下 OPC 园区引流分佣

---

## 2. 系统架构

### 2.1 总体拓扑

```
                ┌─────────────────────┐
                │  浏览器 / 移动 App   │
                └──────────┬──────────┘
                           │ HTTPS
                ┌──────────▼──────────┐
                │ Nginx / Cloudflare │
                └──────────┬──────────┘
                           │
                ┌──────────▼──────────┐
                │  ruoyi-gateway:8080 │ ← Sentinel + AuthFilter(JWT) + 黑/白名单
                └──────────┬──────────┘
                           │ OpenFeign (内部 RPC)
   ┌───────┬───────┬───────┼───────┬───────┬───────┬───────┬───────┬───────┬───────┬───────┬──────┐
   ▼       ▼       ▼       ▼       ▼       ▼       ▼       ▼       ▼       ▼       ▼       ▼
 ai-core user    agent-  billing finance erp     crm     hr      content community  notif  insight
 9301   -center  hub     9304    9305    9311    9312    9322    9325    9316      9310    9306
 9302    9303
                ▲                          ▲                       ▲
                │ cron trigger              │ 配置推送              │ OpenFeign + @InnerAuth
                │                          │
        ┌───────┴────────┐         ┌───────┴────────┐
        │ ruoyi-job      │         │ Nacos cluster  │
        │ (Quartz)       │         │ ns: opc-prod   │
        └────────────────┘         └────────────────┘
        ┌──────────────┐  ┌─────────┐ ┌──────────┐ ┌─────────┐ ┌──────────┐
        │ MySQL 8 主从 │  │ Redis  │ │ Qdrant   │ │ RabbitMQ│ │ MinIO    │
        └──────────────┘  └─────────┘ └──────────┘ └─────────┘ └──────────┘
```

### 2.2 技术栈

**后端**（`springboot3/`）

| 类别 | 选型 |
|------|------|
| 基础 | Spring Boot 3.2 + Spring Cloud 2023 + Spring Cloud Alibaba 2022.0.0.0 |
| 注册/配置 | Nacos（namespace `opc-prod`，dev 用 `opc-dev`） |
| ORM | MyBatis-Plus 3.5（**`mybatis-plus:` 别名误配会导致 mapper xml 不加载 → 见 §11.1**） |
| 缓存 | Redis + Caffeine 双层 |
| 消息 | RabbitMQ 异步事件 |
| 向量 | Qdrant（Agent 长期记忆 + 候选人画像） |
| 限流熔断 | Sentinel |
| 调度 | Quartz via `ruoyi-job`（**不走 XXL-Job**） |
| RPC | OpenFeign + `@InnerAuth` + `from-source: INNER` |
| 加密 | Jasypt（口令 `OpcEncrypt!2026`） |

**前端**（`vue3-typescript/`）

| 类别 | 选型 |
|------|------|
| 框架 | Vue 3.5 + Composition API + TypeScript 5.6 |
| UI | Element Plus 2.13.1 |
| 构建 | Vite 6 |
| 状态 | Pinia 3 |
| 路由 | Vue Router 4（createWebHistory，无 hash） |
| 样式 | Sass + 全局响应式 mixin（xs/sm/md/lg） |
| 二维码 | `qrcode@1.5.4`（H 级别） |
| 海报 | **Native Canvas 2D**（不依赖 html2canvas） |

**AI 能力**（`opc-ai-core`）

- 多 Provider：**DeepSeek / OpenAI / 文心 / MiniMax**（OpenAI 兼容协议）
- Prompt 版本化 + 红队评测 + AB 测试
- **PromptGuard v0.3** + SensitiveWordFilter 多层安全防护
- **AgentRuntime** ReAct 循环（Reasoning + Acting，最多 8 步）
- **HttpLlmClient**（JDK 11+ HttpClient，零额外依赖，同步 + SSE）

---

## 3. 业务模块清单

### 3.1 OPC 自研 12 个服务

| 端口 | 服务 | 业务定位 | 关键能力 | 交付周 |
|:----:|------|---------|---------|:----:|
| 9301 | **opc-ai-core** | AI 中台（LLM Gateway / Agent Runtime / 评测 / 安全） | 多 Provider、PromptGuard、记忆、Token 计量、Eval/RedTeam | M0 |
| 9302 | **opc-user-center** | 用户中心 | 注册、公司档案、成员、邀请、邀请码落地 | W7 |
| 9303 | **opc-agent-hub** | Agent 调度 | WorkflowEngine + Quartz cron + Agent 实例化 | W3 |
| 9304 | **opc-billing** | 计费 | 钱包、订单、充值、交易流水 | W2 |
| 9305 | **opc-finance** | 财务 Agent | 凭证、银行流水、税务报表、对账 | M0 |
| 9306 | **opc-insight** | 数据洞察 | 日报、仪表盘、异常预警、决策建议 | M4 |
| 9310 | **opc-notification** | 通知中心 | 邮件/短信/站内信 + WebSocket Inbox | W49 |
| 9311 | **opc-erp** | 进销存 | 商品/SKU、采购、销售、退货、库存预警、FIFO | W72 |
| 9312 | **opc-crm** | 客户关系 | 客户/联系人/跟进/商机/合同/订单 | W50 |
| 9316 | **opc-community** | 模块市场 | 模块浏览/订阅/评分/评论 | W52 |
| 9322 | **opc-hr** | 招聘 / 人事 | JD / 候选人 / 投递 / 面试 / Offer / 评分 | W71 |
| 9325 | **opc-content** | AI 内容创作 | 短剧/视频/图文脚本 + 抖音 sandbox 发布 | W74 |

### 3.2 RuoYi 内置 8 个服务

| 端口 | 服务 | 用途 |
|:----:|------|------|
| 9200 | `ruoyi-gateway` | Spring Cloud Gateway + Sentinel 流控 + 4 个 Filter |
| 9201 | `ruoyi-auth` | OAuth2 统一认证（登录、JWT） |
| 9202 | `ruoyi-system` | 用户/角色/菜单/字典（`com.ruoyi.system.domain`，Nacos 配置必须禁用） |
| 9203 | `ruoyi-job` | Quartz 调度（任务注册在 `sys_job` 表） |
| 9204 | `ruoyi-file` | 文件存储抽象 |
| 9205 | `ruoyi-gen` | 代码生成器 |

### 3.3 模块选型决策树（业务设计师）

新增业务时，按下列问题决定归属：

1. **是否需要与 AI 对话？** → 调 `opc-ai-core`（不要自建 LLM client）
2. **是否需要推送通知？** → 调 `opc-notification`（Feign + `@InnerAuth`）
3. **是否需要 LLM 评分/匹配？** → 落 `opc_hr_match_score` / `opc_crm_*` 业务表，避免重复 LLM 调用
4. **是否产生凭证/财务影响？** → 调 `opc-finance` 而不是写自己的钱相关表
5. **是否需要多租户隔离？** → 必须带 `company_id`（详见 §6.1）

---

## 4. 领域模型 & 数据概览

### 4.1 通用约定

- **多租户**：所有业务表 `company_id BIGINT NOT NULL` + `idx_company_*` 复合索引。**Service 层 `WHERE company_id = ?` 强约束**——跨服务调用时由调用方注入或由 `SecurityUtils.getCompanyId()` 提供。
- **主键**：统一 `SnowflakeIdWorker`（`opc-common`），不用 AUTO_INCREMENT。
- **审计**：`created_by` / `create_time` / `updated_by` / `update_time` 必有；NOT NULL 列必须有 DEFAULT。
- **状态机**：枚举驱动（`@EnumValue`），状态变更用 `transitionTo()` 显式方法，禁止 SQL 直接 UPDATE。

### 4.2 表分类速查

| 模块 | 核心表 |
|------|--------|
| user-center | `opc_user_profile`、`opc_company_profile`、`opc_company_member`、`opc_invitation` |
| agent-hub | `opc_agent_definition`、`opc_agent_instance`、`opc_agent_task`、`opc_agent_workflow`、`opc_agent_workflow_run`、`opc_agent_token_usage` |
| finance | `opc_finance_voucher`、`opc_finance_bank_flow`、`opc_finance_tax_report` |
| billing | `opc_wallet`、`opc_transaction`、`opc_billing_order`、`opc_commission_record` |
| crm | `opc_crm_customer`、`opc_crm_contact`、`opc_crm_follow_up`、`opc_crm_opportunity`、`opc_crm_contract`、`opc_crm_order` |
| hr | `opc_hr_job`、`opc_hr_candidate`、`opc_hr_application`、`opc_hr_interview`、`opc_hr_offer`、`opc_hr_match_score` |
| erp | `opc_erp_supplier`、`opc_erp_product`、`opc_erp_product_sku`、`opc_erp_batch`、`opc_erp_inventory_log`、`opc_erp_purchase`、`opc_erp_sale`、`opc_erp_return` |
| content | `opc_content_script`、`opc_content_platform_account`、`opc_content_publish`、`opc_content_adapt` |
| community | `opc_business_module`、`opc_module_category`、`opc_module_subscription`、`opc_module_review` |
| notification | `opc_notification`、`opc_notification_template` |
| insight | `opc_insight_daily_report`、`opc_insight_anomaly`、`opc_insight_advice` |

### 4.3 跨服务数据流（关键链路）

```
创建财务：自然语言流水文本 → opc-ai-core(LLM) → opc-finance.voucher(DRAFT,need_review=1)
   → 人类确认(POST /opc/finance/vouchers/{id}/approve)
   → voucher.status=REVIEWED → opc-agent-hub 触发工作流
   → opc-billing 扣 Token + 钱包
   → opc-notification 推送给用户
   → opc-insight 汇总入日 KIP

CRM 商机打分：opc-crm 创建商机 → Feign → opc-ai-core LLM 评分
   → 落 opc_crm_opportunity.score + score_reason
   → Feign → opc-user-center 取客户档案 → 聚合进 opc_insight_advice
```

---

## 5. AI 能力落点

### 5.1 Prompt 仓库结构（`opc-ai-core/src/main/resources/prompts/`）

```
prompts/
├── prompt-finance-v0.2.ftl         # 财务智能记账
├── prompt-finance-tax-v1.0.ftl     # 财务报税建议
├── prompt-hr-jd-v1.0.ftl           # JD 生成
├── prompt-hr-resume-v1.0.ftl       # 简历解析
├── prompt-hr-score-v1.0.ftl        # 候选人评分
├── prompt-crm-score-v1.0.ftl       # 商机打分
├── prompt-erp-category-v1.0.ftl    # 商品自动分类
├── prompt-content-drama-v1.0.ftl   # 短剧脚本
├── prompt-content-video-v1.0.ftl   # 视频脚本
├── prompt-content-article-v1.0.ftl # 图文文案
├── prompt-insight-advice-v1.0.ftl  # 决策建议
├── system-safety-v0.3.ftl          # 8 条不可协商红线（必须所有 prompt 引用）
└── CHANGELOG-v0.2.md               # 版本变更说明
```

### 5.2 PromptGuard（必须前置）

每个 LLM 调用入口都必须**先调** `PromptGuard.validate(userInput)`，再调 LLM：
- 命中攻击 → 抛 `PromptBlockedException`（HTTP 403）
- 7 类攻击模式：凭证注入、提现绕过、跨租户越权、聚合反推、汇率篡改、间接注入、角色冒充
- 误杀率 ≤ 5%（CI 门禁）

### 5.3 AgentRuntime ReAct 循环

`AgentRuntime.run(userInput, agentDefinition)` 最多 8 步：
1. LLM 思考（带 system prompt + 历史）
3. Tool 调用白名单（`PromptGuard.ALLOWED_TOOLS`）
4. tool 结果回填 LLM
5. 重复直到 LLM 返回 FINAL 或达到 maxSteps
6. 失败：重试 3 次（指数退避 1s/3s/9s），最终兜底返回 `ServiceUnavailable`

### 5.4 评测集 + 红队（CI 必跑）

| 套件 | 文件 | 阈值 | 触发 |
|------|------|------|------|
| Eval（合法用例通过率） | `eval/finance-100.json` + `eval/<domain>-*.json` | ≥ 85% | PR 必跑 |
| RedTeam（攻击拦截率） | `eval/redteam-*.json` | ASR ≤ 10%，误杀率 ≤ 5% | PR 必跑 |
| Mutation（突变测试） | `pit` profile（POM） | ai-core ≥ 80%、gateway ≥ 75% | W5 周期跑 |

---

## 6. 跨切关注点

### 6.1 鉴权 & 多租户

```
用户登录 (ruoyi-auth) → JWT
↓
gateway AuthFilter 校验 JWT → 把 userId/companyId 塞 Header
↓
ruoyi-system 拦截器 → WebMvc SecurityUtils 静态方法
↓
业务 Service 层：
    Long userId = SecurityUtils.getUserId();
    Long companyId = SecurityUtils.getCompanyId(); // 内部服务拿不到时=null, 业务方要 fallback
    Long loginUserId = SecurityUtils.getLoginUserIdOrNull(); // 推荐用这个,避免 NPE
```

**铁律**：
- 任何 SQL 必须带 `company_id` 过滤，越权查询立即 fail（W72 ERP 教训）
- NOT NULL DEFAULT 业务表必在 service 层补默认（W50 CRM 教训：列 DEFAULT 0 不生效 → service.create() 必 `if (x == null) x = 0`）
- `SecurityUtils.getUserId()` 在 LLM 测试中返回 null → 用 `MockedStatic` + `@MockitoSettings(strictness = LENIENT)`

### 6.2 安全护栏

| 防线 | 触发点 | 实现 |
|------|--------|------|
| 网关白名单 | `IgnoreWhiteProperties` | path-only，不支持 HTTP method；改造成 `method+path` 在 backlog |
| PromptGuard | 所有 LLM 调用入口 | 7 类攻击模式 + 8 条 system-safety-v1 红线 |
| SensitiveWordFilter | PromptGuard 后置 | 政治/黄赌毒 9 词 |
| RateLimiter | gateway + Sentinel | 默认 100 QPS/IP，关键端点 5 QPS |
| 多租户越权 | 业务 Service 层 | 越权 → `OpcException("FORBIDDEN")` |
| 工具白名单 | `ToolRegistry` | `update_voucher` / `create_payment` **禁止暴露给 LLM 自调用** |

### 6.3 LLM 集成（HttpLlmClient）

```java
// opc-common 提供，所有需要 LLM 的服务都复用
String answer = HttpLlmClient.chat(provider, model, systemPrompt, userPrompt, timeoutMs);
```

- Provider 优先级：DeepSeek → OpenAI → 文心 → MiniMax
- 主 Provider 失败 → 自动 fallback（30s 内）
- 任何超时 / 5xx → 3 次指数退避（1s / 3s / 9s）
- Token 计量每次必写 `opc_agent_token_usage`（按 user_id + company_id）

### 6.4 Nacos 配置约定

- **dev**：`namespace=opc-dev`，每个服务 `<name>-dev.yml`
- **prod**：`namespace=opc-prod`，每个服务 `<name>-prod.yml` + 共享 `application-prod.yml`
- **关键**：`tenant=` 和 `namespaceId=` 写不同命名空间，**Spring Cloud 用 `namespaceId`**
- 敏感字段全部 `ENC(...)` 包裹，`JasyptEnvironmentPostProcessor` 启动 fail-fast on 明文
- `bootstrap.yml` 必须 `nacos.{config,discovery}.fail-fast: true`（prod）
- 启动类无额外校验，但 `OpcNacosStartupChecker`（`opc-common`）会 ping `/nacos/v1/cs/health`

### 6.5 通知 / 审计 / 定时

- **通知**：任何业务状态变更 → Feign `opc-notification`，**失败仅写日志，不阻塞主流程**（FallbackFactory）
- **审计**：沿用 `sys_oper_log`（RuoYi）+ `opc_audit_log`（敏感动作：finCENAME/HR Offer/CRM 合同）
- **定时**：所有 cron 任务放 `sys_job`（`ruoyi-job`），handler 必须在 `com.ruoyi.job.task` 包下；新工作流通过 `WorkflowCronJob` 触发 `opc-agent-hub`

---

## 7. 开发约定

### 7.1 包结构

```
ruoyi-modules/opc-<name>/
├── OpcXxxApplication.java          # @SpringBootApplication
│                                    # @ComponentScan("com.ruoyi.opc")
│                                    # @EnableRyFeignClients(basePackages = {"com.ruoyi.system.api","com.ruoyi.opc"})
├── controller/                       # @RestController + @RequestMapping("/opc/<name>")
├── service/impl/
├── domain/                           # Lombok @Data @Builder
├── mapper/                           # @Mapper interface
├── dto/                              # 入参 / 出参, snake_case JSON 用 @JsonProperty
├── enums/                            # 状态机/分类
├── feign/                            # OpenFeign client + FallbackFactory
└── config/                           # @ConfigurationProperties
```

### 7.2 命名 & ID

| 项 | 约定 |
|----|------|
| 服务名 | `opc-<name>`（小写、连字符） |
| 数据库 | 共享 `aiopc`，每服务独立 schema 或前缀（当前全部共享库 + `opc_` 前缀） |
| 表 | `opc_<service>_<entity>`（单数：voucher 不是 vouchers） |
| 列 | `snake_case`；金额 `DECIMAL(12,2)`；时间 `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| 状态值 | `DRAFT/CONFIRMED/COMPLETED/CANCELLED`（业务单）/ `OPEN/CLOSED`（任务） |
| ID | `SnowflakeIdWorker`（`opc-common`） |
| API path | `/opc/<service>/<resource>`；动词操作 `/.../{id}/<verb>`（`/publish` `/cancel` `/approve`） |

### 7.3 Controller / Service / Mapper 模板

**Controller（薄）**

```java
@RestController
@RequestMapping("/opc/<svc>/<resource>")
@RequiredArgsConstructor
public class Opc<Res>Controller {
    private final IOpc<Res>Service service;

    @GetMapping("/list")
    public TableDataInfo list(Opc<Res>Query q) { return service.list(q); }

    @PostMapping
    @Log(title = "新建<Res>", businessType = BusinessType.INSERT)
    public R<Opc<Res>> create(@RequestBody Opc<Res>CreateReq req) { return R.ok(service.create(req)); }
}
```

**Service（厚）**

```java
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class Opc<Res>ServiceImpl implements IOpc<Res>Service {
    private final Opc<Res>Mapper mapper;
    private final Remote<Other>Service remoteXxx;     // Feign
    private final HttpLlmClient llmClient;            // LLM

    @Override
    public Opc<Res> create(Opc<Res>CreateReq req) {
        Long userId = SecurityUtils.getLoginUserIdOrNull();
        Long companyId = SecurityUtils.getCompanyIdOrDefault();
        if (companyId == null) throw new OpcException("公司未选定");

        // 1. 业务规则校验
        validate(req);
        // 2. 默认列补齐
        if (req.getScore() == null) req.setScore(0);
        // 3. 主键 + 审计
        Opc<Res> entity = Opc<Res>.builder()
            .id(SnowflakeIdWorker.nextId())
            .companyId(companyId).createdBy(userId).createTime(now())
            .status("DRAFT").build();
        mapper.insert(entity);
        // 4. 跨服务调用（用 Feign + Fallback，不影响主流程）
        try { remoteNotificationGateway.send(...); } catch (Exception ignore) {}
        return entity;
    }
}
```

**Mapper（薄）**

```java
@Mapper
public interface Opc<Res>Mapper extends BaseMapper<Opc<Res>> {
    int insert(Opc<Res> e);
    Opc<Res> selectById(@Param("id") Long id);
    List<Opc<Res>> selectList(@Param("companyId") Long companyId, ...);
    int updateById(@Param("e") Opc<Res> e);
    int deleteById(@Param("id") Long id);
}
```

**Mapper XML** 必须在 `src/main/resources/mapper/<EntityName>Mapper.xml`，命名空间 `com.ruoyi.opc.<svc>.mapper.<EntityName>Mapper`。

### 7.4 启动类必须

```java
@SpringBootApplication
@ComponentScan("com.ruoyi.opc")
@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@EnableDiscoveryClient
public class OpcXxxApplication {
    public static void main(String[] args) { SpringApplication.run(...); }
}
```

`application.yml` 必须 `spring.main.allow-bean-definition-overriding: true` + `spring.cloud.allow-bean-definition-overriding: true`，且在 `spring.autoconfigure.exclude` 列出 25 个 OpenAI / ZhiPu AI 配置类。

### 7.5 Thin jar & 镜像

- pom `<phase>none</phase>` 阻止 spring-boot:repackage
- Dockerfile 用 `java -cp "xxx.jar:lib/*" Main-Class`
- 先 `mvn dependency:copy-dependencies` 拷 198-257 依赖到 `target/dependency/`
- **不要 `VOLUME /home/...`**（容器首次启动会被外部 mount 覆盖）

---

## 8. 测试策略

### 8.1 单测（JUnit5 + Mockito）

- **覆盖率**：业务 Service ≥ 80%，Controller 100%
- **MockedStatic**：`SecurityUtils.getUserId()` + 各 Feign client
- **状态机**：所有合法 + 非法转换（`X → Y → Z`，非法 `X→Z` 抛 `BizException`）
- **多租户**：构造 companyId=1 实体 + mock 查询 → assert 测试返回限定 companyId
- **NOT NULL DEFAULT**：service.create() 测不传默认值时实际补齐
- **POM 突变测试**：opc-finance ≥ 80%、ruoyi-gateway ≥ 75%（`pit` profile）

**Controller 单测**：`@WebMvcTest` + `MockMvc`（W6 模板）

### 8.2 E2E（Python）

`tmp_e2e/e2e_<svc>.py`：
- 顶层 `BASE_URL = "http://localhost:8079"`（走 gateway 真实路径）
- `export PYTHONIOENCODING=utf-8`（避免中文 json.load 失败）
- 必须断言 `body.code`（RuoYi 错误也返回 HTTP 200）
- WS 用 websocket-client 测 Inbox

### 8.3 LLM 评测 + 红队（`opc-ai-core`）

- **Eval**：`EvalRunnerTest` 加载 `eval/<domain>-*.json`，断言通过率 ≥ 85%
- **RedTeam**：`RedTeamRunnerTest` + `PromptGuardHardeningTest`，断言 ASR ≤ 10% / 误杀率 ≤ 5%
- **Eval JSON 字段**：`@JsonProperty("snake_case")`（snake_case JSON + `@JsonIgnoreProperties` = null）
- 任何 prompt 变更 → CI 必跑这两个 Test

### 8.4 健康检查（`deploy/scripts/health-check.sh`）

- 每个服务 ≥ 6 个端点（health/list/create/detail/dashboard/etc.）
- 必须同时支持 `/v1/cs/health` 返回明文"UP" 和 `/login` 返回 `access_token`（兼容两种 RuoYi 版本）
- 22 容器目标 ≥ 50/50 PASS

---

## 9. 部署 / DevOps

### 9.1 启动顺序

```
1) 基础设施：nacos1 / mysql / redis / rabbitmq / qdrant / prometheus / grafana
2) 数据库：mysql-initdb.d/01-93*.sql 容器首次启动自动跑（仅 /var/lib/mysql 空时）
3) Nacos 配置：deploy/nacos/import-dev.sh 推 12 个 dev yml
4) 后端：ruoyi-gateway → ruoyi-auth → ruoyi-system → 12 OPC 服务
6) Frontend：vue3-typescript dist → nginx（端口 8079）
```

### 9.2 Helm chart（`deploy/helm/opc/`）

- 7-12 个服务（gateway + ai-core + 业务服务 + system）
- `tier` label：ai/business/gateway/system
- `hpa.yaml` 用 `range $name, $svc := .Values.services`
- values 3 套：dev=18 / staging=28 / prod=29 / default=28 resources by kind
- CI：`helm lint` + `helm template test | grep '^kind:' | sort | uniq -c` + `python deploy/helm/opc/ci/diff-envs.py`

### 9.3 RECOVERY（`springboot3/deploy/RECOVERY.md`）

15 节一键恢复文档：
- §1 前置依赖 / §2 启动步骤 / §3 服务范围 / §4 备份 / §5 位置 / §6 Nacos / §7 前端 / §8 坑 / §9 清单 / §10 Notification 增量 / §11 CRM 增量 / §12 Snapshot / §13 一键 deploy / §14 Recurring / §15 Remote SSH Bootstrap

### 9.4 一键工具

| 脚本 | 用途 |
|------|------|
| `deploy/deploy.sh` | up / status / stop / nuke / rebuild / snapshot / health |
| `deploy/scripts/snapshot-stack.sh` | 全栈快照（容器 + MySQL + Redis + Nacos） |
| `deploy/scripts/health-check.sh` | 22+ 端点健康检查 |
| `deploy/scripts/backup-mysql.sh` | mysqldump 全量备份 |
| `springboot3/deploy/bootstrap-remote.sh` | 远程 SSH 一键 bootstrap（5 阶段 idempotent） |

### 9.5 部署后必跑（W51+）

```bash
# 上游 IP 漂移导致前端 502，必须重建前端
docker compose build aiopc-frontend && docker compose up -d aiopc-frontend
```

---

## 10. 业务设计 Checklist

### 10.1 新模块从 0 到上线（标准 19 步）

| # | 步骤 | 产出 | 参考 |
|---|------|------|------|
| 1 | brainstorming skill | output spec draft | [[aiopc-roadmap-expansion]] |
| 2 | ExitPlanMode 审批 | spec 终稿 | `docs/superpowers/specs/<date>-<svc>-design.md` |
| 3 | plan 阶段 | 18-22 task 计划 | `docs/superpowers/plans/<date>-<svc>-impl.md` |
| 4 | scaffold | pom + Dockerfile + app + bootstrap.yml | W72 ERP task1 |
| 5 | MySQL schema | `V<date>__opc_<svc>_schema.sql` + seed | task2 |
| 6 | enums | 5-6 状态机枚举 | task3 |
| 7 | entities | 6 POJO Lombok @Data @Builder | task4 |
| 8 | mappers | 6 Mapper interface + XML + MybatisConfig | task5 |
| 9 | services | 6 ServiceImpl + 单测 | task6-12 |
| 10 | feign | 1-4 Feign + FeignConfig + FallbackFactory | task13 |
| 11 | controllers | 6-7 Controller + 41 endpoints | task14 |
| 12 | deployment | Nacos dev/prod + docker-compose + Dockerfile | task15 |
| 13 | deploy | thin jar 94K + 204 deps + 容器启动 < 30s | task16 |
| 14 | health + recovery + helm | health-check +N + RECOVERY +N + Helm chart | task17 |
| 15 | frontend | 4-8 Vue 3 views + <svc>.ts + router | task18 |
| 16 | e2e | `<svc>_e2e.py` 10-23/23 PASS | task19 |
| 17 | VERIFICATION | `OPC-W<N>-VERIFICATION-<svc>.md` | task20 |
| 18 | git push | `验证通过的改动自动 git add + commit + push origin main` | W79 |
| 19 | 文档回填 | `MEMORY.md` + 子条目 | — |

### 10.2 设计阶段红线（业务设计师必查）

- [ ] 业务表全部带 `company_id` + `idx_company_*`
- [ ] NOT NULL 列必须有 DEFAULT，且 Service 层 create() 显式补默认
- [ ] 状态机用枚举 + 显式 transitionTo()，禁直接 UPDATE
- [ ] 跨服务调用：必须 Feign + `@InnerAuth` + FallbackFactory
- [ ] LLM 调用入口必前置 PromptGuard
- [ ] 敏感字段（密码/token/key）必 Jasypt `ENC(...)`
- [ ] 高频定时任务放 `sys_job`，handler 在 `com.ruoyi.job.task` 包下
- [ ] 审计日志：所有写操作 `@Log(title=, businessType=)`
- [ ] RateLimit 关键端点（Sentinel）
- [ ] 移动端响应式：< 768px 用 ResponsiveTable（卡片列表）

### 10.3 上线前红线（测试工程师必查）

- [ ] Service 单测覆盖率 ≥ 80%
- [ ] Controller `@WebMvcTest` 100%
- [ ] E2E ≥ 10 步，全 PASS
- [ ] Eval 通过率 ≥ 85%（LLM 调用前）
- [ ] RedTeam ASR ≤ 10% / 误杀率 ≤ 5%
- [ ] PIT mutation ≥ 阈值（ai-core 80 / gateway 75）
- [ ] health-check.sh 全栈 ≥ 50/50 PASS
- [ ] helm lint + helm template 渲染成功
- [ ] 前端 type-check + build 通过
- [ ] DTO snake_case JSON 用 `@JsonProperty` 标注
- [ ] SecurityUtils.getUserId() 用 `getLoginUserIdOrNull()` 替代
- [ ] LLM 调用有 3 次指数退避 retry

---

## 11. 常见陷阱 & 教训（W48-W79 沉淀）

> 完整记录见各模块 VERIFICATION 报告和 `MEMORY.md` 中的 W*-子条目。

### 11.1 配置类

1. **`mybatis-plus:` 别名误配 → mapper xml 不加载**
   - opc-common 引入 vanilla `mybatis-3.5.19.jar` 时，若 OPC 服务 yml 用 `mybatis-plus:`，mapper xml 加载失败 → `Invalid bound statement`
   - **修法**：所有 OPC 服务 yml 用 `mybatis:` 而非 `mybatis-plus:`

2. **Nacos `tenant=` vs `namespaceId=`**
   - 两者写不同命名空间，Spring Cloud 用 `namespaceId`
   - 写错导致 dev 与 prod 数据混淆

3. **`spring.cloud.gateway.server.webflux` 路径**
   - Spring Cloud Gateway 4.x 路由 prefix 从 `spring.cloud.gateway` 改到 `spring.cloud.gateway.server.webflux`
   - 老 yml 下 routes 一直为 `[]` → /login 走 static resource fallback → 404

4. **Jasypt 明文 fail-fast**
   - `JasyptEnvironmentPostProcessor` 启动时检查 `ENC(...)` 包裹，启动环境变量缺 `JASYPT_PASSWORD` 时 fail-fast
   - 本机开发口令 `OpcEncrypt!2026`，prod 必须用环境变量

### 11.2 Docker & 部署

5. **`VOLUME /home/...` 不能有**
   - Dockerfile 一旦声明 VOLUME，外部 bind mount 会覆盖容器内文件
   - 教训：日志、配置都走 Nacos / Redis，不依赖容器文件系统

6. **frontend build 不自动重建**
   - 改了上游服务 IP（如 aiopc-gateway 重建）必须显式 `docker compose build aiopc-frontend && docker compose up -d aiopc-frontend`
   - 否则前端 nginx 配置照旧 IP，访问上游 111 connection refused → 502

7. **gateway 用 `lb://` 失败**
   - Docker DNS 不解析 Nacos serviceId，必须直连 `http://aiopc-<svc>:<port>`

8. **gateway 白名单过宽误伤**
   - `whites` 只支持 path-only，不支持 HTTP method 维度
   - 教训：白名单精确到具体路径 + controller 层 `SecurityUtils.getUserId()` 二重防御

### 11.3 数据库 & 并发

9. **NOT NULL DEFAULT 不生效**
   - 列 `score INT NOT NULL DEFAULT 0` 当 INSERT XML 显式列名时 MySQL 不应用 DEFAULT
   - **修法**：service.create() 必 `if (score == null) score = 0`（W50 CRM 教训）

10. **FIFO 批次并发扣减**
    - 跨批次扣减需事务 + 行锁 `SELECT ... FOR UPDATE`
    - 库存预警 24h 去重用 Caffeine 内存缓存

11. **MySQL init 默认 latin1**
    - 容器首次启动 latin1 存中文 double-encoded
    - **修法**：`--default-character-set=utf8mb4 SOURCE` 重导入

12. **response 编码 Tomcat 默认 ISO-8859-1**
    - 三件套：`server.servlet.encoding` + `tomcat.uri-encoding` + `spring.http.encoding` 配合 `spring.jackson`

### 11.4 LLM & AI

13. **DTO snake_case JSON 全部 null**
    - `@JsonIgnoreProperties` + snake_case JSON = 所有字段 null
    - **修法**：所有 DTO 字段显式 `@JsonProperty("snake_case")`（W75 content 教训）

14. **LLM 必须 3 次指数退避 retry**
    - 超时 / 5xx → 1s / 3s / 9s 三次
    - 否则瞬时抖动导致 user-visible 失败

15. **PromptGuard 正则要匹配「意图」不是「名词」**
    - "跳过审核" 是攻击意图，"审核流程" 是中性名词
    - **修法**：正则只匹配「要求规避内控」的动词短语

16. **侧信道推断（错误消息差异）**
    - 工具错误消息未归一化 → 攻击者用 `公司是否存在` 推断记录
    - **修法**：工具错误统一抛 `BizException("NOT_FOUND")`，不暴露细节

### 11.5 测试 & 质量

17. **`@MockitoSettings(strictness = LENIENT)` + `MockedStatic<SecurityUtils>`**
    - `@BeforeEach` 开启 + `@AfterEach` close
    - 静态部分调用 = 调用 + close

18. **`EmptyResultDataAccessException(int)`**
    - 单测期望"查不到记录"用这个而不是 null

19. **`argThat(Predicate)`**
    - 时间戳 / UUID 风格参数匹配

20. **e2e 必须断言 `body.code`**
    - RuoYi 错误也返回 HTTP 200
    - **教训**：W49 notification 集成前 `body.code=500` 也通过

21. **Eval JSON gotcha**
    - `@JsonIgnoreProperties` 一定不要无脑加，snake_case JSON 必 `@JsonProperty`

### 11.6 配置 & 凭证

22. **MySQL 8 `ADD COLUMN IF NOT EXISTS` 不存在**
    - Flyway 脚本必须先查 `INFORMATION_SCHEMA.COLUMNS`
    - 或在事务里 DROP+ADD

23. **Quartz sys_job seed `concurrent='1'`**
    - '0' 是允许并发，'1' 是禁止（双保险）
    - 命名反直觉，必须查表

24. **Docker DNS 解析 serviceId 失败**
    - gateway → upstream 必 `http://aiopc-<svc>:<port>` 直连
    - 不要 `lb://aiopc-<svc>`（Nacos 注册的 serviceId Docker 不识别）

25. **Cron job handler 包名**
    - 必须 `com.ruoyi.job.task` 否则 `ScheduleUtils.whiteList()` 拦截
    - 抛 `IllegalStateException` 才能让 Quartz 标 failed

---

## 12. 模块依赖矩阵（业务设计师快速查）

```
调用方 \ 被调方  | ai-core | user-center | agent-hub | billing | finance | notif | insight | erp | crm | hr | content | community
----------------|---------|-------------|-----------|---------|---------|-------|---------|-----|-----|----|---------|-----------
user-center    |         |             |           |         |         |   ✅  |         |     |     |    |         |
agent-hub      |   ✅    |     ✅      |           |   ✅    |   ✅    |   ✅  |         |     |     |    |         |
billing        |         |     ✅      |           |         |         |   ✅  |         |     |     |    |         |
finance        |   ✅    |             |           |   ✅    |         |   ✅  |         |     |     |    |         |
notif          |         |             |           |         |         |       |         |     |     |    |         |
insight        |   ✅    |     ✅      |           |   ✅    |   ✅    |   ✅  |         |     |     |    |         |
erp            |   ✅    |             |           |         |         |   ✅  |         |     |     |    |         |
crm            |   ✅    |     ✅      |           |         |         |   ✅  |         |     |     |    |         |   ✅
hr             |   ✅    |     ✅      |           |         |         |   ✅  |         |     |  ✅ |    |         |
content        |   ✅    |     ✅      |           |   ✅    |         |   ✅  |         |     |     |    |         |
community      |         |     ✅      |     ✅    |         |         |   ✅  |         |     |     |    |         |
```

**实现方式**：全部 OpenFeign + `@InnerAuth` + `from-source: INNER` header + FallbackFactory

---

## 13. 验收 Checklist（新模块上线前必走）

### 13.1 后端

- [ ] `mvn -pl ruoyi-modules/opc-<svc> test` 全 PASS
- [ ] `mvn -pl ruoyi-modules/opc-<svc> -am package` 产 thin jar < 200 KB
- [ ] `mvn dependency:copy-dependencies` 拷 200+ 依赖到 `target/dependency/`
- [ ] Docker build 成功，thin jar Dockerfile COPY 失败模式
- [ ] 容器启动 < 30s + health UP
- [ ] Nacos dev yml 推入 `opc-dev` 命名空间
- [ ] Nacos prod yml 推入 `opc-prod` 命名空间
- [ ] Jasypt 加密 + fail-fast 验证
- [ ] Gateway 路由 + 白名单 patch
- [ ] `deploy/scripts/health-check.sh` +N 个端点 PASS
- [ ] RECOVERY.md 新增一节

### 13.2 前端

- [ ] `npm run type-check`（vue-tsc --noEmit）通过
- [ ] `npm run build`（Vite build）成功
- [ ] 移动端 360 / 414 / 768 / 1024 四档断点无错位
- [ ] ResponsiveTable 在 < 768px 渲染卡片列表
- [ ] Router meta.icon 自动生成菜单（layout/index.vue）
- [ ] Playwright 截图（关键页面）

### 13.3 AI / LLM

- [ ] 所有 prompt 引用 `system-safety-v0.3.ftl` 8 条红线
- [ ] PromptGuard 前置 validate（每个 LLM 调用入口）
- [ ] `EvalRunnerTest` 通过率 ≥ 85%
- [ ] `RedTeamRunnerTest` ASR ≤ 10% / 误杀率 ≤ 5%
- [ ] 3 次指数退避 retry（1s / 3s / 9s）
- [ ] Token 计量每次必写 `opc_agent_token_usage`
- [ ] Provider 主备 fallback 30s 内

### 13.4 Helm / 部署

- [ ] `helm lint deploy/helm/opc/` 0 errors
- [ ] `helm template test -f values-{dev,staging,prod}.yaml` 渲染成功
- [ ] `python deploy/helm/opc/ci/diff-envs.py` 三套 values diff 正常
- [ ] HPA（ai-core 2-8，business 2-4）合理

### 13.6 文档

- [ ] `docs/superpowers/specs/<date>-opc-<svc>-design.md` 已存档
- [ ] `docs/superpowers/plans/<date>-opc-<svc>-impl.md` 已存档
- [ ] `docs/verification/week-<N>/OPC-W<N>-VERIFICATION-<svc>.md` 已存档
- [ ] `MEMORY.md` 增加子条目
- [ ] README.md 模块清单更新

---

## 14. 进阶话题（业务设计师可读）

### 14.1 Prompt 版本化 & AB 测试

```
opc_ai_prompt_template
├── id, code, version
├── content (LONGTEXT)
├── enabled (0/1) — 仅一个 enabled=1 是「当前生效」
├── eval_pass_rate, redteam_asr
└── published_at, published_by
```

**升级流程**：新版本 prompt → EvalRunnerTest ≥ 85% → RedTeamRunnerTest ASR ≤ 10% → 手动切换 enabled=1 → 旧版本 enabled=0 保留审计

### 14.2 Workflow 编排（`opc-agent-hub`）

```
opc_agent_workflow.code → WorkflowTriggerService.trigger(code)
↓
WorkflowEngine.run(code, context)
↓
按顺序执行 step：
   1. 调 LLM
   2. 调 Tool（白名单内）
   3. 写 opc_* 业务表
   4. Feign 推 opc-notification
↓
opc_agent_workflow_run 记录 status / duration / cost
```

**Cron 触发**：放 `sys_job` → `WorkflowCronJob` → Feign `from-source: INNER` → `WorkflowTriggerService.trigger(code)`
**Dedupe**：`WorkflowTriggerService` 无 `@Transactional`（RUNNING 行必须 commit 立即可见）

### 14.3 邀请链路（业务典型流程参考）

```
A: POST /opc/user/invitations/generate
   → OpcCodeGenerator.inviteCode() (8 位 base32)
   → 5-retry 冲突避免
   → INSERT opc_invitation

B: GET /opc/user/invitations/{code}  (公开)
   → 落地页渲染

A 登录 B 接受 → POST /opc/user/invitations/accept
   → 校验 ACTIVE / 未过期 / 未用完
   → INSERT IGNORE opc_company_member (B 加入 A 公司 STAFF)
   → INSERT opc_transaction (RECHARGE +50)
   → UPDATE opc_wallet.balance += 50
   → UPDATE opc_invitation (status=USED)
```

**关键常量**：`MAX_ACTIVE_PER_USER=50`、`DEFAULT_EXPIRE_DAYS=90`、`DEFAULT_MAX_USES=1`、`INVITER_REWARD=50.00`

### 14.4 多模型策略

| Provider | 用途 | 优先级 |
|---------|-----|--------|
| DeepSeek | 中文长文本 / 中文推理 | 1（主） |
| OpenAI gpt-4o-mini | 通用 / fallback | 2 |
| 文心 ERNIE-Bot | 中文敏感行业（涉政） | 3 |
| MiniMax（MiniMaxAI） | 国产化 / 长上下文 | 4 |

切换方式：Nacos `opc-ai-core-prod.yml` 改 `opc.llm.primary=<provider>:<model>`

### 14.5 LLM 安全 3 层（参考 OPC-SECURITY-REPORT-v0.1）

1. **正则层（PromptGuard）**：90% 攻击在正则层拦截，不消耗 token
2. **系统提示词层（system-safety-v0.3）**：8 条不可协商红线
3. **模型自觉层**：剩余 ~10% 由 LLM 自主拒答

**关键指标**：ASR ≤ 10%，误杀率 ≤ 5%，CRITICAL 类攻击 ASR = 0

---

## 15. 不在 OPC v1 范围（设计师不要承诺）

- 多仓库（当前共享 aiopc 库 + opc_ 前缀）
- 多币种（单 CNY）
- 多语言（仅中文）
- 移动 App（RN 推迟到 Iter 3，W85+）
- 区块链 / Web3
- 海外部署（仍单机房）
- 公网开放注册（现邀请制）
- 自研 ASR/TTS（用阿里云/讯飞）
- 商户实名认证（用现有邀请链路代替）

---

## 附录 A：关键命令速查

```bash
# 后端
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-ai-core test    # 唯一本机可全跑的模块

# 前端
cd vue3-typescript
npm install
npm run type-check
npm run build

# LLM 安全门禁
mvn -pl ruoyi-modules/opc-ai-core test \
  -Dtest=RedTeamRunnerTest,PromptGuardHardeningTest

# Helm
helm lint springboot3/deploy/helm/opc
helm template test springboot3/deploy/helm/opc -f values-dev.yaml | less

# 部署
cd springboot3/deploy
./deploy.sh up
./deploy.sh health
./deploy.sh rebuild aiopc-frontend   # 重建前端

# 数据库
mysql --default-character-set=utf8mb4 -uroot -p'Opc@2026!' opc < ./sql/XXX.sql

# Nacos
./nacos/import-prod.sh
./nacos/apply-whitelist.sh prod
```

---

## 附录 B：模块模板链接

| 模块 | 设计 spec | 计划 plan | VERIFICATION |
|------|----------|-----------|--------------|
| 通知 | (无 spec 独立) | — | `docs/verification/week-49/` |
| CRM | (无 spec 独立) | — | `docs/verification/week-50/` |
| 社区 | (无 spec 独立) | — | (无独立 VERIFICATION) |
| HR | `2026-09-11-opc-hr-design.md` | `2026-09-11-opc-hr-impl.md` | `week-71/` |
| ERP | `2026-09-12-opc-erp-design.md` | `2026-09-12-opc-erp-impl.md` | `week-72/` |
| 内容 | `2026-09-14-opc-content-design.md` | `2026-09-14-opc-content-impl.md` | `week-74/` `week-75/` |
| 远程部署 | `2026-09-22-remote-ssh-opc-deploy-design.md` | `2026-09-22-remote-ssh-opc-deploy.md` | `late/W79-...` |
| 扩展路线图 | `2026-09-10-opc-roadmap-expansion-design.md` | — | — |

---

## 附录 C：核心常量与业务规则

| 常量 | 值 | 含义 |
|------|----|------|
| `MAX_ACTIVE_INVITATIONS_PER_USER` | 50 | 单用户未用邀请码上限 |
| `INVITE_CODE_LENGTH` | 8 | base32 邀请码长度 |
| `INVITE_CODE_ALPHABET` | 31 字符（去 I/L/O/0/1） | 防混淆 |
| `DEFAULT_EXPIRE_DAYS` | 90 | 邀请码有效期 |
| `DEFAULT_MAX_USES` | 1 | 一次性 |
| `INVITER_REWARD` | 50.00 元 | 接受邀请奖励 |
| `SnowflakeIdWorker` | opc-common | 全局统一 ID 生成 |
| `AgentRuntime.maxSteps` | 8 | ReAct 最大步数 |
| `LLMRetryPolicy` | 1s/3s/9s | 指数退避 |
| `Sentinel.defaultFlow` | 100 QPS/IP | 默认限流 |
| `Sentinel.criticalFlow` | 5 QPS | 关键端点限流 |
| `AsrTarget` | ≤ 10% | 红队攻击成功率 |
| `FpTarget` | ≤ 5% | 合法请求误杀率 |
| `EvalPassRate` | ≥ 85% | 评测通过率 |
| `PitThreshold.aiCore` | 80% | ai-core 突变测试 |
| `PitThreshold.gateway` | 75% | gateway 突变测试 |
| `HealthCheck.target` | ≥ 50/50 | 全栈健康检查 |
| `TouchTarget.min` | 44×44 px | iOS HIG |

---

> 最后更新：2026-09-23 · 维护者：OPC 平台架构组
> 反馈渠道：提 issue 或更新 `MEMORY.md` 子条目