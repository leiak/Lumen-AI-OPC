# 财务数字员工深化设计 —— 让每天那条链读到真数据

> 元信息：设计者 Claude Code / 日期 2026-11-02 / 版本 **v1.3** / 状态 **draft 待 review**
> 关联：
> - 主题来源：`CLAUDE.md` §二「不说假话」；根 README「近期开发」表的延续
> - 调度链路现状：`CLAUDE.md` 坑 #9（`sys_job` → Quartz → `WorkflowCronJob` → Feign → agent-hub）
> - 健康检查：`springboot3/deploy/scripts/health-check.sh` 第 25 节（定时任务）
> - 既有 spec：`2026-09-08-opc-insight-mvp-design.md`（KPI 聚合与日报生成）
> - 实施计划：`docs/superpowers/plans/2026-11-02-finance-agent-deepening.md`
>
> **v1.3 相对 v1.2 的变化（写实施计划时发现的事实错误，不是改主意）**：
> 1. **区间语义改了**（§4.1）：v1.2 写的是「固定近 30 天窗口」。
>    **那是错的** —— 演示库最后一笔流水是 2026-10-21，固定 30 天窗口到了
>    2026-11-21 就空了，26.3 的对账断言会退化成「两个空集相等」——
>    永远绿，等于没有。改成「`fromDate` 缺省 = 该公司最早一条记录，
>    `toDate` 缺省 = 昨天」，并把**实际使用的区间回写进响应**，
>    让健康检查能原样复算。
> 2. **列名 `source` → `report_source`**（§4.4）：`source` 与 MySQL 复制相关关键字撞名。
> 3. **`llm_used` 的取值集明确了**（§4.4）：`PARTIAL` 专指「取数成功、写文案失败」，
>    与 `FALLBACK`（LLM 完全没参与的固定模板）**不是一回事**。
> 4. **验证层补了两条断言 + 一条护栏**（§4.6）：26.5 改成打一个真端点而不是看输出文本；
>    新增 26.9（降级链接上了）、26.10（insight 自己的 LLM 也通了）。
> 5. **工具返回体的字段名**（§4.1）：`source` = 来源表名。
>
> **v1.2 相对 v1.0/v1.1 的变化（都是复核后改的，不是改主意）**：
> 1. 路线改了：用户复核后选了**「引擎 TOOL 节点」**（原方案是「模型自主调工具」）。
>    原因已核实：**模型自主那条链在 HTTP 层今天是断的** —— 没有任何 provider 会解析
>    OpenAI 的 `tool_calls`，回程也不带工具结果（§3.4）。做「引擎 TOOL 节点」不必碰 provider 透传，
>    而且一次运行只要 1 次 LLM 调用（原来 2 次，加工具会变 3 次）—— MiniMax 配额压力小得多。
> 2. 工作流**改挂 company 2**（原方案没注意工作流挂在 company 1，而全部财务数据在 company 2，
>    这会让验收标准变成空断言 —— §3.5）。
> 3. 本轮**给 DeepSeek key**，用户自己写进 `.env`（§4.5）。
> 4. 新增 12 条已核实的事实（工具链、PromptGuard 白名单、种子假数据、时间口径、cron 时区…）。

---

## 0. 执行摘要

**问题**：「每天自动生成财务日报」是整个演示环境里最响的一个功能，而它**每天产出编出来的数字**。

工作流 `finance_daily_report_v1` 的 `dag_json` 是两个纯 LLM 节点。第一个节点的 prompt 是
「请汇总昨日的收支流水、待处理凭证数量与异常项」—— 但引擎把这段话原样丢给模型，
**模型从来没有查过 `opc_finance_bank_flow`**。它在编。

**做什么**：让这条链 **读到真实财务数据 → 产出真实结论 → 落进业务表**。

**核心数字**：

- 引擎层：`WorkflowEngine.runTool()` 现在是 `// TODO: 调 ToolExecutor` + 塞 `"{}"` → 本轮落地
- 工具层：`FinanceTools` 5 个工具**全是硬编码假返回** → 真实现 3 个、下线 3 个
- 数据层：opc-finance 新增 2 个 `@InnerAuth` 工具端点（按日流水、按日凭证）
- 落表层：工作流产出物经 `persist` 声明写入 `opc_insight_daily_report`
- 供给层：第二个 LLM provider（DeepSeek，用户供 key）+ 真重试 + 真超时 + opc-insight 补 key 缺口
- 验证层：健康检查新增第 26 节「业务闭环在发生」；修第 25 节不断言 `status='SUCCESS'` 的漏洞
- 改动面：6 个模块（ai-core / agent-hub / finance / insight / job / deploy）

**一句话验收**：**当昨天确实没有流水时，日报必须如实写「昨日无资金流水」，而不是编一段
「昨日营收 3.8 万元」。** 假链路一定编数字；这条断言通过，才证明链路是真的。

**但光有这一条不够**（v1.2 补）：昨天确实没有流水，所以「日报说无流水」这条断言
**分不出「链路是真的」和「工具完全坏掉」**。所以日报里还必须带一段**自有数据起算的区间**
（当前 10-07 ~ 11-01，6 笔真流水、金额 3582 / 7080 / 9860 / 12150 / 13200 / 38450），
健康检查要能拿这些数字**逐笔对回 `opc_finance_bank_flow`**。§6 两条一起过才算数。

> **区间不是固定「近 30 天」**（v1.3 更正）：固定窗口过了 2026-11-21 就空了，
> 对账断言会退化成「两个空集相等」—— 永远绿，等于没有。
> 见 §4.1。

---

## 1. 为什么（现状的假，逐条已核实）

| # | 现状 | 证据位置 |
|---|------|---------|
| 1 | 工作流 dag 两个纯 LLM 节点，**不查任何表** | `opc_agent_workflow.dag_json`；`WorkflowEngine.runLlm` → `AgentRuntime.run()`，无工具挂载 |
| 2 | `WorkflowEngine.runTool()` 是空壳 | 方法体 = `// TODO: 调 ToolExecutor` + 塞 `"{}"` |
| 3 | `FinanceTools` 5 个工具全是硬编码假返回 | `queryDailyReport` 写死 `{"date":"2026-09-03","revenue":0,"expense":0}`；`createVoucher` 返回 `V-PLACEHOLDER`；`extractVoucher` 注释说走 Feign 但没实现 |
| 4 | `InsightDailyReportJob`（真·日报生成器）**从没被调用过** | 注释要求 `RemoteInsightService`（**不存在**），`sys_job` 无对应条目 |
| 5 | 唯一在跑的链最近 **4** 次 CRON 全 FAILED | `opc_agent_workflow_run` id 35/36/37 = HTTP 429「已达 Token Plan 用量上限 (2056)」；id 38（今天）= `request timed out` |
| 6 | 调度器记「成功」，业务记「失败」 | `sys_job_log` **5 条**全 `status=0`；对应 `opc_agent_workflow_run` 4 条 `FAILED` |
| 7 | 健康检查第 25 节看不出 5、6 | 25.5 只断言「有 `trigger_source LIKE 'quartz:%'` 的记录落库」，**不断言 `status='SUCCESS'`**；25.6 判的正是那个「失败也报成功」的 `sys_job_log.status` |
| 8 | **opc-insight 的 LLM 功能 100% 是假的** | `opc_insight_daily_report` 7 行 `llm_used` 全 `FALLBACK`；`opc_insight_advice` 5 行全 `FALLBACK`。根因：compose **没给 `aiopc-insight` 任何 LLM key**（§3.3） |
| 9 | **`max-retries: 3` 是声明了没接线的配置** | 该键只出现在 7 个 yml 里，**全仓没有一行 Java 读它**；`LlmGateway.chat` 一次失败直接抛 `OpcException` —— 重试数是 0 |
| 10 | **单次 LLM 调用 30 秒超时，且没有任何重试** | `AgentRuntime.DEFAULT_STEP_TIMEOUT_SECONDS = 30` → JDK `HttpRequest.timeout` → `HttpTimeoutException("request timed out")`。id 38 就是被这个掐断的 |
| 11 | **「模型自主调工具」这条链在 HTTP 层是断的** | `HttpLlmClient.parseChatResponse()` 只读 `message.content`，**无视 `message.tool_calls`**；`callChatCompletions()` 回程只写 `role`+`content`，assistant 的 `tool_calls` 和 `role=tool` 的结果**根本不会发回 provider**。`ChatResponse.toolCalls` 全仓无人填充 |
| 12 | **PromptGuard 工具白名单是写死的 11 个名字** | `PromptGuard.ALLOWED_TOOLS` 不含新工具；`AgentRuntime` 捕获 403 后把「工具未授权」当**工具结果回喂给模型**，模型会**照旧编数字** —— 正是本项目最想消灭的失败模式 |
| 13 | **种子里的 7 行日报本身就是编的数字** | `sql/seed/opc_demo_ecommerce_seed.sql` 种的金额是 9800/19600/6200/4200/3100/**3580**/890/300，真实流水是 13200/38450/12150/9860/7080/**3582** —— **3580 ≠ 3582，一条都对不上** |
| 14 | **「每天 09:00」是北京时间的假话** | 容器时区是 **UTC**（`docker exec aiopc-agent-hub date` → `UTC`），cron `0 0 9 * * ?` → **北京 17:00** 才跑。`opc_agent_workflow_run.start_time` 记的是 **CST**（JDBC `serverTimezone=Asia/Shanghai`），所以那几条记录写着 17:00 |

### 为什么这不只是「一个功能没做好」

`CLAUDE.md` §二列的坑里有一条：「记忆系统『能记事』—— 实际 `embed()` 返回 `Math.random()`」。
`FinanceTools` 是同一类：注册了、规格有了、名字取得像样、**返回的是编的**。
比「没有这个功能」更危险 —— 因为接线一旦通了，模型会拿着 `revenue:0` 和
`V-PLACEHOLDER` 当真数据用，而且**看起来完全正常**。

第 8 条更严重一档：opc-insight 界面上是「AI 数据洞察」，实际 12 条产出**一条都不是 AI 生成的**，
全是固定模板兜底（`summary_md` 以 `[自动聚合·未走 LLM]` 开头，`advice_md` 是那句写死的
「请检查数据完整性后重试，或联系平台支持。」）。**用户看不出来，因为界面不标。**

---

## 2. 目标 / 非目标 / 反目标

### 目标

1. **G1** 工作流的 TOOL 节点真的调工具，把真实数据取回来。
2. **G2** 工具返回的全部来自 `opc_finance_bank_flow` / `opc_finance_voucher` / `opc_wallet` 的真实行。
3. **G3** 假工具下线 —— 注册表里不再有任何返回硬编码假值的工具。
4. **G4** 每天跑出来的日报落进 `opc_insight_daily_report`（业务表），不是只躺在 `opc_agent_workflow_run`。
5. **G5** 日报里的每个数字都能在业务表里找到对应行。
6. **G6** LLM 挂掉时降级到「基于真实表的聚合数字」，**绝不编段落**。
7. **G7** 上述每一条都有健康检查断言（新增第 26 节），且第 25 节的漏洞被堵上。
8. **G8** opc-insight 自己那 12 条 `FALLBACK` 要么变成真的，要么在界面上明确标出来。
9. **G9** 种子里的假日报清掉 —— 新环境部署出来不能带编的数字。

### 非目标（本轮明确不做）

- **不做数据加厚** —— 财务数据维持现状（6 条流水 / 5 张凭证 / 1 份税报）。用户已定。
  后果见 §7「数据薄的必然结果」。
- **不做「雇佣 → 计费」交易闭环** —— 那是另一个方向（商业闭环），本轮不碰。
- **不实现 `create_voucher` / `extract_voucher`** —— 写操作要带审核状态机，超出「读真数据」的范围。
  假工具**下线**而不是留着。
- **不补 HTTP 层的 `tool_calls` 透传** —— 用户已定走「引擎 TOOL 节点」。这是下一轮的事，
  本轮**不假装它能用**（§4.2 明确写清）。
- **不改 `opc_prompt_template` / `opc_agent_eval_*`** —— 那是评测体系的方向。
- **不接真实支付渠道** —— 演示环境用钱包扣款即可。
- **不补 `QRTZ_*` 表** —— RAMJobStore 是预期状态。

### 反目标（做到这些也算失败）

- ❌ 日报里出现任何**无法在业务表里查到的数字**。
- ❌ 「昨天的流水是 0 元」被写成「昨日营收 0 元，运营平稳」这种带判断的段落。
- ❌ 用「已接入 / 已支持 / 已打通」描述任何**没有断言证明**的能力。
- ❌ 让「工具查表」变成「模型编表」—— 工具返回必须可被断言核对。
- ❌ 修好了工作流那条链，却让界面上的 `FALLBACK` 报告继续装作 AI 产出。
- ❌ 留一个「模型可以自己调工具」的开关，但那条路其实不通。

---

## 3. 已核实的事实（设计依据）

### 3.1 依赖图（决定工具实现放哪）

```
opc-ai-core  ← opc-finance ← opc-insight
     ↑              ↑            ↑
opc-agent-hub    (依赖 ai-core)  (依赖 ai-core + finance + billing + user-center)
```

- **`opc-finance` 依赖 `opc-ai-core`** → `opc-ai-core` **不能**依赖 `opc-finance`（Maven 成环）。
  → 工具调 finance 的数据要走 Feign，且 **只能用 `R<Map<String,Object>>` 泛型**，
  不能引 `com.ruoyi.opc.finance.*` 的类。
- `ServiceNameConstants.FINANCE_SERVICE = "opc-finance"` 已存在；
  **没有 `INSIGHT_SERVICE`**（要新增）。
- **四个进程都会扫到 `com.ruoyi.opc.ai`**（`FinanceTools` 现在住在那里）：

| 进程 | 扫描范围 | 装 FinanceTools 会怎样 |
|---|---|---|
| `opc-ai-core` | `@ComponentScan({"com.ruoyi.opc","com.ruoyi.ai"})` | 也会去 Feign 调 opc-finance → **运行时服务环** |
| `opc-agent-hub` | `@ComponentScan({"com.ruoyi.opc","com.ruoyi.agent"})` | 正常（就是要它） |
| `opc-finance` | `@ComponentScan({"com.ruoyi.opc","com.ruoyi.finance"})` | **自己 Feign 调自己** |
| `opc-insight` | `scanBasePackages = {"com.ruoyi.opc.insight","com.ruoyi.opc.ai"}` + `@EnableFeignClients(clients={显式三个})` + ComponentScan **排除 `@FeignClient` 注解** | 新 Feign client 注册不进来 → **启动直接失败** |

→ **决定 D4：工具实现搬到 `opc-agent-hub` 模块**（`com.ruoyi.opc.agent.tools` + `com.ruoyi.opc.agent.feign`）。
opc-agent-hub 的 pom 里**没有** opc-finance/opc-insight 的依赖 → 这三个进程根本没这些类，
扫不到，也就不会启动失败、不会自调用。ai-core 只保留**通用机制**（`ToolRegistry` / `ToolExecutor`）。

### 3.2 已存在、可以直接用的零件

| 零件 | 位置 | 说明 |
|---|---|---|
| `ToolRegistry` / `ToolExecutor` / `ToolSpec` | `opc-ai-core/.../ai/tools/` | 工具体系完整，缺的是真工具 + 引擎接线 |
| `ToolSpec.toOpenAiFormat()` | `ToolSpec.java` | 已经能转成 OpenAI function 格式（**本轮用不上**，留给下一轮） |
| `ToolExecutor.execute()` | `ToolExecutor.java` | 已经在往 context 里塞 `companyId` / `userId` / `instanceId` / `taskId` ✓ |
| `RemoteWorkflowService`（ruoyi-job → agent-hub） | `ruoyi-job/api/` | 已验证的第 25 节链路 |
| opc-finance 的 4 个 `@InnerAuth` 聚合端点 | `/opc/finance/agg/*` | `voucher` / `flow` / `tax-report` / `token-usage`，**按月**聚合 |
| opc-billing 的 `@InnerAuth /agg/wallet` | `/opc/billing/agg/wallet` | 钱包余额 |
| `DailyReportService.generate()` | `opc-insight` | 查 KPI → 调 LLM → upsert `opc_insight_daily_report` → 失败写 `opc_insight_anomaly`。**已完整**，只是没人调度 |
| `InsightDailyReportJob` | `opc-insight/.../insight/workflow/` | 多公司版日报生成器，**未被调度** |
| `FeignRequestInterceptor` | `ruoyi-common` | 无条件注入 `from-source: inner`，`@InnerAuth` 端点只对服务间可达 |
| 工作流触发端点 | `OpcWorkflowTriggerController` | `@InnerAuth POST /opc/agent/workflows/{code}/trigger?triggerSource=`；**返回体已经带 `status` / `errorMessage`**（Q9：job 侧只是没用） |

### 3.3 供给层的真实状态（全部已核实）

**谁有 LLM key**（`deploy/docker-compose.yml`）：

| 容器 | `MINIMAX_API_KEY` | `DEEPSEEK_API_KEY` | 结论 |
|---|---|---|---|
| `aiopc-agent-hub` | ✅ | ✅（但 `enabled: false`） | 能调 LLM |
| `aiopc-ai-core` | ✅ | ✅（但 `enabled: false`） | 能调 LLM |
| `aiopc-insight` | ❌ | ❌ | **provider 全部 disabled → LLM 必然失败** |
| `aiopc-finance` | ❌ | ❌ | 同上（finance 目前不自己调 LLM） |

**为什么 insight 必然失败**：它的 `application.yml` 里没有 `opc.llm` 段，
`@Value("${opc.llm.minimax.api-key:}")` 取默认空串 → `MiniMaxProvider.enabled()`
= `enabled && apiKey非空` = **false** → `LlmGateway` 一个 provider 都遍历不到 →
抛「所有 LLM provider 失败」→ `DailyReportServiceImpl` catch → 写 `FALLBACK`。
**这就是 7/7 日报 + 5/5 建议全是 FALLBACK 的机制，不是巧合。**

**配置来源**：Nacos 的 `application-dev.yml` 里有完整的 `opc.llm` 段（insight 也 import 了它），
但它的 api-key 是占位符 `${MINIMAX_API_KEY:}` / `${DEEPSEEK_API_KEY:}` ——
**insight 容器里没有这两个环境变量 → 占位符解析成空串 → 还是 disabled**。
→ 缺的不是配置，是**环境变量**。

**provider 的启用规则**：

```java
@Value("${opc.llm.minimax.api-key:}")   private String apiKey;
@Value("${opc.llm.minimax.enabled:true}") private boolean enabled;
@Override public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }
```

- MiniMax `priority=5`、DeepSeek `priority=10` → `LlmGateway` 按 priority 升序遍历，**先 MiniMax 后 DeepSeek**。
- **降级链是现成的**，但今天**没有备胎**：`.env` 里 `DEEPSEEK_API_KEY` 未配置，
  且 `opc.llm.deepseek.enabled: false` 写在**三个地方**：
  `opc-ai-core/application.yml:96`、`opc-agent-hub/application.yml:96`、`deploy/nacos/application-dev.yml:48`。
  → **「填上 key 即自动启用」这句话是错的**（v1.0 写错过，v1.2 更正）。
- compose 里 `DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-sk-placeholder}` —— 填了 `enabled: true`
  但 env 还是占位符的话，DeepSeek 会拿着假 key 每次 401。**要一起改。**

**失败模式**（`opc_agent_workflow_run` 最近 4 条 CRON）：

| id | 本地时间(CST) | 错误 | 说明 |
|---|---|---|---|
| 35 | 10-30 | HTTP 429「已达 Token Plan 用量上限 (2056)」 | MiniMax 日配额耗尽 |
| 36 | 10-31 | 同上 | 同上 |
| 37 | 11-01 | 同上 | 同上 |
| 38 | 11-02 | `request timed out` | n1 成功（13.4s，903 tokens），n2 被 **30 秒**超时掐断 |

**id 38 的时间账**（`opc_agent_token_usage` id 232 = `11-02 09:00:14` **UTC** = 17:00 CST，
latency 13366ms；run 总耗时 44694ms）：n1 13.4s 成功 → n2 再跑 30s 超时 → 44.7s 整条链失败。
**一次 429 / 超时 = 整条链报废**（§1 第 9 条：零重试）。

**计量表只记成功**：`opc_agent_token_usage` 229 行，`success=1` 229 行，**失败 0 行**
（`LlmGateway` 失败分支 `continue`，根本不调 `tokenMeter.record`）。
且缓存命中也记 `success=1`（`request_id` 前缀 `cache-`、`cost=0`）。
→ **「过去 24h 有 `success=1` 的 token_usage」这条断言今天就是绿的**（id 232 就在今天），
而今天的运行是 FAILED。**这条断言必须换掉**（§4.6）。

### 3.4 工具调用链的真实状态（为什么改路线）

| 层 | 状态 |
|---|---|
| `AgentRuntime` 的 tool_calls 循环 | **在**（`AgentRuntime.java:92-126`）：识别 tool_calls → 执行工具 → 把结果作为 `role=tool` 消息塞回 `messages` → 下一轮 |
| `ChatResponse.toolCalls` / `ChatMessage.ToolCall` 字段 | **在**（`ChatResponse.java:24`、`ChatMessage.java:38`） |
| `ChatOptions.tools` → HTTP body | **在**（`HttpLlmClient.java:64`：`body.set("tools", ...)`） |
| provider 解析 `message.tool_calls` | **没有** —— `parseChatResponse()` 只读 `message.content`（`HttpLlmClient.java:172-195`） |
| 回程带 assistant 的 `tool_calls` + `role=tool` 消息 | **没有** —— `callChatCompletions()` 只写 `role` + `content`（`:55-60`） |
| 工具调用的单元测试 | **一条都没有** |

→ 结论：**「模型自主调工具」这条路，底下是空的。** 本轮按用户决定走**引擎 TOOL 节点**，
不去补这一层（下一轮）。**但也不能留一个「模型可以自己调工具」的假开关**（反目标最后一条）。

### 3.5 归属与日期（两个会让验收变空断言的坑）

**(1) 工作流挂在 company 1，数据全在 company 2**（DB 实测）：

| 表 | company_id=1 | company_id=2 |
|---|---|---|
| `opc_agent_workflow`（`finance_daily_report_v1`） | **1（工作流在这里）** | — |
| `opc_finance_bank_flow` | 0 | **6** |
| `opc_finance_voucher` | 0 | **5** |
| `opc_insight_daily_report` | 0 | **7** |
| `opc_wallet` 余额 | **0.00** | **2860.00** |

工具的 companyId 来自 `WorkflowTriggerServiceImpl`（`def.setCompanyId(workflow.getCompanyId())`）
→ **恒为 1** → 工具查 company 1 → **永远 0 行** → 日报永远写「昨日无资金流水」，
**工具完全坏掉时也一样成立** → 验收标准成了空断言。

→ **决定 D1（用户已定）：工作流改挂 company 2。**（`UPDATE opc_agent_workflow SET company_id=2`）

**成员关系**（`opc_company_member`）：user 1（admin）是 company 1 的 OWNER；
user 101 是 company 2 的 OWNER、同时是 company 1 的 STAFF。演示时切公司即可看到 company 2 的日报。

**(2) `${yesterday}` 没有任何来源**：`WorkflowEngine.renderTemplate()` 只替换 `inputParams + outputs`，
而触发时传的是 `Collections.emptyMap()`（`WorkflowTriggerServiceImpl:89`）→ `${yesterday}` 会**原样发给模型**。

→ **决定 D6：引擎注入内置日期变量** `today` / `yesterday` / `weekAgo` / `monthAgo`（§4.3）。

### 3.6 「日报」这张表有三个写入方（v1.2 补）

`opc_insight_daily_report` 有 `uk_company_period (company_id, period)`，写入方：

| 写入方 | 入口 | 产出质量 |
|---|---|---|
| insight 自己的 `DailyReportService.generate()` | **`POST /opc/insight/daily/generate?date=`（公开接口，UI 上有按钮）** | LLM 永远失败 → `FALLBACK` 固定模板 |
| 新增：agent-hub 工作流 `persist` | `@InnerAuth POST /opc/insight/daily/upsert` | 真实数据 + 真 LLM |
| 种子 | `sql/seed/opc_demo_ecommerce_seed.sql` | **编的数字**（§1 第 13 条） |

手工点一次「生成日报」就会把数字员工产出的日报覆盖成 FALLBACK 版；
新环境部署还会重新长出 7 行编的日报。→ §4.4 的 `report_source` 列 + §4.3 的删除种子。

### 3.7 时间口径（三个不同的时钟，别互相推算）

| 字段 | 谁写的 | 口径 |
|---|---|---|
| `opc_agent_workflow_run.start_time` | 应用（JDBC `serverTimezone=Asia/Shanghai`） | **CST（北京）** |
| `opc_agent_token_usage.create_time` | MySQL `CURRENT_TIMESTAMP`（服务端 UTC） | **UTC** |
| 容器 JVM / Quartz | 容器默认 | **UTC** |

→ `run.start_time` 与 `token_usage.create_time` **差 16 小时**（CST 与 UTC 各偏 8 小时，方向相反）。
**不要拿它们互相推算「是不是今天」**，统一用 `NOW()` 对比同一列。
→ 并且：`0 0 9 * * ?` 是 **UTC 的 9 点 = 北京 17 点**（§1 第 14 条）→ §4.7 修 cron。

---

## 4. 设计

### 4.1 工具层：数字员工能查到真数据

**opc-finance 新增 2 个 `@InnerAuth` 工具端点**（返回 `R<Map<String,Object>>`，
与既有 agg 端点同一套 `@ExceptionHandler` 兜底，保证 Feign 侧永远拿到 `R<T>`）：

```
GET /opc/finance/tools/flows?companyId=&fromDate=&toDate=
  → { count, totalIn, totalOut, net, items: [ {tradeTime, direction, amount, counterParty, memo, status} ] }

GET /opc/finance/tools/vouchers?companyId=&fromDate=&toDate=
  → { count, totalIn, totalOut, items: [ {voucherDate, summary, status, totalDebit, totalCredit} ] }
```

- `fromDate` / `toDate` 缺省语义（**v1.3 修正**）：
  - `toDate` 缺省 = **昨天**（北京时间；今天的数据不进日报）
  - `fromDate` 缺省 = **该公司最早一条记录的日期**（不是「30 天前」）
  - 区间是闭区间 `[fromDate 00:00:00, toDate 23:59:59]`
  - 响应里**回写实际使用的 `fromDate` / `toDate`**
  - **为什么不用固定「近 30 天」**：演示库最后一笔流水是 2026-10-21，
    固定 30 天窗口过了 2026-11-21 就空了 → 26.3 的对账断言退化成
    「两个空集相等」→ **永远绿，等于没有**。改成「自有数据起算 ~ 昨天」，
    并回写区间让健康检查能原样复算，断言永远有东西可对。
- `companyId` **必填**，由 `ToolExecutor` 注入的 context 取（`req.getCompanyId()`），
  **不接受调用方传入的 companyId** → 跨租户风险在工具层就被堵死。
- 汇总口径与既有 `FlowAggVo` 一致（IN = 收入，OUT = 支出）。
- **凭证没有 amount 列**（`opc_finance_voucher` 只有 `totalDebit` / `totalCredit`）→
  工具层**换算成同一套 in/out 口径**（借方=支出、贷方=收入），并把原始两列一并返回。
  否则同一个「支出」在流水表和凭证表里是反的，§4.6 的数字核对会全错。
- 返回体带 **`source`（来源表名）** + `companyId` + `fromDate` / `toDate` +
  `count` + 汇总 + `items[]`（每一笔的金额）——
  「数字从哪来、是哪段区间、每一笔是多少」三样都可核对。

**opc-agent-hub 新增 `FinanceToolClient` / `BillingToolClient`**
（`@FeignClient(name = ServiceNameConstants.FINANCE_SERVICE)` 等），
返回类型一律 `R<Map<String,Object>>` —— **不引 finance 的类**（§3.1 的成环约束）。

**`FinanceTools` 改真实现**（模块从 ai-core 搬到 agent-hub，见 D4）：

| 工具 | 实现 |
|---|---|
| `query_bank_flow` | → `FinanceToolClient.flows(companyId, from, to)` |
| `query_voucher` | → `FinanceToolClient.vouchers(companyId, from, to)` |
| `query_balance` | → `BillingToolClient.wallet(companyId)` |

工具返回体统一带 `source` 字段（表名）+ `companyId` + `count` + `fromDate` / `toDate`，
让「数字从哪来、是哪段区间」可核对。

**下线 3 个假工具**（§3.4 的表 + `ToolSpec.specs()`）。

> 「下线」= 从 `ToolRegistry` 移除 + 从 spec 列表移除 + 在源码里留注释
> 写清「曾注册但返回硬编码假值，已下线，何时回来」。**假工具比没工具危险** ——
> 它返回的东西看起来像真数据。

**`ToolRegistry` 增存 spec**（决定 D5）：现在它只存 `Map<String, ToolHandler>`，
**spec 在 `FinanceTools.specs()` 这个 static 方法里，而且只覆盖 3 个工具**
（`query_voucher` / `query_balance` 压根没有 spec）。
→ 改成 `register(name, spec, handler)`，注册表同时是**规格的唯一真相源**。
本轮 TOOL 节点不依赖 spec，但下一轮「模型自主」要用 —— 现在就把接口留好。

### 4.2 引擎层：TOOL 节点真的调工具（本轮的核心）

**`WorkflowEngine.runTool()` 现在是空壳**：

```java
private void runTool(JsonNode nodeDef, WorkflowContext ctx) {
    // TODO: 调 ToolExecutor
    ctx.outputs.put(nodeDef.path("id").asText(), "{}");
}
```

**改成**：

```java
// dag 里：
// {"id":"n1","type":"TOOL","tools":["query_bank_flow","query_voucher"],
//  "args":{"fromDate":"${monthAgo}","toDate":"${yesterday}"}}
for (String toolName : nodeDef.path("tools").findValuesAsText("") ...) {
    String argsJson = renderTemplate(nodeDef.path("args").toString(), vars);
    String result = toolExecutor.execute(toolName, argsJson, request);  // companyId 从 ctx 来
    ctx.outputs.put(nodeDef.path("id").asText() + "." + toolName, result);
}
```

- **`companyId` 只能来自 `ctx.companyId`**（= 工作流所属公司 = company 2，§3.5），
  调用方传的 companyId 一律忽略。
- 工具返回**原样 JSON 字符串**存进 `ctx.outputs` → 落到 `opc_agent_workflow_run.output_result`
  → **健康检查可以拿它逐笔对表**（§4.6 的 26.3）。
- 工具抛异常 → 节点失败 → 整条链 FAILED（**不吞**）。但工具返回「0 行」是成功。

**`finance_daily_report_v1` 的 dag 改成**（3 个节点 + persist）：

```json
{
  "nodes": [
    {"id":"n1","type":"TOOL",
     "tools":["query_bank_flow","query_voucher"],
     "args":{},
     "label":"自有流水与凭证"},
    {"id":"n2","type":"LLM",
     "prompt":"你是企业财务助理。下面是系统从业务表里查出来的真实数据（JSON）。请据此写一份面向管理者的财务日报，包含【关键数字】【风险提示】【今日建议】三个小节。\n\n"
            + "硬性要求：1) 所有数字必须直接引用给定数据里的值，不得推算或补充；"
            + "2) 如果数据里昨日（${yesterday}）没有流水，必须明确写「昨日无资金流水」，不得写成「营收 0 元，运营平稳」；"
            + "3) 不得出现给定数据里没有的数字。\n\n"
            + "真实数据：\n${n1}",
     "stepTimeoutSeconds": 60}
  ],
  "edges":[{"source":"n1","target":"n2"}],
  "persist": {"target":"daily_report", "date":"${yesterday}",
              "kpiFrom":"n1", "summaryFrom":"n2"}
}
```

> **`args` 是空的（v1.3）**：区间由工具自己算（缺省 = 该公司最早一条记录 ~ 昨天），
> 并把实际区间回写进工具返回。dag 里写死 `${monthAgo}` 反而会踩坑 ——
> 固定 30 天窗口过了 2026-11-21 就空了，对账断言会空转（§4.1）。

**为什么工具返回里要同时有「区间」和「每一笔」**：
- **昨日**（11-01）确实 0 行 → 是「如实说无流水」的试纸，但**分不出真假**（§0）。
- **区间全量**（10-07 ~ 11-01）有 **6 笔真流水**（3582 / 7080 / 9860 / 12150 / 13200 / 38450）
  和 5 张凭证 → **每天都有非空的、可逐笔对表的数字**。
  → 26.3 断言「工具结果里的金额集合 == DB 里同区间同公司的金额集合」**每天都成立且非空**。
- 「日报带一段区间上下文」本身也是正常财务日报该有的东西，不是为了测试而加的。

**LLM 调用次数**：本轮 **1 次**（n2）。原来 2 次（两个 LLM 节点），
「模型自主」方案会是 3 次（调工具 + 收口 + 写日报）。**MiniMax 配额压力因此小得多。**

**本轮明确不做**（也不留假开关）：
- 不把工具挂到 LLM 节点的 `tools` 参数上 —— 底层 HTTP 层是断的（§3.4），挂上去只是摆设。
- 不实现 `ToolCatalog`（OpenAI function schema）—— 本轮没有消费者；spec 已经存进注册表，
  下一轮直接可用。

### 4.3 变量层：内置日期变量

**决定 D6**：`WorkflowEngine` 在 `renderTemplate` 之前，往 `vars` 里注入四个内置变量：

| 变量 | 值 |
|---|---|
| `today` | 今天（本地日期） |
| `yesterday` | 昨天 |
| `weekAgo` | 7 天前 |
| `monthAgo` | 30 天前 |

- 日期口径统一用**本地日期**（CST），与 §3.7 的口径对齐；`run.start_time` 也是 CST。
- 注入的是**变量表**，不是新参数位 —— 触发端点签名不变，ruoyi-job 不用改。
- 同时注入 `companyId`（只读）→ dag 里能引用，但不能覆盖工具层的归属。

### 4.4 落表层：产出物进业务表

工作流跑完，结果在 `WorkflowResult.outputs`。要让它进 `opc_insight_daily_report`。

**决定：工作流 dag 支持可选的 `persist` 声明，由引擎在跑完后确定性执行。**

```json
"persist": {"target":"daily_report", "date":"${yesterday}", "kpiFrom":"n1", "summaryFrom":"n2"}
```

- `target=daily_report` → agent-hub 调 **insight 的 `@InnerAuth POST /opc/insight/daily/upsert`**。
- **不让模型决定要不要落表** —— 模型只负责产出内容，落表是确定性动作。
  （分工：取数靠引擎确定性执行，写产出物靠引擎确定性执行，**只有措辞靠模型**。）

**insight 新增**：

```
POST /opc/insight/daily/upsert   (@InnerAuth)
  body: { companyId, period, summaryMd, adviceMd, kpiJson, llmUsed, reportStatus,
          reportSource, sourceWorkflowRunId }
  → upsert opc_insight_daily_report (uk_company_period)
```

- **必须有 `R<T>` 错误契约**：opc-finance / opc-billing 的 agg 端点都配了 controller 级
  `@ExceptionHandler(OpcException.class)` 保证响应体永远是 `R<T>`；
  insight 的 `DailyReportController` **没有** → 抛异常时全局 advice 返回 `AjaxResult`，
  agent-hub 侧声明的 `R<Map<String,Object>>` 反序列化会失败。
  → **新增独立的 `InsightInternalController`**（只放 `@InnerAuth` 端点），带同款 `@ExceptionHandler`。
  （不能直接加在 `DailyReportController` 上：它其它端点用 `SecurityUtils.getCompanyId()`，
  而 InnerAuth 调用**没有登录态**，companyId 只能从 body 取。）
- `ServiceNameConstants` 要补 `INSIGHT_SERVICE = "opc-insight"`。

**`opc_insight_daily_report` 加 3 列**（迁移脚本 ALTER）：

| 列 | 取值 | 为什么 |
|---|---|---|
| `report_status` | `FULL` / `PARTIAL` / `FALLBACK` | 区分「全做成了」「做了一半」「LLM 全挂」 |
| `report_source` | `AGENT_WORKFLOW` / `MANUAL` | 这张表有三个写入方（§3.6），必须能分清谁写的 |
| `source_workflow_run_id` | 工作流运行记录 id | 能从日报反查是哪次运行产出的，闭环可断言（26.2） |

> **列名是 `report_source` 不是 `source`**（v1.3）：`source` 与 MySQL 复制相关关键字撞名，
> 迁移脚本里到处要加反引号，不如一开始就避开。
> **`llm_used` 的取值集**（v1.3 明确）：`PARTIAL` = 「取数成功、写文案失败」
> （真实数据落表、摘要用确定性聚合）；`FALLBACK` = 「LLM 完全没参与，输出固定模板」。
> **这两个不是一回事** —— 26.2 判的是 `llm_used != 'FALLBACK'`，
> 所以 PARTIAL 的行也算「LLM 参与过一半」，不误报成假数据。

**`kpi_json` 是结构化的数字来源**（决定 D-数字口径）：
persist 时引擎直接把 **n1 的工具结果**（count / totalIn / totalOut / items 的金额集合）
写进 `kpi_json` —— **不是从 markdown 里抠数字**。
`summary_md` 是 n2 的散文（给人看），`kpi_json` 是真数字（给机器对账）。
→ 26.3 的断言落在 `kpi_json` 上，**稳定、可实现、不会误报**。

**PARTIAL 态为什么必须有**：id 38 那次，**n1 成功拿到了真实数据，n2 超时**。
现有逻辑下整条链判 FAILED → **n1 拿到的真实数据被一起丢掉**。
加 PARTIAL 后：n1 成功即落表，`report_status='PARTIAL'`，`summary_md` 用引擎侧确定性聚合
（而不是丢掉），`llm_used='PARTIAL'`。
**真实数据不因为写文案失败而丢失**，同时不假装它完成时了。

**写入冲突规则**：手工 `POST /opc/insight/daily/generate` 遇到同 `(company_id, period)`
已经是 `report_source='AGENT_WORKFLOW'` 的行 → **拒绝覆盖**，返回明确提示
「该日期日报已由数字员工产出，不支持手工覆盖」。反之，数字员工的 upsert 覆盖手工行是允许的。

**命名冲突要处理**：agent-hub 的工作流产出也会被叫「日报」。两个都叫日报 = 又一次
「说不清谁是谁」（本项目已经吃过一次：两个市场页都叫「智能体市场」）。

> **决定**：界面上区分开 —— insight 的叫**「财务日报」**（既有菜单 `财务日报`），
> agent-hub 工作流运行记录的叫**「数字员工运行记录」**（既有菜单 `我的实例` / 运行记录）。
> 两者不是同一个东西：前者是系统报表，后者是某个数字员工干完活的记录。

### 4.5 供给层（LLM）

**问题不是「要不要接第二个 provider」，是「没有第二个 provider，这条链每天必然红」**：
MiniMax 的 Token Plan 日配额在 10-30 / 10-31 / 11-01 连续三天于 09:00(UTC) 被 429 打断。

**决定 D3（用户已定）：本轮给 DeepSeek 的 key。** 用户自己写进 `springboot3/deploy/.env`：

```
DEEPSEEK_API_KEY=sk-.....        # 不要发在对话里
```

**四条改动**：

**(1) 打开降级链（三处都改，别赌优先级）**：
`opc-ai-core/application.yml`、`opc-agent-hub/application.yml`、
`deploy/nacos/application-dev.yml` 里的 `opc.llm.deepseek.enabled`：

```yaml
enabled: ${OPC_LLM_DEEPSEEK_ENABLED:false}   # 原来是写死的 false
```

仓库的规矩是「**随 jar 走的本地 application.yml 是真相源，Nacos 那份是可运维副本**」
（见 `deploy/nacos/application-dev.yml` 的文件头），两份必须**逐条一致**。
用占位符而不是写死 `true`：这样开关在 compose 的环境变量里，
**改开关 = 改 compose + 重启容器，不用重建镜像**。

**(2) compose 补 key + 去掉假 key**：
`aiopc-agent-hub` / `aiopc-ai-core` 现在的 `DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-sk-placeholder}`
→ 改成 `${DEEPSEEK_API_KEY:-}`（**空值**：key 没配就是 disabled，而不是拿 `sk-placeholder` 去撞 401）。
`aiopc-insight` **新增** `DEEPSEEK_API_KEY`（它现在一个 key 都没有 → LLM 100% 降级）。
三个服务都加 `OPC_LLM_DEEPSEEK_ENABLED: "true"`。

**(3) 给 opc-insight 补 `opc.llm` 段**（本地 `application.yml`）：
它现在完全没有这个段（Nacos 那份够不到它）。加上，与 Nacos 那份**逐条一致**，
api-key 用显式占位符 `${MINIMAX_API_KEY:}` / `${DEEPSEEK_API_KEY:}`。

**(4) 补上没接线的两件事**：
- **重试**：`opc.llm.max-retries: 3` 现在是死的。实现 `LlmGateway` 的重试
  （同 provider 重试 N 次再换下一个 provider）。**429 必须不重试**（重试同样会 429，只是白等），
  只对超时 / 5xx / 网络错重试。
- **超时**：dag 节点支持 `"stepTimeoutSeconds"`（默认 30），日报工作流写 60。

**探针**：
- 健康检查断言「过去 24h 有 `opc_agent_token_usage.success=1`」—— **这条原写法今天就是绿的**
  （id 232 就在今天 09:00 UTC，而运行是 FAILED），**必须换掉**（§4.6）。
- **余额耗尽要能被看见**：429 现在只出现在 `opc_agent_workflow_run.error_message` 里。
  新的断言要能从那里读出 provider 名和错误摘要。

### 4.6 验证层：健康检查第 26 节

**新增第 26 节「业务闭环在发生」**（**10 条断言**，全部可复跑）：

| 断言 | 内容 |
|---|---|
| 26.1 | `opc_agent_workflow_run` 里存在 `trigger_source LIKE 'quartz:%'` 且 **`status='SUCCESS'`** 的记录（≤48h 内） |
| 26.2 | `opc_insight_daily_report` 有对应日期的记录，`report_status='FULL'`、`report_source='AGENT_WORKFLOW'`、`llm_used != 'FALLBACK'`，且 `source_workflow_run_id` 指向一条 SUCCESS 的运行 |
| 26.3 | **日报 `kpi_json.query_bank_flow.items[]` 里的金额集合 == `opc_finance_bank_flow` 里同公司同区间的金额集合**（逐笔相等，条数相等）—— 这一条最强：区间内有 6 笔真流水，非空、可对表 |
| 26.4 | 工具结果里的 `companyId` == 工作流的 `company_id`（跨租户护栏），且 == 2 |
| 26.5 | `GET /opc/agent/tools`（agent-hub 新增的只读端点）返回的工具名里**没有** `query_daily_report` / `create_voucher` / `extract_voucher`，且**有** `query_bank_flow` / `query_voucher` / `query_balance` —— v1.3 更正：原来打算「看输出文本里有没有假工具名」，那证明不了注册表的状态 |
| 26.6 | **最近一次 quartz 触发的 run 是 `SUCCESS`**（替代 v1.0 的「有 success=1 的 token_usage」）—— 附注：token_usage 只记成功、缓存命中也记 success=1，它证明不了「这次跑成了」 |
| 26.7 | `opc_insight_daily_report` 最近一条**不是 `FALLBACK`** |
| 26.8 | `opc_insight_daily_report` 里 `report_source='MANUAL'` 的行**不得**覆盖 `report_source='AGENT_WORKFLOW'` 的同 (company, period) 行（写入冲突护栏） |
| 26.9 | **降级链接上了**：`GET /opc/llm/models`（只返回**已启用**的 provider）≥ 2 个，且其中有 DeepSeek —— v1.3 新增（对应 §6 验收第 7 条） |
| 26.10 | **insight 自己的 LLM 也通了**：`opc_insight_advice` 最近一条 `llm_used != 'FALLBACK'` —— v1.3 新增（对应 §6 验收第 8 条） |

> **26.9 证明的是「降级链接上了」，不是「降级真的发生过」**：
> 真发生过 = MiniMax 429 后落到了 DeepSeek，那要等一次真实的 429。
> **不要写成「断言 token_usage 里必须有 DeepSeek 的行」** —— MiniMax 正常时不重试也不降级，
> 那条断言会永远红，而且它红的时刻跟本轮改动无关。
> **26.10 会真的花一次 LLM 配额**（regenerate 一次 ≈ 一个小请求），但它是验收第 8 条
> 唯一的可复跑证明。

> 26.3 只断言**结构化字段**（`kpi_json`），不去解析 `summary_md` 里的自由文本 ——
> 从 Markdown 里抠数字再对表，做出来的是一个脆的、经常误报的检查。
> 「散文里的数字对不对」由 prompt 的硬性要求 + 26.3 的数字来源约束共同保证。

**修第 25 节的漏洞**（这是本轮必须做的，因为它现在就在放过失败）：

- **25.5**：在「等落库」之后，**追加断言该记录 `status='SUCCESS'`**。
  现在只断言记录存在。→ 拆成 25.5（记录存在）和 25.5b（状态成功）。
  （PARTIAL 不算成功，见 §4.4。）
- **25.6**：`sys_job_log.status=0` **不能**当成功证据 —— 改成
  「`sys_job_log` 有记录 **且** 对应的 `opc_agent_workflow_run` 是 SUCCESS」。
  并在注释里写明「`sys_job_log.status` 记的是 Feign 触发受理成功，不是业务成功」。

**触发源要能区分「定时」和「手动执行一次」**（决定 D13）：
RuoYi 的「执行一次」走的是 `scheduler.triggerJob(jobKey, dataMap)`（`SysJobServiceImpl.run()`）
→ **同一个 CronTrigger、同一个 invoke_target、同一个 triggerSource**。
所以现在 id 34 那条（其实是健康检查自己触发的）被记成了 `quartz:...`，
26.1 会被**健康检查自己喂绿**。

→ **实现**：手动执行时在 `JobDataMap` 里放一个标记（`SysJobServiceImpl.run()`），
`QuartzJobExecution.doExecute(context, sysJob)` 读出来放进 ThreadLocal，
`WorkflowCronJob.execute(code)` 读 ThreadLocal 决定 `triggerSource`：
- 定时触发 → `quartz:<code>`
- 手动执行一次 → `manual:<code>`

→ 26.1 只认 `quartz:`，健康检查自己触发的（`manual:`）不算数。

### 4.7 调度层

- **`sys_job` 的 cron 改成北京时间 09:00**：容器时区是 UTC（§3.7），
  `0 0 9 * * ?` 实际是**北京 17:00**（§1 第 14 条）—— README 说「每天 09:00」是假的。
  → 改成 `0 0 1 * * ?`（UTC 01:00 = 北京 09:00），并在 README/CLAUDE.md 写明口径。
  （另一种改法是给容器加 `TZ=Asia/Shanghai`，但那会改动所有 `LocalDateTime.now()` 的落库时间，
  影响面大得多 —— 不采用。）
- **`InsightDailyReportJob` 本轮不接调度** —— 它会和工作流产出重复（§2 非目标）。
  源码里留注释写明「本轮由工作流链产出日报，此 job 暂不接调度，避免两个日报」。
- **修 `WorkflowTriggerServiceImpl` 的落库**：工作流 `persist` 失败时，
  `opc_agent_workflow_run.status` 必须记 `FAILED` 并带上 `persist` 的错误信息
  （现在只记工作流本身的结果）。**产出物没落表 = 这件事没做成。**
- **PARTIAL 时 run 的 status 记 `PARTIAL`**（新增取值，`status` 是 `varchar(16)`）。
  健康检查 25.5b / 26.1 取严：**只认 `SUCCESS`**。
- **`WorkflowCronJob` 要记业务状态**：现在它只判 `R.code==200`（那是「Feign 受理成功」），
  而响应体里**已经带了 `status` / `errorMessage`** → 把它写进 `sys_job_log.job_message`，
  让「调度器记成功、业务记失败」这件事在日志里也看得见。

### 4.8 数据加厚的**后果**（已知，写进文档）

财务流水最后一笔是 **2026-10-21**。日报跑出来会写「2026-11-01 无资金流水」，
同时带一段「近 30 天共 6 笔，收 38450…支 3582…」。

**这是正确的输出，不是 bug。** 假链路一定会编出「昨日营收 3.8 万元」——
**两者一对比，就是这套改造最有说服力的证据**。

演示时建议这样呈现：先展示改造前那 4 条 FAILED 记录 + 编造的字段，
再展示改造后的日报（如实说无流水 + 近 30 天真数字）→ 说明「它不再编了」。

---

## 5. 改动清单（文件级）

| 模块 | 文件 | 改动 |
|---|---|---|
| **opc-agent-hub** | `tools/FinanceTools.java`（**从 ai-core 搬过来**）+ `tools/FinanceToolClient.java`、`tools/BillingToolClient.java` | 3 个真实现 + 下线 3 个假工具；client 返回 `R<Map>` |
| **opc-agent-hub** | `workflow/WorkflowEngine.java` | `runTool` 落地；`runLlm` 支持节点级 `stepTimeoutSeconds`；注入内置日期变量；跑完执行 `persist` |
| **opc-agent-hub** | `workflow/WorkflowPersistHandler.java`（新） | `persist` 声明 → 调 insight；PARTIAL 判定；`kpi_json` 由工具结果直接写 |
| **opc-agent-hub** | `feign/RemoteInsightService.java`（新） | `@InnerAuth` upsert 调用 |
| **opc-agent-hub** | `service/impl/WorkflowTriggerServiceImpl.java` | `persist` 失败 → run 记 FAILED；PARTIAL → run 记 PARTIAL |
| **opc-agent-hub** | `controller/OpcWorkflowTriggerController.java` | 无需改（返回体已带 status） |
| **opc-ai-core** | `tools/ToolRegistry.java` | `register(name, spec, handler)` —— 同时存 spec（唯一真相源） |
| **opc-ai-core** | `tools/builtin/FinanceTools.java` | **删除**（搬到 agent-hub） |
| **opc-ai-core** | `gateway/llm/LlmGateway.java` | **实现重试**（429 不重试） |
| **opc-ai-core** | `security/PromptGuard.java` | 白名单同步：删掉已下线的 3 个假工具；加 `query_bank_flow` 为下一轮留门 |
| **opc-ai-core** | `application.yml` | `opc.llm.deepseek.enabled: ${OPC_LLM_DEEPSEEK_ENABLED:false}` |
| **opc-finance** | `controller/OpcFinanceController.java` | 加 2 个 `@InnerAuth` 工具端点（同一套 `@ExceptionHandler`） |
| **opc-finance** | `service/impl/OpcFinanceBankFlowServiceImpl.java` | 加按日区间查询 + 汇总方法 |
| **opc-finance** | `service/impl/OpcFinanceVoucherServiceImpl.java` | 加按日区间查询 + 汇总方法（totalDebit/totalCredit → in/out 口径） |
| **opc-insight** | `controller/InsightInternalController.java`（新） | `@InnerAuth POST /opc/insight/daily/upsert` + `R<T>` 错误契约 |
| **opc-insight** | `service/impl/DailyReportServiceImpl.java` | upsert + 冲突判定（MANUAL 不许覆盖 AGENT_WORKFLOW） |
| **opc-insight** | `application.yml` | 加 `opc.llm` 段（与 Nacos 那份逐条一致）—— 补 key 缺口 |
| **opc-insight** | `workflow/InsightDailyReportJob.java` | 注释：本轮不接调度（避免两个日报） |
| **ruoyi-common** | `constant/ServiceNameConstants.java` | 加 `INSIGHT_SERVICE = "opc-insight"` |
| **ruoyi-job** | `task/WorkflowCronJob.java` | 区分 `quartz:` / `manual:` 触发源；把业务 status 写进 job_message |
| **ruoyi-job** | `util/QuartzJobExecution.java` + `service/SysJobServiceImpl.java` | 手动执行时在 JobDataMap 里放标记 → ThreadLocal |
| **deploy** | `docker-compose.yml` | `aiopc-insight` 加 `DEEPSEEK_API_KEY`；三处去掉 `sk-placeholder`；加 `OPC_LLM_DEEPSEEK_ENABLED` |
| **deploy** | `.env` / `.env.example` | 配 `DEEPSEEK_API_KEY`（**只写键名，不写值**） |
| **deploy** | `nacos/application-dev.yml` | `opc.llm.deepseek.enabled` 改成占位符（与本地 yml 逐条一致） |
| **sql** | `sql/migrations/V20261102__finance_agent_truth.sql` | 改 `opc_agent_workflow` 的 `company_id` + `dag_json`；`opc_insight_daily_report` 加 3 列；`sys_job` cron 改成 `0 0 1 * * ?`；**删掉 7 行种子日报** |
| **sql** | `sql/seed/opc_demo_ecommerce_seed.sql` | 不再种那 7 行假日报（**迁移 + seed 双写**） |
| **sql** | `sql/seed/opc_agent_workflow_seed.sql` | 同步改 dag_json（否则新环境又种出旧的假 dag） |
| **deploy** | `scripts/health-check.sh` | 修 25.5 / 25.6；新增第 26 节（8 条） |
| **前端** | `vue3-typescript/src/views/opc/insight/daily.vue` + `src/api/opc/insight.ts` | 日报行显示 `report_status` / `report_source` 标记；**并修三处「页面上是空的」**（列表数字列、详情弹窗的 `current.kpi`、摘要框的 `current.summary` —— 后端返回的是 `kpiJson` 字符串和 `summaryMd`，前端读的是 `kpi` 和 `summary`）|
| **docs** | `CLAUDE.md` / 根 `README.md` | 更新「定时任务」段；**cron 时间口径改成北京时间**；已知问题 |

**不改**：任何 `deploy/helm/`、`deploy/k8s/` 下的文件（K8s 已划出范围）。

---

## 6. 验收标准

**做完 = 以下全部成立**（不是「代码写完了」，是「断言全绿」）：

1. `bash deploy.sh health` → **26 节全绿**，且第 25 节新增的「状态成功」断言也绿。
2. **最近一次** `trigger_source LIKE 'quartz:%'` 的运行是 `status='SUCCESS'`（26.6）。
   > **v1.3 更正**：v1.2 写的是「`trigger_type='CRON'` 的记录**全部** SUCCESS」。
   > 那条判据**永远红** —— 当前有 4 条历史 FAILED（10-30 / 10-31 / 11-01 的 429 和
   > 11-02 的超时），而 §8 待定 #3 已经定了「保留历史，不删」。
   > **旧账不该由新代码背**。判据改成「最近一次是 SUCCESS」——
   > 历史 4 条留着当证据（它们正是「428 连败」的现场）。
3. `opc_insight_daily_report` 有一行 `report_status='FULL'`、`report_source='AGENT_WORKFLOW'`、
   `llm_used` **不是** `FALLBACK` 的记录；`kpi_json.query_bank_flow.items[]` 里的金额**逐笔**能在
   `opc_finance_bank_flow` 里找到（当前应有 6 笔：3582 / 7080 / 9860 / 12150 / 13200 / 38450）。
4. `ToolRegistry` 里**没有**任何返回硬编码假值的工具（26.5 打 `GET /opc/agent/tools` 守着）。
5. 日报里**昨日（2026-11-01）如实写「昨日无资金流水」**，且**没有**任何 DB 里查不到的数字。
6. LLM 挂掉时，日报退化为「基于真实表的聚合数字」并标 `PARTIAL`（取数成功但写文案失败）
   或 `FALLBACK`（LLM 完全没参与），**不编段落**。
7. **降级链是接上的**：`GET /opc/llm/models` 返回 ≥ 2 个已启用 provider 且含 DeepSeek（26.9）。
   > **v1.3 更正**：v1.2 写的是「MiniMax 被 429 打断时，自动落到 DeepSeek（有一条
   > `opc_agent_token_usage.model` 是 DeepSeek 的模型名）」。
   > 那条判据**取决于外部配额** —— MiniMax 不 429 就永远没有 DeepSeek 的用量行，
   > 断言会一直红，而且红的原因跟本轮改动无关。拆成两半：
   > 26.9 判「接上了」（可复跑），「真降级过」靠 `opc_agent_token_usage` 里出现
   > DeepSeek 模型名时人工看一眼（**不写成断言**）。
8. `opc_insight_advice` 最近一条不是 `FALLBACK`（insight 自己的 LLM 也通了）—— 26.10 断言。
9. **新环境部署**（`deploy/mysql-initdb.d` + 种子）出来的库里**没有**那 7 行编的日报，
   `opc_agent_workflow` 的 dag_json 是新版本。
10. **前端**：日报页的「收入/支出/流水笔数/凭证数」四列显示**真数字**（不是空的、不是 0.00），
    「状态」列显示 完整/部分完成/未走 LLM，来源显示 数字员工/手工。
    > **v1.3 新增**：核对时发现这三处「页面上是空的」——列表列读 `row.totalRevenue`
    > （后端 `DailyReportVo` 根本没这个字段）、详情弹窗读 `current.kpi`（后端给的是
    > `kpiJson` 字符串）、摘要框读 `current.summary`（字段名是 `summaryMd`）。
    > **页面上看着有、其实全是空的** —— 跟本轮要治的病是同一种。

**测试**：

- 后端：`mvn -pl ruoyi-modules/opc-finance,ruoyi-modules/opc-agent-hub,ruoyi-modules/opc-ai-core,ruoyi-modules/opc-insight,ruoyi-modules/ruoyi-job -am test`
- 前端：`npx vitest run`（66 文件 / 1294 用例，必须全绿）
- 线上：`bash deploy.sh health`

---

## 7. 风险与回滚

| 风险 | 概率 | 处置 |
|---|---|---|
| 模型不按数据写，编数字 | 中 | prompt 硬性要求 + 26.3 对表（数字来源是 `kpi_json`，不依赖模型自觉） |
| 工具调的公司错（跨租户） | 低 | 工具层 companyId 强制来自 context；26.4 断言 |
| 第二个 provider 的 key 不生效 | 中 | 26.7 断言；compose 去掉了 `sk-placeholder` 默认值（没配就是 disabled，不是拿假 key 撞 401） |
| **MiniMax 429 + DeepSeek 也没额度 → 那条链仍然红** | 中 | 本轮接受：至少要能**看见**它红（26.6 + 错误摘要带 provider 名），而不是像现在这样「记成功」 |
| 重试放大配额消耗 | 中 | 429 **不重试**，只重试超时 / 5xx |
| `persist` 写表失败但工作流记成功 | 中 | 已定：persist 失败 → run 记 FAILED（§4.7） |
| 手工「生成日报」覆盖数字员工的日报 | 中 | 已定：`report_source` 列 + 冲突拒绝（§4.4）；26.8 断言 |
| 工具搬模块后，insight 的 `FinanceTools` 引用还在 | 中 | 搬干净：ai-core 里删掉旧文件 + agent-hub 里新增；编译期就能发现 |
| 改 cron 时间影响既有断言 | 低 | 只影响「什么时候跑」；§25 的等待逻辑是「触发后等落库」，不依赖具体钟点 |
| `opc-ai-core` 引入 finance 依赖造成 Maven 成环 | 高（若图省事） | 已定：工具搬去 agent-hub，且只用 `R<Map>` 泛型 Feign，**不加依赖**（§3.1） |
| 改动跨 6 个模块，回滚成本高 | 中 | 按模块分批提交；每批单独 rebuild + health |

**回滚**：每个模块独立提交，`git revert` 单个 commit 即可回退该模块；
`dag_json` / `company_id` / cron 的改动是数据层，回滚只需执行反向 UPDATE。

---

## 8. 待定（实现时定）

| # | 问题 | 状态 |
|---|---|---|
| 1 | `query_balance` 指向 opc-billing 钱包，是否要区分「公司钱包」与「个人余额」 | 实现时可定，默认公司钱包 |
| 2 | 前端日报页怎么显示 `report_status` / `report_source` | **已定（v1.3）**：加「状态」列（完整/部分完成/未走 LLM）+ 来源标记（数字员工/手工），并修三处「页面上是空的」 |
| 3 | `opc_agent_workflow_run` 里 4 条历史 FAILED 记录是清理还是保留标注 | 实现时定 —— 建议保留并加 remark，不删历史 |
| 4 | `opc.llm.max-retries` 的重试次数取值 | 实现时定 —— 建议 2 次（配合「429 不重试」） |
| 5 | 下一轮做「模型自主调工具」时，`PromptGuard` 白名单是改成「以注册表为准」还是继续手工维护 | 下一轮定 —— 本轮只做同步（删 3 个旧的、加 1 个新的） |
