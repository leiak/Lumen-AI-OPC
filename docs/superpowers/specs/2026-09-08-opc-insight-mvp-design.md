# M4 — OPC 数据洞察 Agent (INSIGHT) MVP 设计

**Date**：2026-09-08
**Status**：Draft (待 user review)
**Author**：Claude (brainstorming session)
**Project**：OPC-Agent-Community (OAC) · Lumen-OPC
**Phase**：M4 — 第二业务 Agent 启动 (MVP §7 升级路径)

---

## 0. 背景与目标

OPC 平台已有 1 个成熟业务 Agent：**财务 Agent** (FINANCE) — 凭证/流水/税报/对账。本 M4 启动第二个业务 Agent：**数据洞察 Agent (INSIGHT)** — 给 OPC 创业者/老板一个"AI 数据参谋"。

MVP 范围：4 个能力完整交付 — **日报 / 经营驾驶舱 / 异常预警 / 决策建议**。所有能力**不写**业务数据（无 voucher/flow 写权限），仅做 LLM 汇总 + 规则判定 + 决策建议生成。

交付节奏：**方案 B — 8 天标准**（与 W1 8 Task 节奏对齐）。

---

## 1. 架构与模块布局 (§1)

### 1.1 新增 1 个 backend service：`opc-insight` (port 9306)

```
springboot3/ruoyi-modules/opc-insight/
├── OpcInsightApplication.java
├── controller/
│   ├── DashboardController.java            # /opc/insight/dashboard
│   ├── AlertController.java                # /opc/insight/alerts
│   ├── DailyReportController.java          # /opc/insight/daily
│   └── AdviceController.java               # /opc/insight/advice
├── service/
│   ├── KpiService.java                     # 4 能力共用聚合器
│   ├── AnomalyService.java                 # 规则 + LLM 二级
│   ├── DailyReportService.java             # 复用 workflow cron
│   └── AdviceService.java                  # LLM 决策建议
├── client/                                  # Feign clients
│   ├── RemoteFinanceService.java           # 调 opc-finance 聚合端点
│   ├── RemoteBillingService.java           # 调 opc-billing 聚合端点
│   └── RemoteUserCenterService.java        # 调 opc-user-center
├── domain/                                  # 3 张表的 domain
├── mapper/                                  # 3 张表的 mapper
└── workflow/
    └── InsightDailyReportJob.java          # Quartz cron 09:00 跑日报
```

### 1.2 新增 1 个 SQL migration

文件：`springboot3/sql/migrations/V20260908__opc_insight_schema.sql`

```sql
CREATE TABLE opc_insight_daily_report (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,
  period          DATE         NOT NULL,
  summary_md      MEDIUMTEXT,
  kpi_json        JSON,
  advice_md       MEDIUMTEXT,
  llm_used        VARCHAR(64),
  create_by       VARCHAR(64),
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64),
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_company_date (company_id, period)
);

CREATE TABLE opc_insight_anomaly (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,
  period          DATE         NOT NULL,
  level           VARCHAR(8)   NOT NULL,         -- HIGH / MEDIUM / LOW
  rule_code       VARCHAR(64)  NOT NULL,
  description     VARCHAR(512),
  status          VARCHAR(16)  DEFAULT 'OPEN',   -- OPEN / ACK / RESOLVED
  llm_confidence  DECIMAL(3,2),
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_status (company_id, status),
  KEY idx_period (period)
);

CREATE TABLE opc_insight_advice (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,
  topic           VARCHAR(64)  NOT NULL,         -- cost_optimization / cashflow_warning / etc
  advice_md       MEDIUMTEXT,
  llm_used        VARCHAR(64),
  confidence      DECIMAL(3,2),
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_company_topic_time (company_id, topic, create_time)
);
```

### 1.3 新增 prompts（opc-ai-core）

```
opc-ai-core/src/main/resources/prompts/
├── insight-system-v1.0.txt          # 主 system prompt（"小数" Agent）
├── insight-soft-anomaly-v1.0.txt    # LLM 软扫描异常 prompt
└── insight-advice-v1.0.txt          # 决策建议 prompt
```

### 1.4 新增 eval 集（opc-ai-core）

```
opc-ai-core/src/main/resources/eval/
├── insight-agent-v1.0.json          # 80 cases（kpi_summary 30 + anomaly_rule_match 20 + soft_anomaly_llm 15 + advice_quality 15）
└── insight-redteam-10.json           # 10 cases（越权 + 提示词注入 + 误报陷阱）
```

### 1.5 前端 4 个新页面 + 1 个路由 tab

```
vue3-typescript/src/views/opc/insight/
├── dashboard.vue       # 4 KPI 卡 + 2 趋势图 + 异常 Top5 + 建议 Top3
├── alerts.vue          # 异常列表 + 详情侧滑 + ACK 按钮
├── daily.vue           # 日报历史 (按日期) + 当日详情
└── advice.vue          # 决策建议列表 + 详情 + 重新生成

vue3-typescript/src/router/index.ts   # 加 /opc/insight/* 路由（meta: { activeMenu: '/opc/insight' }）
```

### 1.6 复用资产

| 资产 | 用途 | 引用 |
|---|---|---|
| opc-finance 聚合端点 4 个 | voucher/flow/tax 汇总 | W1 后续 + 本 spec §3 |
| opc-billing 聚合端点 3 个 | wallet/order/token 汇总 | 同上 |
| opc-agent-hub workflow cron | 日报触发器 | Task #3 已收官 |
| opc-ai-core LLM Gateway | DeepSeek + GPT-4o-mini + 自动 fallback | 已有 |
| opc-ai-core PromptGuard v0.3 | LLM 输入过滤 | Task #8 |
| opc-ai-core EvalRunner | 评测驱动 | Task #1 |
| ruoyi-job sys_job 表 | Quartz 任务持久化 | 已有 |

---

## 2. 组件与职责 (§2)

| 组件 | 职责 | 输入 | 输出 | 依赖 |
|---|---|---|---|---|
| **KpiService** | 拉 finance/billing/user 数据，按公司聚合 | companyId, period | KpiSnapshot { revenue, expense, voucherCount, anomalyCount, walletBalance, tokenUsage, ... } | 3 Feign client |
| **DashboardController** | `/opc/insight/dashboard` | companyId (JWT) | DashboardVo { kpis, trends, alertsTop5, adviceTop3 } | KpiService + AnomalyService + AdviceService |
| **AnomalyService** | 硬规则 + LLM soft scan 二级 | KpiSnapshot | List\<AnomalyVo\> { level, rule_code, description, action } | KpiService + LLM Gateway |
| **AlertController** | `/opc/insight/alerts` + 详情 + ACK | companyId, filter | AnomalyVo[], 详情, ack 接口 | AnomalyService + mapper |
| **DailyReportService** | 拉快照 + LLM 汇总 | companyId, date | DailyReportVo { summary, kpi_table, advice } | KpiService + LLM Gateway + WorkflowCronJob |
| **DailyReportController** | `/opc/insight/daily` + 历史 + 详情 | companyId, dateRange | DailyReportVo[] + 详情 | DailyReportService + mapper |
| **AdviceService** | LLM 决策建议 + 7 天缓存 | companyId + topic | AdviceVo { topic, advice, confidence, sources } | KpiService + LLM Gateway + mapper |
| **AdviceController** | `/opc/insight/advice` + 详情 + 重新生成 | companyId, topic | AdviceVo[] + 详情 | AdviceService |
| **InsightDailyReportJob** | Quartz cron 09:00 触发日报生成 | — | void | DailyReportService + all active companies |

**关键设计**：
- **KpiService 是 4 能力的共用底座** — 4 个 controller 全部走它，避免重复 Feign
- **AnomalyService 二级判定** — 硬规则（HIGH/MEDIUM）直接入库 + LLM soft scan（仅 LOW 且 confidence > 0.6 才入库）
- **DailyReport 与 WorkflowEngine 集成** — Quartz cron 09:00 跑 `InsightDailyReportJob.trigger()`，按所有 active company 跑
- **AdviceService 7 天缓存** — 同一 companyId+topic 在 7 天内复用 mapper 已有记录，避免 LLM 重复调用

---

## 3. 数据流 (§3)

### 3.1 Dashboard 数据流（每次访问实时拉）

```
User → GET /opc/insight/dashboard
  ↓ SecurityUtils.getCompanyId()
  ↓ KpiService.snapshot(companyId)
  ↓   ├─ RemoteFinanceService.getVoucherAgg(period)    [Feign]
  ↓   ├─ RemoteFinanceService.getFlowAgg(period)        [Feign]
  ↓   ├─ RemoteFinanceService.getTaxReport(period)      [Feign]
  ↓   ├─ RemoteBillingService.getWalletBalance()        [Feign]
  ↓   ├─ RemoteBillingService.getTokenUsage(period)     [Feign]
  ↓   └─ RemoteUserCenterService.getCompanyProfile()    [Feign]
  ↓ AnomalyService.scan(KpiSnapshot)                    [硬规则 + LLM soft]
  ↓ AdviceService.topN(companyId, 3)                    [查 mapper 缓存]
  ↓ return DashboardVo
```

### 3.2 日报数据流（cron 触发 + LLM）

```
Quartz cron 09:00 (Asia/Shanghai)
  ↓ InsightDailyReportJob.trigger()
  ↓   for each active company:
  ↓     try:
  ↓       DailyReportService.generate(companyId, today)
  ↓         KpiService.snapshot(companyId, today)
  ↓         LLM Gateway.chat(insight-system-v1.0 + insight-advice-v1.0, kpiJson)
  ↓         mapper.insert(dailyReport) — UNIQUE KEY uk_company_date 兜底
  ↓     catch:
  ↓       mapper.insert(anomaly { rule_code='DAILY_REPORT_FAILED' })
  ↓ continue next company
```

### 3.3 异常告警数据流

```
AnomalyService.scan(snapshot)
  ↓ for each rule in AnomalyRule enum:
  ↓   if rule.match(snapshot) → AnomalyVo { level=HIGH/MEDIUM, rule_code, description }
  ↓   mapper.insert(AnomalyVo)
  ↓ if (HIGH count == 0 && LLM_ENABLED):
  ↓   LLM Gateway.chat(insight-soft-anomaly-v1.0, snapshotJson)
  ↓   parse JSON → AnomalyVo[] { level=LOW, llm_confidence }
  ↓   filter: confidence > 0.6 → mapper.insert
  ↓ return List<AnomalyVo> (all)
```

### 3.4 决策建议数据流

```
User → POST /opc/insight/advice { topic: 'cost_optimization' }
  ↓ AdviceService.generate(companyId, topic)
  ↓   cached = mapper.findRecent(companyId, topic, last_7_days)
  ↓   if cached: return AdviceVo
  ↓   KpiService.snapshot(companyId, last_30_days)
  ↓   LLM Gateway.chat(insight-advice-v1.0, {topic, kpiJson})
  ↓   mapper.insert(advice)
  ↓ return AdviceVo
```

---

## 4. 错误处理 (§4)

| 失败模式 | 处理策略 |
|---|---|
| Feign 调用 finance/billing 失败（超时/降级） | Sentinel 熔断 → 走 fallback 返回**空 KpiSnapshot**（kpi 全 0） + 在 DashboardVo 标 `partial=true` 让前端展示 ⚠️ |
| LLM Gateway 全部模型失败 | 走 `[自动聚合·未走 LLM]` 模板（与 TaxReport 一致） + insight system prompt 显式支持降级 |
| 异常规则扫描时 DB 异常 | catch Exception → log warn → 返回空 List（不阻塞 dashboard） |
| 日报 cron 触发时某公司失败 | try/catch per company + 失败公司写 `opc_insight_anomaly { rule_code='DAILY_REPORT_FAILED' }`，不影响其他公司 |
| workflow cron 调度失败 | 复用 ruoyi-job 的 sys_job_log 失败机制，W3 验证过 |
| 重复生成日报（同公司同日） | `UNIQUE KEY uk_company_date` 兜底 + `INSERT ... ON DUPLICATE KEY UPDATE` |
| User 没 companyId（个人用户） | `OpcException("请先创建公司档案")` 401 引导去 `/opc/user/profile` |
| INSIGHT 越权访问别公司 | `@PreAuthorize` + `SecurityUtils.getCompanyId()` 强校验（与现有 finance 一致） |
| insight-redteam 命中攻击 prompt | PromptGuard v0.3 拦 90%+，剩余走 `[拒绝回答]` fallback |

---

## 5. 测试策略 (§5)

### 5.1 后端单元测试（JUnit5 + Mockito，与 W2-W4 一致）

| 文件 | @Test | 覆盖 |
|---|---|---|
| `KpiServiceImplTest` | 12 | 7 Feign 端点 mock 正常/超时/降级 + 空快照 |
| `AnomalyServiceImplTest` | 18 | 8 硬规则各 2 测（命中/不命中 = 16）+ LLM soft scan 1 测（参数化 5 case）+ 0 异常时 LLM 跳过 1 测 |
| `DailyReportServiceImplTest` | 10 | 正常生成 + LLM 失败降级 + 重复日期 UNIQUE 处理 + 3 家公司 cron 批量 |
| `AdviceServiceImplTest` | 8 | 缓存命中 + 缓存 miss + LLM 失败降级 + 越权拦截 |
| `InsightDailyReportJobTest` | 4 | 1 家公司成功 + 1 家公司失败不影响其他 + 全失败 log error + UNIQUE 重复日期 |
| `OpcInsightControllerMvcTest` | 12 | 4 端点 × 3 路径（正常/401/参数） |
| **小计** | **64** | 12+18+10+8+4+12 = 64 |

### 5.2 前端契约测试（vitest + axios-mock-adapter，与 W19-W25 一致）

| 文件 | @Test |
|---|---|
| `api/opc/insight.ts` wrapper | 12 |
| `views/opc/insight/dashboard.vue` SFC | 18 |
| `views/opc/insight/alerts.vue` SFC | 12 |
| `views/opc/insight/daily.vue` SFC | 12 |
| `views/opc/insight/advice.vue` SFC | 10 |
| **小计** | **64** |

### 5.3 AI 评测（opc-ai-core EvalRunner）

- `insight-agent-v1.0.json` 80 cases，按 4 能力分组：
  - `kpi_summary` 30 cases（断言关键数字正确）
  - `anomaly_rule_match` 20 cases（断言规则命中/不命中）
  - `soft_anomaly_llm` 15 cases（断言 LLM 软扫描合理）
  - `advice_quality` 15 cases（断言建议相关 + 长度 + 含 action item）
- `MockInsightLlmProvider` 规则+正则实现 v1.0（与 `MockFinanceLlmProvider` 同模式）
- baseline v0.1 → v0.2 迭代，目标 ≥ 85%
- redteam `insight-redteam-10.json` 10 cases（越权提示词注入、误报陷阱、跨公司数据请求）

### 5.4 集成 & 端到端

- 4 个 controller 的 `@WebMvcTest`（W6 风格）
- 1 个 workflow cron 集成测试（Day 9 触发 cron → 验证日报落库）
- 前端 4 页 E2E（playwright 可选，超出 M4 范围）

**测试总目标**：**218 测试**（64 后端 + 64 前端 + 80 eval + 10 redteam），与 W1-W25 测试强度对齐。

---

## 6. 8 天交付节奏（方案 B）

| Day | 后端 A (聚合+领域) | 后端 B (INSIGHT 主) | 前端 (4 页) | AI Eng (eval) |
|---|---|---|---|---|
| 1 | opc-finance 加 4 聚合端点 + 单测 | opc-insight 脚手架 + bootstrap.yml + Application | — | — |
| 2 | opc-billing 加 3 聚合端点 + 单测 | Feign clients (3) + KpiService 实现 + 12 测 | — | — |
| 3 | — | AnomalyService 完整 (8 硬规则 + LLM soft scan + 跳过逻辑) + 18 测 | dashboard 骨架 + 路由 | — |
| 4 | — | AnomalyService LLM 集成 + 与 KpiService 联调 | alerts 页 | insight-eval baseline v0.1 |
| 5 | — | DailyReportService + InsightDailyReportJob + 14 测 (10+4) | daily 页 | — |
| 6 | — | AdviceService + 7 天缓存 + 8 测 | advice 页 | insight v0.2 (80 cases → 85%+) |
| 7 | — | 4 端 controller + @WebMvcTest 12 测 + 集成 | 联调 4 页 | — |
| 8 | — | PromptGuard v0.1 + redteam 10 cases + 验证报告 | UAT | 收尾报告 |

**测试分配核对**：
- Day 2: 12 测 (KpiService)
- Day 3: 18 测 (AnomalyService 完整)
- Day 5: 14 测 (DailyReport 10 + Job 4)
- Day 6: 8 测 (Advice)
- Day 7: 12 测 (Controller @WebMvcTest)
- **累计 12+18+14+8+12 = 64 测**（与 §5.1 一致）

**Day 8 验收 checkpoint**：
- [ ] `mvn clean install` 所有模块通过
- [ ] `npm run test:run` 所有 vitest 通过
- [ ] `npm run build` 前端通过
- [ ] `mvn test -pl opc-ai-core -Dtest=EvalInsightRunnerTest` 80 cases ≥ 85%
- [ ] `mvn test -pl opc-ai-core -Dtest=InsightRedteamTest` ASR ≤ 10%
- [ ] 1 个 Quartz cron 集成测试通过（09:00 → 落日报）
- [ ] 7 个聚合端点单测全过
- [ ] [`OPC-M4-VERIFICATION-insight-mvp.md`](../../verification/milestones/m4-insight/OPC-M4-VERIFICATION-insight-mvp.md) 验证报告提交

---

## 7. 风险与依赖图

```
依赖：
  Day 1-2 opc-finance/opc-billing 加聚合端点
     ↓
  Day 2-3 opc-insight 接收 Feign 数据
     ↓
  Day 4-5 异常 + 日报功能
     ↓
  Day 6-7 决策 + 联调
     ↓
  Day 8 收尾 + 报告
```

**关键路径**：Day 1-2 聚合端点（不可并行，Day 3 才开始 INSIGHT）— 2 天延迟就打破 8 天节奏。

**风险**：
1. **opc-finance/billing 聚合端点命名冲突** — 实施 Day 1 第一步先 `git grep '/opc/finance/agg'` 验证是否被 W3 已占；如冲突则改名 `/opc/insight/agg/**` 让 INSIGHT 自己做二次聚合。
2. **Quartz cron 时区** — 复用 `0 0 9 * * ?` + `timezone=Asia/Shanghai` (Task #3 模板)，已验证。
3. **LLM 调用成本** — 80 eval × 2 轮 = 160 LLM call，DeepSeek 价约 ¥0.2/次，总 ¥32；cron 9 点对 100 公司 = 100 call/天 = ¥20/天。可接受。
4. **insight-eval Mock LLM 准确度** — 与 `MockFinanceLlmProvider` 同模式，目标 100% mock pass。但 mock ≠ real LLM 准确度，需 Day 8 跑 1 次 `-Peval-live` profile 真实 LLM 验证。
5. **越权防护** — INSIGHT 跨公司读数据是高风险入口，必须 `@PreAuthorize` + `SecurityUtils.getCompanyId()` 双重校验（Day 7 controller 集成测试覆盖）。

---

## 8. 验收标准

### 8.1 功能性
- [ ] 4 能力完整可用（dashboard 实时拉、cron 9 点日报、异常规则+LLM 二级、决策建议 7 天缓存）
- [ ] 多页 + tab 切换 UI 正常切换
- [ ] 端到端：注册公司 → 上传流水 → 雇佣 INSIGHT → 看 dashboard → 等 cron 看日报

### 8.2 质量性
- [ ] 218 测试全过
- [ ] mutation score ≥ 75%（与 W10 PIT 配置一致）
- [ ] insight-eval v0.2 通过率 ≥ 85%
- [ ] insight-redteam ASR ≤ 10%

### 8.3 部署性
- [ ] `opc-insight` 加入 `RuoYi-Cloud-springboot3/pom.xml` modules
- [ ] Nacos `opc-insight-prod.yml` 配置就绪
- [ ] Helm chart `deploy/helm/opc/templates/service-insight.yaml` + `deployment-insight.yaml` + `values-{dev,staging,prod}.yaml` 三个环境都加
- [ ] docker-compose.yml 加 `opc-insight` 服务

### 8.4 文档
- [ ] [`OPC-M4-VERIFICATION-insight-mvp.md`](../../verification/milestones/m4-insight/OPC-M4-VERIFICATION-insight-mvp.md) 验证报告（与 W* 一致格式）
- [ ] `init.md` 加 M4 章节
- [ ] 前端 4 页 Storybook（如已建立）

---

## 9. 范围外（不做）

明确不在 M4 范围：
- ❌ 多公司切换 / 跨公司对比（仅单公司视图）
- ❌ 自定义 KPI / 自定义规则（仅硬编码 8 条）
- ❌ 移动端原生 App（复用 W5 ResponsiveTable + 移动端 drawer）
- ❌ 邮件 / 短信推送（仅 WebSocket 通知，可选 Day 7 后期）
- ❌ 实时大盘（仅定时 cron + 用户主动访问）
- ❌ 第三方 Agent 接入（M5 才做）
- ❌ M5 开放平台 / M6 移动端 App

---

## 10. 引用 & 关联文档

- `OPC-MVP-DELIVERY.md` §7 — 升级路径占位 (M4-M6)
- `init.md` §3.9 — 数据洞察 (opc-insight) 原始定义
- `OPC-W1-TASK-BREAKDOWN.md` — 8 Task 拆分模板
- `OPC-W1-VERIFICATION-task1-eval-set.md` — 评测集结构参考
- `OPC-W1-VERIFICATION-task3-workflow-cron.md` — workflow cron 集成模板
- `OPC-W1-VERIFICATION-task8-redteam.md` — 红队测试模式
- `OPC-W10-VERIFICATION-all-remaining.md` — updateBy/createBy 审计、PIT 配置参考

---

> 文档版本：v1.0 · 2026-09-08 · brainstorming 流程产出 · 待 user review 后进入 writing-plans 阶段。
