# 财务数字员工深化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让「每天自动生成财务日报」这条链读到真实业务数据、产出可逐笔对账的结论，并在 LLM 挂掉时降级到「基于真实表的聚合」而不是编段落。

**Architecture:** 工作流 dag 改成 `n1 TOOL(2 个工具) → n2 LLM(写日报) → persist`；工具放在 opc-agent-hub，经 `@InnerAuth` Feign 调 opc-finance / opc-billing 的真实数据端点；引擎在跑完后确定性执行 `persist` 声明，把工具结果原样写进 `opc_insight_daily_report.kpi_json`（结构化数字）和 `summary_md`（散文）。LLM 节点失败 → PARTIAL，用引擎侧确定性聚合产出摘要，真实数据不丢。前端把日报的状态/来源标出来，并把两种 `kpi_json` 形状都显示成真数字。

**Tech Stack:** Java 17 / Spring Boot 3.5.16 / Spring Cloud 2025.0.2 / OpenFeign / MyBatis / MySQL 8 / JUnit 5 + Mockito / AssertJ / Vue 3.5 + TS 5.6 + Element Plus 2.13 + Vitest / Docker Compose / Quartz(RAMJobStore)

**Spec:** [`docs/superpowers/specs/2026-11-02-finance-agent-deepening-design.md`](../specs/2026-11-02-finance-agent-deepening-design.md)（**v1.3** —— 写这份计划时发现 v1.2 有 5 处事实错误，已在 spec 头部列明改动）

---

## Global Constraints

这几条是整个仓库的规矩，**每个 Task 都隐含包含**：

- **Maven 必须显式带 JDK 17**：`JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl <module> -am test`（本机默认 `java` 是 1.8）。`package` 不写 `~/.m2`，`install` 才写 —— **共享库（`ruoyi-common-*` / `opc-common` / `opc-ai-core`）改完必须 `install`**，否则镜像里还是旧包且部署照样报成功（CLAUDE.md 坑 #1）。
- **RuoYi 的错误是 HTTP 200 + `body.code=500`**；网关拒绝是 HTTP 200 + `body.code=401`。**`curl -sf` 判不出成败** —— 写检查必须看 `body.code`。
- **线上验证只走 `bash deploy.sh health` / `bash deploy.sh rebuild`**。直接 `bash scripts/health-check.sh` 会被安全分类器判成绕过既定入口而拒掉（CLAUDE.md 坑 #10）。
- **口令不上命令行**：查库用 `mysql -t ry-vue-opc -e '...'`（走 `/root/.my.cnf`）。**API key 只看有没有 / 多长，绝不打印值**；key 由用户自己写进 `springboot3/deploy/.env`，**不要发在对话里**。
- **生产操作要点名授权**：重建镜像 ≠ 授权重启容器 ≠ 授权推 Nacos 配置。实现阶段每一样都要单独问。
- **提交信息用中文，把「为什么」写清楚**，结尾加 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- **改已推送的历史（amend / rebase / filter-branch）必须先拿到用户点名授权。**
- **Maven 成环约束**：`opc-finance` 依赖 `opc-ai-core` ⇒ **opc-ai-core 不能依赖 opc-finance**。跨服务 Feign **只能用 `R<Map<String, Object>>` 泛型**，不引对端的 VO 类。
- **不重建空库**：103 张表现有数据不动，用增量迁移改。

## Review Focus

spec 说了「必须怎样」，但没说「边界输入会怎样」。下面 5 条是 spec 隐含、而任何 Task 的测试都没覆盖、却最可能咬人的地方 —— 每个 Task 的步骤里都有一条测试钉住它。

1. **LLM 挂掉时的日报**（Task 4 / Task 5 拥有）
   期望：日报**仍然落库**；数字全部来自真实表；`report_status='FALLBACK'`；正文明说「未走 LLM」；**正文里不出现任何 DB 里没有的数字**；`llm_used='FALLBACK'`。
2. **工具的跨租户**（Task 2 拥有）
   期望：`companyId` **只**从 `ToolExecutor` 注入的 context 取；dag 的 args 里写 `companyId: 999` 也**必须被忽略**，工具永远查工作流所属公司。
3. **手工点「生成日报」撞上数字员工的日报**（Task 4 拥有）
   期望：**拒绝覆盖**并给出明确中文提示（不是静默覆盖、不是 500）。
4. **工作流被「手动执行一次」**（Task 7 拥有）
   期望：那条运行记录 `trigger_source='manual:...'`、`trigger_type='MANUAL'`；健康检查第 26 节的「定时器跑成了」断言**不被它喂绿**。
5. **区间里一笔流水都没有**（Task 1 / Task 10 拥有）
   期望：工具返回 `count=0` 而不是报错；日报如实写「无资金流水」；健康检查的数字对账断言在**有数据时非空**（不能是「两个空集相等」这种空转）。

---

## 文件结构总览

| 模块 | 新建 | 修改 | 删除 |
|---|---|---|---|
| **opc-finance** | `vo/FlowRangeVo.java`、`vo/VoucherRangeVo.java` | `IOpcFinanceBankFlowService` / `Impl` / `Mapper` / `Mapper.xml`、`IOpcFinanceVoucherService` / `Impl` / `Mapper` / `Mapper.xml`、`OpcFinanceController` | — |
| **opc-ai-core** | `gateway/llm/LlmHttpException.java` | `tools/ToolRegistry.java`、`gateway/llm/ChatResponse.java`、`gateway/llm/HttpLlmClient.java`、`gateway/llm/DeepSeekProvider.java`、`gateway/llm/MiniMaxProvider.java`、`gateway/llm/LlmGateway.java`、`runtime/AgentRuntime.java`、`security/PromptGuard.java`、`common/constant/OpcConstants.java`、`application.yml` | `tools/builtin/FinanceTools.java` |
| **opc-agent-hub** | `tools/FinanceToolClient.java`、`tools/BillingToolClient.java`、`tools/FinanceTools.java`、`feign/RemoteInsightService.java`、`workflow/WorkflowPersistHandler.java`、`workflow/DeterministicAggregator.java`、`controller/OpcToolRegistryController.java` | `workflow/WorkflowEngine.java`、`service/impl/WorkflowTriggerServiceImpl.java`、`application.yml` | — |
| **opc-insight** | `controller/InsightInternalController.java`、`vo/DailyReportUpsertRequest.java` | `service/IDailyReportService.java`、`service/impl/DailyReportServiceImpl.java`、`domain/OpcInsightDailyReport.java`、`vo/DailyReportVo.java`、`mapper/OpcInsightDailyReportMapper.java` + `Mapper.xml`、`application.yml` | — |
| **ruoyi-common** | — | `constant/ServiceNameConstants.java` | — |
| **ruoyi-job** | — | `task/WorkflowCronJob.java`、`util/QuartzJobExecution.java`、`service/SysJobServiceImpl.java` | — |
| **sql** | `sql/migrations/V20261102__finance_agent_truth.sql` | `sql/seed/opc_agent_workflow_seed.sql`、`sql/seed/opc_demo_ecommerce_seed.sql`、`deploy/mysql-initdb.d/92-opc-agent-workflow-seed.sql`、`deploy/mysql-initdb.d/93-sys-job-workflow-seed.sql`、`deploy/mysql-initdb.d/07-opc-insight-schema.sql` | — |
| **deploy** | — | `docker-compose.yml`、`.env.example`、`nacos/application-dev.yml` | — |
| **health-check** | — | `deploy/scripts/health-check.sh` | — |
| **vue3-typescript** | — | `src/api/opc/insight.ts`、`src/views/opc/insight/daily.vue`、`src/views/opc/insight/__tests__/daily.spec.ts` | — |

---

### Task 1: 财务侧真实区间查询（opc-finance）

给「数字员工」提供可核对的真实流水/凭证数据。这是整条链的地基：后面每个断言都要拿它的输出跟数据库对表。

**Files:**
- Create: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/vo/FlowRangeVo.java`
- Create: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/vo/VoucherRangeVo.java`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/service/IOpcFinanceBankFlowService.java`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/service/IOpcFinanceVoucherService.java`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/service/impl/OpcFinanceBankFlowServiceImpl.java`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/service/impl/OpcFinanceVoucherServiceImpl.java`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/mapper/OpcFinanceBankFlowMapper.java`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/mapper/OpcFinanceVoucherMapper.java`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/resources/mapper/OpcFinanceBankFlowMapper.xml`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/resources/mapper/OpcFinanceVoucherMapper.xml`
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/controller/OpcFinanceController.java`
- Test: `springboot3/ruoyi-modules/opc-finance/src/test/java/com/ruoyi/opc/finance/service/impl/OpcFinanceRangeQueryTest.java`
- Test: `springboot3/ruoyi-modules/opc-finance/src/test/java/com/ruoyi/opc/finance/controller/OpcFinanceToolEndpointTest.java`

**Interfaces:**
- Consumes: 无（Task 1 是链的起点）
- Produces:
  - `com.ruoyi.opc.finance.vo.FlowRangeVo` / `VoucherRangeVo` —— 字段见下
  - `IOpcFinanceBankFlowService#queryRange(Long companyId, String fromDate, String toDate) -> FlowRangeVo`
  - `IOpcFinanceVoucherService#queryRange(Long companyId, String fromDate, String toDate) -> VoucherRangeVo`
  - `GET /opc/finance/tools/flows` / `GET /opc/finance/tools/vouchers` → `R<FlowRangeVo>` / `R<VoucherRangeVo>`（`@InnerAuth`）

**为什么区间不做成「近 30 天」**：演示库的流水最后一笔是 2026-10-21。固定 30 天窗口到 2026-11-21 之后就空了，断言会退化成「两个空集相等」—— 那种检查永远绿，等于没有。所以本 Task 的语义是：

- `fromDate` **缺省 = 该公司最早一条记录的日期**（不是 30 天前，是「自有数据起算」）
- `toDate` **缺省 = 昨天**（本地日期，北京时间；今天的数据不进日报）
- 区间**闭区间** `[fromDate 00:00:00, toDate 23:59:59]`
- 响应里**回写实际使用的 `fromDate` / `toDate`** —— 健康检查第 26 节靠这两个值原样复算一遍

`FlowRangeVo` 字段（`VoucherRangeVo` 同构，只是 `items` 类型不同）：

```java
Long companyId; String fromDate; String toDate;
int count; BigDecimal totalIn; BigDecimal totalOut; BigDecimal net;
int yesterdayCount; BigDecimal yesterdayIn; BigDecimal yesterdayOut;
List<...> items;
```

口径（**必须一致，否则对账全错**）：

| | `totalIn` | `totalOut` |
|---|---|---|
| 流水 | `direction='IN'` 的 amount 之和 | `direction='OUT'` 的 amount 之和 |
| 凭证 | `SUM(total_credit)`（贷方=收入） | `SUM(total_debit)`（借方=支出） |

- [ ] **Step 1: 写失败的测试 —— 区间口径与「昨天」子集**

新建 `OpcFinanceRangeQueryTest`（Mockito + `Mock` mapper）。用**仓库里的真实数据**当 fixture，值是实测的：

```java
// company 2 的 6 条流水（实测，2026-11-02）
id 27  2026-10-07  OUT  13200.00  PROCESSED  采购货款
id 28  2026-10-10  IN   38450.00  PROCESSED  小店结算款
id 29  2026-10-12  OUT  12150.00  PROCESSED  采购货款
id 30  2026-10-15  IN    9860.00  EXTRACTED  家居专场结算
id 31  2026-10-18  OUT   7080.00  PROCESSED  音箱采购货款
id 32  2026-10-21  IN    3582.00  IMPORTED   零散订单
// → totalIn=51892.00  totalOut=32430.00  net=19462.00
// company 2 的 5 张凭证（实测）
id 25  2026-10-07  debit 13200.00 credit     0.00  POSTED
id 26  2026-10-10  debit     0.00 credit 35400.00  POSTED
id 27  2026-10-15  debit 12150.00 credit     0.00  REVIEW
id 28  2026-10-19  debit  2600.00 credit     0.00  REVIEW
id 29  2026-10-20  debit  1800.00 credit     0.00  REJECTED
// → totalIn(credit)=35400.00  totalOut(debit)=29750.00
```

断言：

| 测试名 | 断言 |
|---|---|
| `queryRange_fromDateOmitted_usesEarliestRecordDate` | `fromDate` 缺省 → `vo.getFromDate()` == `"2026-10-07"`（mapper 的 `selectMinTradeDate` 返回 `"2026-10-07"`） |
| `queryRange_toDateOmitted_defaultsToYesterday` | `toDate` 缺省 → == 昨天（用 `Clock` 注入，测试里固定 `2026-11-02` → 期望 `"2026-11-01"`） |
| `queryRange_sumsInAndOutByDirection` | `totalIn`=51892.00、`totalOut`=32430.00、`net`=19462.00（scale=2） |
| `queryRange_yesterdaySubsetCountsOnlyYesterday` | `yesterdayCount` == 昨天那一天的条数（fixture 里昨天没数据 → 0）；`yesterdayIn`/`yesterdayOut` == 0.00 |
| `queryRange_credentialUsesCreditAsInAndDebitAsOut` | 凭证：`totalIn`=35400.00、`totalOut`=29750.00 |
| `queryRange_noRows_returnsZeroesNotNull` | mapper 返回空 list → `count`=0、`totalIn`/`totalOut`/`net` == `BigDecimal.ZERO`（**不是 null**）、`items` 是空 list（**不是 null**） |
| `queryRange_companyIdNull_throwsOpcException` | `companyId` 为 null → 抛 `OpcException`，**且不调 mapper**（`verifyNoInteractions(mapper)`） |

> `Clock` 注入：`queryRange` 的「昨天」不能直接用 `LocalDate.now()` —— 那会让测试随日期漂移。service 的构造里多接一个 `java.time.Clock`（默认 `Clock.systemDefaultZone()`），测试传 `Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("Asia/Shanghai"))`。
> **时区必须显式 `Asia/Shanghai`**：容器 JVM 是 UTC（CLAUDE.md 坑 #9 的时间口径），`LocalDate.now()` 会算成 UTC 的「昨天」，和日报口径差 8 小时。

- [ ] **Step 2: 跑测试确认失败**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-finance -am test -Dtest=OpcFinanceRangeQueryTest
```
Expected: FAIL —— 编译错误「找不到 queryRange / FlowRangeVo」。

- [ ] **Step 3: 实现 VO + service + mapper**

`FlowRangeVo`（`@Data @Builder @NoArgsConstructor @AllArgsConstructor`，`implements Serializable`）字段见上。`VoucherRangeVo` 同构，`items` 是 `List<OpcFinanceVoucher>`。

`OpcFinanceBankFlowServiceImpl` 加：

```java
private final java.time.Clock clock;   // 构造注入，默认 systemDefaultZone
```
（`@RequiredArgsConstructor` 只能带 final 字段 → `Clock` 用 final + 在需要的地方显式 new。**简单做法**：service 里不注入 Clock，改用一个 `protected LocalDate today()` 方法，测试用 `@SpyBean`/子类覆盖 —— 但本仓库是纯 Mockito 单测，**改成构造注入 `Clock` 最省事**：把 `@RequiredArgsConstructor` 换成显式构造器 `public OpcFinanceBankFlowServiceImpl(OpcFinanceBankFlowMapper mapper, Clock clock)`，Spring 侧需要提供 `Clock` bean。）

**决策**：不引入 `Clock` bean（那要在 4 个模块各加一个 `@Bean`）。改成 service 里定义

```java
/** 供测试覆盖的「今天」；生产用北京时间。 */
protected LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai"));
}
```
测试用 Mockito `spy` + `doReturn(...)` 覆盖 `today()`。

Mapper 新增（`OpcFinanceBankFlowMapper.java` + XML）：

```java
List<OpcFinanceBankFlow> selectByCompanyAndDateRange(@Param("companyId") Long companyId,
                                                      @Param("fromDate") LocalDate fromDate,
                                                      @Param("toDate") LocalDate toDate);
String selectMinTradeDate(@Param("companyId") Long companyId);
```
XML：
```xml
<select id="selectByCompanyAndDateRange" resultMap="BaseResultMap">
    SELECT <include refid="Base_Columns" />
    FROM opc_finance_bank_flow
    WHERE company_id = #{companyId}
      AND trade_time &gt;= #{fromDate}
      AND trade_time &lt;  DATE_ADD(#{toDate}, INTERVAL 1 DAY)
    ORDER BY trade_time ASC, id ASC
</select>

<select id="selectMinTradeDate" resultType="java.lang.String">
    SELECT DATE_FORMAT(MIN(trade_time), '%Y-%m-%d')
    FROM opc_finance_bank_flow WHERE company_id = #{companyId}
</select>
```

凭证（`OpcFinanceVoucherMapper.java` + XML）：

```java
List<OpcFinanceVoucher> selectByCompanyAndDateRange(@Param("companyId") Long companyId,
                                                     @Param("fromDate") LocalDate fromDate,
                                                     @Param("toDate") LocalDate toDate);
String selectMinVoucherDate(@Param("companyId") Long companyId);
```
XML 用 `voucher_date`（DATE 列）：
```xml
<select id="selectByCompanyAndDateRange" resultMap="BaseResultMap">
    SELECT <include refid="Base_Columns" />
    FROM opc_finance_voucher
    WHERE company_id = #{companyId}
      AND voucher_date &gt;= #{fromDate}
      AND voucher_date &lt;= #{toDate}
    ORDER BY voucher_date ASC, id ASC
</select>
```

service 的 `queryRange` 实现要点：
- `companyId` 为 null → `throw new OpcException("companyId 不能为空")`
- `fromDate` 解析失败 → `throw new OpcException("fromDate 格式非法，应为 yyyy-MM-dd: " + fromDate)`
- `toDate` 缺省 = `today().minusDays(1)`；`fromDate` 缺省 = `mapper.selectMinTradeDate(companyId)`，为 null 时用 `toDate`
- `fromDate` 晚于 `toDate` → `throw new OpcException("fromDate 不能晚于 toDate")`
- 汇总在 Java 里算（mapper 只查明细）—— 明细最多几十条，直接算最简单，也避免 SQL 口径漂移
- `yesterday*` = 明细里 `tradeTime` 落在昨天的子集（流水按 `tradeTime`，凭证按 `voucherDate`）

- [ ] **Step 4: 跑测试确认通过**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-finance -am test -Dtest=OpcFinanceRangeQueryTest
```
Expected: PASS，7 个用例全绿。

- [ ] **Step 5: 加 controller 端点 + 测试**

`OpcFinanceController` 加两个 `@InnerAuth` 端点（放在既有 4 个 agg 端点旁边，**同一个 controller 级 `@ExceptionHandler(OpcException.class)` 自动覆盖**）：

```java
@Operation(summary = "工具端点：银行流水区间查询（数字员工）")
@InnerAuth
@GetMapping("/tools/flows")
public R<FlowRangeVo> toolFlows(@RequestParam Long companyId,
                                @RequestParam(required = false) String fromDate,
                                @RequestParam(required = false) String toDate) {
    return R.ok(bankFlowService.queryRange(companyId, fromDate, toDate));
}

@Operation(summary = "工具端点：凭证区间查询（数字员工）")
@InnerAuth
@GetMapping("/tools/vouchers")
public R<VoucherRangeVo> toolVouchers(@RequestParam Long companyId,
                                      @RequestParam(required = false) String fromDate,
                                      @RequestParam(required = false) String toDate) {
    return R.ok(voucherService.queryRange(companyId, fromDate, toDate));
}
```

新建 `OpcFinanceToolEndpointTest`（参照既有 `OpcFinanceAggControllerTest` 的写法）：

| 测试名 | 断言 |
|---|---|
| `toolFlows_returnsRangeVo` | `body.code` == 200，`data.count` == 6，`data.totalIn` == 51892.00 |
| `toolFlows_requiresCompanyId` | 不传 `companyId` → `body.code` != 200（`R.fail`），**不是** HTTP 500 |
| `toolVouchers_returnsRangeVo` | `data.totalIn` == 35400.00（贷方口径） |

- [ ] **Step 6: 跑 opc-finance 全量测试**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-finance -am test
```
Expected: 全绿（既有 3 个测试类 + 新增 2 个）。

- [ ] **Step 7: 提交**

```bash
git add springboot3/ruoyi-modules/opc-finance
git commit -m "$(cat <<'EOF'
feat(finance): 加两个 @InnerAuth 工具端点 —— 让数字员工能查到真流水

以前财务侧只有按月聚合的 4 个 agg 端点,数字员工拿不到「某段时间有哪些流水」。
新加的 tools/flows 与 tools/vouchers 返回闭区间内的明细 + 汇总 + 昨日子集。

区间语义刻意不做成「近 30 天」:演示库最后一笔流水是 2026-10-21,固定 30 天
窗口过了 2026-11-21 就空了,后面的对账断言会退化成「两个空集相等」——
永远绿,等于没有。所以 fromDate 缺省 = 该公司最早一条记录,toDate 缺省 = 昨天,
并且把实际用的区间回写进响应,让健康检查能原样复算。

凭证没有 amount 列,in/out 用 total_credit / total_debit 换算,与流水同一套口径。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 工具层搬家 + 真实现（opc-agent-hub + opc-ai-core）

把 5 个返回硬编码假值的工具换成 3 个真工具，并把它们**搬出 opc-ai-core**。

**为什么要搬**：旧 `FinanceTools` 住在 `com.ruoyi.opc.ai.tools.builtin`，而**四个进程都扫得到它**：

| 进程 | 扫描范围 | 后果 |
|---|---|---|
| `opc-ai-core` | `com.ruoyi.opc` + `com.ruoyi.ai` | 会去 Feign 调 opc-finance → **运行时服务环** |
| `opc-finance` | `com.ruoyi.opc` + `com.ruoyi.finance` | **自己 Feign 调自己** |
| `opc-insight` | `com.ruoyi.opc.insight` + `com.ruoyi.opc.ai` | 新 `@FeignClient` 注册不进来 → **启动直接失败** |
| `opc-agent-hub` | `com.ruoyi.opc` + `com.ruoyi.agent` | 正常（就是要它） |

搬到 `com.ruoyi.opc.agent.tools` 后，opc-agent-hub 的 pom 里**没有** opc-finance / opc-insight 的依赖 → 另外三个进程根本扫不到这些类。

**Files:**
- Delete: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/tools/builtin/FinanceTools.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/tools/ToolRegistry.java`
- Create: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/tools/FinanceToolClient.java`
- Create: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/tools/BillingToolClient.java`
- Create: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/tools/FinanceTools.java`
- Create: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/controller/OpcToolRegistryController.java`
- Test: `springboot3/ruoyi-modules/opc-agent-hub/src/test/java/com/ruoyi/opc/agent/tools/FinanceToolsTest.java`
- Test: `springboot3/ruoyi-modules/opc-agent-hub/src/test/java/com/ruoyi/opc/agent/controller/OpcToolRegistryControllerTest.java`
- Test: `springboot3/ruoyi-modules/opc-ai-core/src/test/java/com/ruoyi/opc/ai/tools/ToolRegistrySpecTest.java`

**Interfaces:**
- Consumes: Task 1 的 `GET /opc/finance/tools/flows`、`/tools/vouchers`（`R<FlowRangeVo>` / `R<VoucherRangeVo>`）
- Produces:
  - `ToolRegistry#register(String name, ToolSpec spec, ToolHandler handler)`（新）
  - `ToolRegistry#spec(String name) -> ToolSpec`（新，可能返回 null）
  - `ToolRegistry#specs() -> Map<String, ToolSpec>`（新）
  - `com.ruoyi.opc.agent.tools.FinanceTools` 注册 3 个工具：`query_bank_flow` / `query_voucher` / `query_balance`
  - `com.ruoyi.opc.agent.tools.FinanceToolClient`：`R<Map<String,Object>> flows(Long, String, String)`、`vouchers(Long, String, String)`
  - `com.ruoyi.opc.agent.tools.BillingToolClient`：`R<Map<String,Object>> wallet(Long)`
  - `OpcToolRegistryController`：`@InnerAuth GET /opc/agent/tools` → `R<List<Map<String,Object>>>`，每项 `{name, specDescription, hasSpec}`
  - **工具返回值 = JSON 字符串**，统一带 `source`（**来源表名**）+ `companyId` + `fromDate`/`toDate` + 汇总 + `items`：

```json
{"source":"opc_finance_bank_flow","companyId":2,
 "fromDate":"2026-10-07","toDate":"2026-11-01",
 "count":6,"totalIn":51892.00,"totalOut":32430.00,"net":19462.00,
 "yesterdayCount":0,"yesterdayIn":0.00,"yesterdayOut":0.00,
 "items":[...]}
```

  - `query_voucher` 的 `source` = `"opc_finance_voucher"`；`query_balance` 的 `source` = `"opc_wallet"`，字段为 `{source, companyId, balance, currency}`（**没有区间概念**）
  - `source` 让「数字从哪来」可核对；`fromDate`/`toDate` 让「哪段区间」可复算 —— 第 26.3 条断言靠这两组字段回查数据库

- [ ] **Step 1: 写失败的测试 —— 假工具下线 + spec 存注册表**

新建 `ToolRegistrySpecTest`（opc-ai-core）：

| 测试名 | 断言 |
|---|---|
| `register_withSpec_storesSpec` | `register("x", spec, handler)` 后 `spec("x")` 返回同一个 spec，`specs()` 含 `"x"` |
| `register_withoutSpec_leavesSpecNull` | 旧的两参 `register(name, handler)` 仍可用，`spec(name)` == null |
| `unregister_removesBothHandlerAndSpec` | `unregister("x")` 后 `exists("x")` false 且 `spec("x")` null |

新建 `FinanceToolsTest`（opc-agent-hub，Mockito mock 两个 Feign client）：

| 测试名 | 断言 |
|---|---|
| `register_publishesExactlyThreeTools` | 注册表里恰好是 `query_bank_flow` / `query_voucher` / `query_balance` 三个 |
| `register_doesNotPublishFakeTools` | **`query_daily_report` / `create_voucher` / `extract_voucher` 都不在注册表里**（假工具下线护栏 —— 健康检查第 26.5 节断言的就是这条） |
| `queryBankFlow_companyIdComesFromContextOnly` | context 的 `companyId`=2 → 传给 Feign 的是 2；**args 里写 `"companyId":999` 被忽略**，Feign 收到的仍是 2（跨租户护栏 —— Review Focus #2） |
| `queryBankFlow_returnsSourceTaggedJson` | 返回 JSON 里 `source` == `"opc_finance_bank_flow"`，且 `count` == 6、`totalIn` == 51892.00、`fromDate` == `"2026-10-07"`、`toDate` == `"2026-11-01"`（**透传 finance 端返回的数据，不加工**） |
| `queryVoucher_usesCreditAsIn` | `source` == `"opc_finance_voucher"`，`totalIn` == 35400.00（贷方口径）透传 |
| `queryBankFlow_passesFromToFromArgs` | args 的 `fromDate`/`toDate` 原样传给 Feign；**不传就不传**（finance 端负责缺省） |
| `queryBalance_callsBillingWallet` | `billingClient.wallet(2)` 被调，返回 `{"source":"opc_wallet","companyId":2,"balance":2860.00,"currency":"CNY"}` |
| `queryBankFlow_feignFailure_throws` | Feign 返回 `R.fail(...)`（code != 200）→ 抛 `OpcException`，**不返回空 JSON 冒充成功** |

新建 `OpcToolRegistryControllerTest`（MockMvc 或直接调 controller 方法 + mock `ToolRegistry`）：

| 测试名 | 断言 |
|---|---|
| `tools_listsRegisteredNames` | 返回的 `data` 里含 `query_bank_flow` / `query_voucher` / `query_balance` 三个 `name` |
| `tools_doesNotListFakeTools` | **不含** `query_daily_report` / `create_voucher` / `extract_voucher`（第 26.5 条断言打的就是这个端点） |
| `tools_reportsHasSpec` | 每个 name 都带 `hasSpec: true`（spec 与 handler 同生共死） |

- [ ] **Step 2: 跑测试确认失败**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-ai-core,ruoyi-modules/opc-agent-hub -am test -Dtest=ToolRegistrySpecTest+FinanceToolsTest+OpcToolRegistryControllerTest
```
Expected: FAIL —— 编译错误（找不到 `register(String,ToolSpec,ToolHandler)` / `FinanceTools`）。

- [ ] **Step 3: `ToolRegistry` 存 spec**

```java
private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();
private final Map<String, ToolSpec> specs = new ConcurrentHashMap<>();

public void register(String name, ToolSpec spec, ToolHandler handler) {
    handlers.put(name, handler);
    if (spec != null) specs.put(name, spec);
    log.info("[ToolRegistry] 注册工具 name={} hasSpec={}", name, spec != null);
}

public void register(String name, ToolHandler handler) { register(name, null, handler); }

public void unregister(String name) { handlers.remove(name); specs.remove(name); }

public ToolSpec spec(String name) { return specs.get(name); }

public Map<String, ToolSpec> specs() { return Map.copyOf(specs); }
```
> spec 与 handler 同生共死 —— 否则「规格说有这个工具、实际没有」又是一句假话。

- [ ] **Step 4: 写 Feign client + 新 FinanceTools，删旧文件**

`FinanceToolClient`（opc-agent-hub）：

```java
@FeignClient(contextId = "financeToolClient", value = ServiceNameConstants.FINANCE_SERVICE)
public interface FinanceToolClient {
    @GetMapping("/opc/finance/tools/flows")
    R<Map<String, Object>> flows(@RequestParam("companyId") Long companyId,
                                  @RequestParam(value = "fromDate", required = false) String fromDate,
                                  @RequestParam(value = "toDate", required = false) String toDate);

    @GetMapping("/opc/finance/tools/vouchers")
    R<Map<String, Object>> vouchers(@RequestParam("companyId") Long companyId,
                                     @RequestParam(value = "fromDate", required = false) String fromDate,
                                     @RequestParam(value = "toDate", required = false) String toDate);
}
```

`BillingToolClient`：

```java
@FeignClient(contextId = "billingToolClient", value = ServiceNameConstants.BILLING_SERVICE)
public interface BillingToolClient {
    @GetMapping("/opc/billing/agg/wallet")
    R<Map<String, Object>> wallet(@RequestParam("companyId") Long companyId);
}
```

> `R<Map<String, Object>>` 是硬约束：opc-agent-hub 的 pom 里**没有** opc-finance / opc-billing 的依赖，不能引它们的 VO；返回体用泛型 Map 接住，Jackson 会把嵌套对象转成 `LinkedHashMap`。

新 `FinanceTools`（opc-agent-hub，包 `com.ruoyi.opc.agent.tools`）：

```java
@Slf4j
@Component
public class FinanceTools {

    private final ToolRegistry registry;
    private final FinanceToolClient financeClient;
    private final BillingToolClient billingClient;

    @PostConstruct
    void register() {
        registry.register("query_bank_flow", SPEC_BANK_FLOW, this::queryBankFlow);
        registry.register("query_voucher",   SPEC_VOUCHER,   this::queryVoucher);
        registry.register("query_balance",   SPEC_BALANCE,   this::queryBalance);
    }

    public String queryBankFlow(String argsJson, Map<String, Object> context) {
        Long companyId = requireCompanyId(context);
        Args args = Args.parse(argsJson);
        Map<String, Object> data = requireData(financeClient.flows(companyId, args.fromDate(), args.toDate()));
        return toJson("query_bank_flow", companyId, data);
    }
    // queryVoucher 同构；queryBalance 调 billingClient.wallet(companyId)
}
```

关键实现点：
- `requireCompanyId(context)`：`context.get("companyId")` 为 null/0 → 抛 `OpcException("工具上下文缺少 companyId")`。**args 里的 companyId 一律不读。**
- `requireData(R<Map<String,Object>> r)`：`r == null || r.getCode() != R.SUCCESS || r.getData() == null` → 抛 `OpcException("远端服务返回失败: " + r.getMsg())`。**绝不返回空 Map 冒充成功。**
- `toJson(source, companyId, data)`：复制 data 到新 LinkedHashMap，**先 put `"source"`（来源表名），再 put `"companyId"`**（如果 data 里已有 companyId，data 的值优先 —— finance 端返回的是权威值），Jackson 序列化。`source` 由工具自己写死（`opc_finance_bank_flow` / `opc_finance_voucher` / `opc_wallet`），**不从远端响应里取** —— 远端说了不算，工具知道自己查了哪张表。
- `Args.parse(argsJson)`：解析 `fromDate` / `toDate` 两个可选字符串；**忽略任何其它键**（包括 companyId）。
- 3 个 `ToolSpec` 常量：`query_bank_flow`（参数 fromDate/toDate，可选）、`query_voucher`（同）、`query_balance`（无参数）。

**删除** `opc-ai-core/.../tools/builtin/FinanceTools.java`（全仓只有它自己引用，已核对）。

**新增 `OpcToolRegistryController`**（opc-agent-hub）：

```java
@RestController
@RequestMapping("/opc/agent/tools")
@RequiredArgsConstructor
public class OpcToolRegistryController {

    private final ToolRegistry registry;

    /** 工具注册表的对外视图 —— 健康检查第 26.5 条靠它断言「假工具确实下线了」。 */
    @InnerAuth
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        Map<String, ToolSpec> specs = registry.specs();
        return R.ok(registry.names().stream()
                .map(name -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    ToolSpec spec = specs.get(name);
                    item.put("hasSpec", spec != null);
                    item.put("specDescription", spec == null ? null : spec.getDescription());
                    return item;
                })
                .toList());
    }
}
```
> **为什么要有这个端点**：不接任何断言就等于「假工具下线了」这句只是嘴上说 —— 注册表里还有没有假工具，从外面看不见。健康检查打这个端点，26.5 才有东西可断言。
> **只读、只回名字**：不返回任何参数 schema 之外的敏感信息，也不返回 handler 的实现细节。

- [ ] **Step 5: 跑测试确认通过**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-ai-core,ruoyi-modules/opc-agent-hub -am test -Dtest=ToolRegistrySpecTest+FinanceToolsTest+OpcToolRegistryControllerTest
```
Expected: PASS。

- [ ] **Step 6: 跑 ai-core + agent-hub 全量测试**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-ai-core,ruoyi-modules/opc-agent-hub -am test
```
Expected: 全绿。**注意 ai-core 改了 `ToolRegistry` 签名 —— 若有别处调用旧的两参 `register`，编译期就会报出来。**

- [ ] **Step 7: 提交**

```bash
git add springboot3/ruoyi-modules/opc-ai-core springboot3/ruoyi-modules/opc-agent-hub
git commit -m "$(cat <<'EOF'
feat(agent-hub): 工具层搬到 agent-hub 并换成真实现 —— 假工具下线

旧的 FinanceTools 住在 opc-ai-core 的 com.ruoyi.opc.ai.tools.builtin,五个工具
全是硬编码假返回(query_daily_report 写死 revenue:0、create_voucher 返回
V-PLACEHOLDER)。更麻烦的是四个进程都扫得到它:finance 会自己 Feign 调自己,
insight 装了新的 @FeignClient 会直接启动失败,ai-core 会形成运行时服务环。

搬到 com.ruoyi.opc.agent.tools 后,opc-agent-hub 的 pom 里没有 opc-finance /
opc-insight 的依赖,另外三个进程根本扫不到这些类。

新工具只留 3 个真实现:query_bank_flow / query_voucher / query_balance。
companyId 只从 ToolExecutor 注入的 context 取,dag 的 args 里写什么都不影响——
跨租户在工具层就堵死。

ToolRegistry 顺带存 spec(规格与 handler 同生共死),下一轮「模型自主调工具」
可以直接用;本轮不需要 OpenAI schema。

另外加了个 @InnerAuth GET /opc/agent/tools,只列注册表里的工具名。
不加这个端点,「假工具下线了」就只是嘴上说 —— 注册表里还有没有假工具,
从外面根本看不见,健康检查也没东西可断言。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 引擎 TOOL 节点落地 + 内置日期变量（opc-agent-hub）

`WorkflowEngine.runTool()` 现在是 `// TODO: 调 ToolExecutor` + 塞一个 `"{}"`。本 Task 让它真的调工具。

**Files:**
- Modify: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/workflow/WorkflowEngine.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/runtime/AgentRuntime.java`
- Create: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/workflow/DeterministicAggregator.java`
- Test: `springboot3/ruoyi-modules/opc-agent-hub/src/test/java/com/ruoyi/opc/agent/workflow/WorkflowEngineToolNodeTest.java`
- Test: `springboot3/ruoyi-modules/opc-agent-hub/src/test/java/com/ruoyi/opc/agent/workflow/DeterministicAggregatorTest.java`

**Interfaces:**
- Consumes: Task 2 的 `ToolExecutor#execute(String toolName, String argsJson, AgentTaskRequest req)`（在 opc-ai-core 里，本模块已依赖）
- Produces:
  - `WorkflowEngine` 新增构造依赖 `ToolExecutor toolExecutor`
  - `WorkflowContext` 新增字段 `String llmModel`（LLM 节点实际用的模型名，LLM 失败时为 null）、`boolean partial`、`String llmError`
  - `WorkflowEngine.WorkflowDefinition` 新增字段 `Long runId`
  - `WorkflowEngine.WorkflowResult` 新增字段 `boolean partial`
  - 内置变量：`today` / `yesterday` / `weekAgo` / `monthAgo`（`yyyy-MM-dd`，**北京时间**）+ 只读的 `companyId`
  - `DeterministicAggregator#bankFlowSummary(Map<String,Object> flowResult, String yesterday) -> String`
  - `DeterministicAggregator#voucherSummary(Map<String,Object> voucherResult, String yesterday) -> String`

- [ ] **Step 1: 写失败的测试 —— TOOL 节点**

新建 `WorkflowEngineToolNodeTest`（Mockito，`@Mock AgentRuntime`、`@Mock ToolExecutor`）：

dag fixture：
```json
{"nodes":[{"id":"n1","type":"TOOL",
           "tools":["query_bank_flow","query_voucher"],
           "args":{"fromDate":"${monthAgo}"}}],
 "edges":[]}
```

| 测试名 | 断言 |
|---|---|
| `toolNode_callsEveryDeclaredTool` | `toolExecutor.execute` 被调 2 次，工具名分别是 `query_bank_flow` / `query_voucher` |
| `toolNode_putsMergedResultUnderNodeId` | `ctx.outputs` 里 `"n1"` 是 JSON 字符串，含两个工具的结果；`"n1.query_bank_flow"` 也单独存一份 |
| `toolNode_usesCompanyIdFromDefinition` | 传给 `toolExecutor.execute` 的 `AgentTaskRequest.getCompanyId()` == 定义里的 companyId（= 2）——**工具的归属来自工作流，不来自 dag** |
| `toolNode_rendersDateVariablesInArgs` | args 里的 `${monthAgo}` 被替换成实际日期字符串（北京时间口径），**不留下 `${...}` 原样** |
| `toolNode_injectsBuiltinDateVariables` | prompt 里 `${yesterday}` 被替换为昨天；`${today}` / `${weekAgo}` 同理 |
| `toolNode_withoutTools_fails` | TOOL 节点没声明 `tools` → run 失败，`errorMessage` 含 `"没有声明 tools"` |
| `toolNode_toolThrows_failsRun` | `toolExecutor.execute` 抛异常 → run 失败（**TOOL 失败是硬失败**，不吞） |
| `llmNode_usesNodeLevelTimeout` | dag 节点写 `"stepTimeoutSeconds":60` → `AgentTaskRequest.getStepTimeoutSeconds()` == 60；没写 → null（走默认 30） |

新建 `DeterministicAggregatorTest`（纯函数）：

| 测试名 | 断言 |
|---|---|
| `bankFlowSummary_statesNoFlowWhenYesterdayIsEmpty` | `yesterdayCount`=0 → 输出含 `"昨日无资金流水"` |
| `bankFlowSummary_statesFlowWhenYesterdayHasRows` | `yesterdayCount`=2 → 输出含 `"昨日 2 笔"`，**且不含** `"无资金流水"` |
| `bankFlowSummary_containsOnlyRealNumbers` | 输出里的每个数字都能在输入 map 里找到（`count`/`totalIn`/`totalOut` 逐一断言） |
| `bankFlowSummary_marksFallbackPrefix` | 输出以 `"[自动聚合·未走 LLM]"` 开头（和 insight 现有 fallback 口径一致，界面能认出这是没走 LLM 的） |

- [ ] **Step 2: 跑测试确认失败**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-agent-hub -am test -Dtest=WorkflowEngineToolNodeTest+DeterministicAggregatorTest
```
Expected: FAIL —— 编译错误（找不到 `DeterministicAggregator`、`ToolExecutor` 未注入）。

- [ ] **Step 3: 实现内置变量 + TOOL 节点**

`WorkflowEngine` 改动：

```java
private final AgentRuntime agentRuntime;
private final ToolExecutor toolExecutor;   // 新增
```
（`@RequiredArgsConstructor` 自动带上。既有测试 `WorkflowEngineAttributionTest` 用 `@InjectMocks` 注入 —— `ToolExecutor` 没 mock 时会是 null，但那个测试只跑 LLM 节点，不受影响。**给那个测试类补一个 `@Mock ToolExecutor` 更稳**。）

新增：

```java
/** 内置日期变量 —— 北京时间口径。容器 JVM 是 UTC,LocalDate.now() 会差 8 小时。 */
private Map<String, Object> builtinVars() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    Map<String, Object> vars = new HashMap<>();
    vars.put("today", today.toString());
    vars.put("yesterday", today.minusDays(1).toString());
    vars.put("weekAgo", today.minusDays(7).toString());
    vars.put("monthAgo", today.minusDays(30).toString());
    return vars;
}

private Map<String, Object> buildVars(WorkflowContext ctx) {
    Map<String, Object> vars = new HashMap<>();
    vars.putAll(ctx.inputParams);
    vars.putAll(ctx.outputs);
    vars.putAll(builtinVars());
    if (ctx.companyId != null) vars.put("companyId", ctx.companyId);
    return vars;
}
```

`runTool`：

```java
private void runTool(JsonNode nodeDef, WorkflowContext ctx) throws Exception {
    String nodeId = nodeDef.path("id").asText();
    JsonNode toolsNode = nodeDef.path("tools");
    if (toolsNode == null || !toolsNode.isArray() || toolsNode.isEmpty()) {
        throw new OpcException("TOOL 节点 " + nodeId + " 没有声明 tools");
    }
    Map<String, Object> vars = buildVars(ctx);
    AgentRuntime.AgentTaskRequest req = AgentRuntime.AgentTaskRequest.builder()
            .companyId(ctx.companyId)
            .userId(ctx.userId)
            .sessionId("wf-" + nodeId + "-" + System.currentTimeMillis())
            .build();
    String argsJson = renderTemplate(nodeDef.path("args").toString(), vars);

    Map<String, Object> merged = new LinkedHashMap<>();
    for (JsonNode t : toolsNode) {
        String toolName = t.asText();
        String result = toolExecutor.execute(toolName, argsJson, req);
        ctx.outputs.put(nodeId + "." + toolName, result);
        merged.put(toolName, parseJsonObject(result));
    }
    ctx.outputs.put(nodeId, objectMapper.writeValueAsString(merged));
}
```
> `args` 是**节点级**的（同一节点的所有工具共用一份 args）—— 本轮 dag 里两个工具区间相同，共用即可。若将来需要每个工具不同参数，再把 `tools` 改成对象数组。
> **节点输出是「按工具名嵌套」的结构**，不是单个工具结果的扁平形式：
> `n1` = `{"query_bank_flow":{...},"query_voucher":{...}}`。
> 下游（persist 写 `kpi_json`、健康检查 26.3 对表、前端取数）都按这个嵌套形状读 —— **三处必须一致，改一处要改三处**。

`runLlm` 改动：
- 用 `buildVars(ctx)` 替换原来的 `vars.putAll(ctx.inputParams); vars.putAll(ctx.outputs);`
- 加节点级超时：
```java
Integer timeout = nodeDef.has("stepTimeoutSeconds") ? nodeDef.path("stepTimeoutSeconds").asInt() : null;
...
.stepTimeoutSeconds(timeout)
```
- **LLM 失败是软失败**：把 `runLlm` 的调用包在 try/catch 里 —— 捕获后设 `ctx.partial = true`、`ctx.llmError = e.getMessage()`、`ctx.outputs.put(nodeId, 确定性聚合摘要)`，**继续往下走**（让 persist 还能落表）。
- 记录模型名：`ctx.llmModel = res.getModel();` —— 需要 `AgentRuntime.AgentTaskResult` 有 `model` 字段（**本 Task 就加**，见下）。

> **`AgentRuntime.AgentTaskResult` 加 `model` 字段**（本 Task 一起做，Task 5 就不用再动它）：
> `private String model;` + 在 `run()` 里 `.model(resp.getModel())`。
> **为什么现在就要**：`llm_used` 要记「这次真的调了哪个模型」（而不是笼统的 `true`），没有这个字段就记不了 —— 而 `llm_used` 正是第 26.2 条断言的依据。

- [ ] **Step 4: 实现 `DeterministicAggregator`**

```java
public final class DeterministicAggregator {
    private DeterministicAggregator() {}

    /** 银行流水确定性摘要 —— 只用工具返回里的真实数字,一个字都不多。 */
    public static String bankFlowSummary(Map<String, Object> flow, String yesterday) { ... }

    public static String voucherSummary(Map<String, Object> voucher, String yesterday) { ... }
}
```

`bankFlowSummary` 的输出格式（**逐字固定，健康检查可以按它断言**）：

```
[自动聚合·未走 LLM] {yesterday} 无资金流水；{fromDate}~{toDate} 共 {count} 笔，收 {totalIn}，支 {totalOut}。
```
- `yesterdayCount` > 0 时，第一句换成：`{yesterday} 有 {yesterdayCount} 笔，收 {yesterdayIn}，支 {yesterdayOut}；`
- 数字一律用 `BigDecimal.toPlainString()`（不带科学计数法）
- 缺字段 → 用 `0`（不是 null、不是 "null"）

- [ ] **Step 5: 跑测试确认通过**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-ai-core,ruoyi-modules/opc-agent-hub -am test -Dtest=WorkflowEngineToolNodeTest+DeterministicAggregatorTest+WorkflowEngineAttributionTest
```
Expected: PASS。

- [ ] **Step 6: 跑 agent-hub + ai-core 全量测试**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-ai-core,ruoyi-modules/opc-agent-hub -am test
```
Expected: 全绿。

- [ ] **Step 7: 提交**

```bash
git add springboot3/ruoyi-modules/opc-ai-core springboot3/ruoyi-modules/opc-agent-hub
git commit -m "$(cat <<'EOF'
feat(agent-hub): TOOL 节点真的调工具 + 注入内置日期变量

WorkflowEngine.runTool() 以前是 // TODO: 调 ToolExecutor + 塞一个 "{}",
所以工作流的 TOOL 节点产出恒为空 —— dag 里写什么工具都等于没写。

现在 runTool 真的走 ToolExecutor,归属(companyId)取自工作流定义而不是 dag,
结果同时存成 <nodeId> 和 <nodeId>.<toolName> 两种 key,prompt 里 ${n1} 拿得到。

另外补了内置日期变量 today/yesterday/weekAgo/monthAgo —— 以前 ${yesterday}
根本没有任何来源,会原样发给模型。口径显式用 Asia/Shanghai:容器 JVM 是 UTC,
LocalDate.now() 会差 8 小时。

节点级 stepTimeoutSeconds 也补上:默认 30 秒把 MiniMax 那次调用掐断过
(opc_agent_workflow_run id 38 = request timed out)。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 落表层 —— insight 的 @InnerAuth upsert + 三个新列（opc-insight + ruoyi-common + SQL）

工作流产出物要落进业务表 `opc_insight_daily_report`。这张表现在有**三个写入方**（手工生成 / 工作流 / 种子），必须能分清谁写的。

**Files:**
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/vo/DailyReportUpsertRequest.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/controller/InsightInternalController.java`
- Modify: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/service/IDailyReportService.java`
- Modify: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/service/impl/DailyReportServiceImpl.java`
- Modify: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/domain/OpcInsightDailyReport.java`
- Modify: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/vo/DailyReportVo.java`
- Modify: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/mapper/OpcInsightDailyReportMapper.java`
- Modify: `springboot3/ruoyi-modules/opc-insight/src/main/resources/mapper/OpcInsightDailyReportMapper.xml`
- Modify: `springboot3/ruoyi-common/ruoyi-common-core/src/main/java/com/ruoyi/common/core/constant/ServiceNameConstants.java`
- Create: `springboot3/sql/migrations/V20261102__finance_agent_truth.sql`（**本 Task 只写 ALTER 那部分**，Task 8 补数据部分）
- Test: `springboot3/ruoyi-modules/opc-insight/src/test/java/com/ruoyi/opc/insight/service/impl/DailyReportUpsertTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `POST /opc/insight/internal/daily/upsert`（`@InnerAuth`）→ `R<Long>`（落库后的行 id）
  - `DailyReportUpsertRequest`：`Long companyId; String period; String summaryMd; String adviceMd; String kpiJson; String llmUsed; String reportStatus; String source; Long sourceWorkflowRunId;`
  - `IDailyReportService#upsertFromWorkflow(DailyReportUpsertRequest req) -> Long`
  - `ServiceNameConstants.INSIGHT_SERVICE = "opc-insight"`
  - `opc_insight_daily_report` 新增 3 列：`report_status VARCHAR(16)`、`report_source VARCHAR(32)`、`source_workflow_run_id BIGINT`

- [ ] **Step 1: 写失败的测试**

新建 `DailyReportUpsertTest`（Mockito，mock `IKpiService` / `LlmGateway` / `OpcInsightDailyReportMapper`）：

| 测试名 | 断言 |
|---|---|
| `upsertFromWorkflow_insertsWithAgentSource` | 调 `upsertFromWorkflow` → `mapper.insert` 的对象 `reportSource` == `"AGENT_WORKFLOW"`、`reportStatus` == `"FULL"`、`sourceWorkflowRunId` == 42L、`companyId` == 2 |
| `upsertFromWorkflow_keepsKpiJsonAsGiven` | `kpiJson` 原样落库（**引擎写进来的结构化数字不被改写**） |
| `upsertFromWorkflow_companyIdNull_throws` | `companyId` null → `OpcException`，**不调 mapper** |
| `upsertFromWorkflow_periodInvalid_throws` | `period` 不是 `yyyy-MM-dd` → `OpcException` |
| `upsertFromWorkflow_periodAfterToday_throws` | period 晚于今天 → `OpcException` |
| `upsertFromWorkflow_existingRow_replacesIt` | 已存在同 `(company_id, period)` → **先删旧再插新**（沿用 `generate()` 的既有做法），最终表里只有一行 |
| `generate_manualRefusesToOverwriteAgentRow` | 库里已有 `report_source='AGENT_WORKFLOW'` 的同日行 → `generate()` 抛 `OpcException`，消息含 `"不支持手工覆盖"`；**且不调 mapper.insert**（Review Focus #3） |
| `generate_manualOverwritesManualRow` | 已有 `report_source='MANUAL'` 的同日行 → `generate()` 正常覆盖 |
| `generate_writesManualSource` | `generate()` 落库的行 `reportSource` == `"MANUAL"`、`reportStatus` == `"FULL"`（LLM 成功时） |
| `generate_llmFails_writesFallbackStatus` | LLM 抛异常 → `reportStatus` == `"FALLBACK"`、`llmUsed` == `"FALLBACK"` |

- [ ] **Step 2: 跑测试确认失败**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-insight -am test -Dtest=DailyReportUpsertTest
```
Expected: FAIL —— 编译错误（找不到 `upsertFromWorkflow` / `DailyReportUpsertRequest`）。

- [ ] **Step 3: 加三个列 + domain/VO/mapper**

迁移文件 `sql/migrations/V20261102__finance_agent_truth.sql` 的**第一段**（幂等）：

```sql
-- 三个新列：谁写的、写到哪一步、能不能从日报反查回那次运行
SET @exist := 0;
SELECT COUNT(*) INTO @exist FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opc_insight_daily_report' AND COLUMN_NAME = 'report_status';
SET @sql := IF(@exist = 0, 'ALTER TABLE `opc_insight_daily_report` ADD COLUMN `report_status` VARCHAR(16) DEFAULT NULL COMMENT ''FULL/PARTIAL/FALLBACK''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
-- report_source / source 同理
```
（照抄 `sql/migrations/V20261033__opc_scheduler_truth.sql` 里已有的幂等 ALTER 写法 —— **先照着那个文件的既有格式写，不要自创一套**。）

列名**最终定死**：

| 列 | 类型 | 取值 |
|---|---|---|
| `report_status` | `VARCHAR(16)` | `FULL` / `PARTIAL` / `FALLBACK` |
| `report_source` | `VARCHAR(32)` | `AGENT_WORKFLOW` / `MANUAL` |

> ⚠️ **列名用 `report_source`，不要用 `source`** —— `source` 是 MySQL 的关键字（复制语句里的
> `CHANGE REPLICATION SOURCE TO`），**不是保留字**，所以 `CREATE TABLE` 能过；
> 但它在 DDL/DML 里到处要加反引号，而且读代码的人第一眼会以为是「数据来源」
> 而不是「谁写的这行」。加前缀是最省事的避法。

`source_workflow_run_id BIGINT DEFAULT NULL COMMENT '产出该日报的工作流运行记录 id'`

domain / VO / mapper XML 三处同步加这 3 个字段（`OpcInsightDailyReport` + `DailyReportVo` + `BaseResultMap` + `Base_Columns` + insert + 各 select）。

- [ ] **Step 4: 实现 `InsightInternalController` + `upsertFromWorkflow`**

```java
@RestController
@RequestMapping("/opc/insight/internal")
@RequiredArgsConstructor
public class InsightInternalController {

    private final IDailyReportService dailyReportService;

    @InnerAuth
    @PostMapping("/daily/upsert")
    public R<Long> upsert(@RequestBody DailyReportUpsertRequest req) {
        return R.ok(dailyReportService.upsertFromWorkflow(req));
    }

    /** 错误体永远是 R<T> —— Feign 侧声明的是 R<Map<String,Object>>,解析 AjaxResult 会炸。 */
    @ExceptionHandler(OpcException.class)
    public R<Void> handleOpcException(OpcException e) {
        return R.fail(e.getCode().intValue(), e.getMessage());
    }
}
```
> **不能**直接把 `@InnerAuth` 端点加到 `DailyReportController` 上：它其它端点用 `SecurityUtils.getCompanyId()`，而 InnerAuth 调用**没有登录态** → companyId 只能从 body 取。

`DailyReportServiceImpl` 加：

```java
@Override
public Long upsertFromWorkflow(DailyReportUpsertRequest req) {
    // 校验 companyId / period（格式 + 不晚于今天）
    // 查现有行：mapper.selectByCompanyAndDate(companyId, period)
    //   存在 → deleteByPrimaryKey + insert（snapshot 语义,不留历史）
    //   不存在 → insert
    // 返回落库后的 id
}
```

`generate()` 改动：
- 落库对象加 `reportSource = "MANUAL"`、`reportStatus = outcome.llmUsed.equals("FALLBACK") ? "FALLBACK" : (kpi.isPartial() ? "PARTIAL" : "FULL")`
- **冲突拒绝**：在 `mapper.selectByCompanyAndDate` 查到 `exist` 之后，若 `exist` 的 `reportSource` 是 `"AGENT_WORKFLOW"` → `throw new OpcException("该日期日报已由数字员工产出，不支持手工覆盖: " + period)`

> `DailyReportVo` 现在**没有** `reportSource` 字段 → 要加，否则 `generate()` 读不到现有行的来源。VO 的 select 也要带上这列。

- [ ] **Step 5: 加 `ServiceNameConstants.INSIGHT_SERVICE`**

```java
/** OPC 数据洞察服务（数字员工的工作流产出物落在这里） */
public static final String INSIGHT_SERVICE = "opc-insight";
```

- [ ] **Step 6: 跑测试确认通过**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-insight -am test -Dtest=DailyReportUpsertTest+DailyReportServiceImplTest
```
Expected: PASS。

> `DailyReportServiceImplTest` 里既有用例可能会因为多了 3 个字段而需要调整 —— **不要改断言去迁就实现**。既有断言应该仍然成立（新字段是 null 或 "MANUAL"，不影响老断言）。

- [ ] **Step 7: 跑 opc-insight 全量测试 + install 共享库**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-common/ruoyi-common-core -am install
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-insight -am test
```
Expected: 全绿。**`ruoyi-common-core` 改了 → 必须 `install`**（CLAUDE.md 坑 #1）。

- [ ] **Step 8: 提交**

```bash
git add springboot3/ruoyi-modules/opc-insight springboot3/ruoyi-common/ruoyi-common-core springboot3/sql/migrations/V20261102__finance_agent_truth.sql
git commit -m "$(cat <<'EOF'
feat(insight): 日报表加来源与状态,加 @InnerAuth upsert 端点

opc_insight_daily_report 现在有三个写入方:手工生成(公开接口)、工作流
(本轮新增)、种子。三个都往同一张表写同一个 (company_id, period),
互相覆盖 —— 谁写的根本分不清。

新增 report_status(FULL/PARTIAL/FALLBACK)、report_source
(AGENT_WORKFLOW/MANUAL)、source_workflow_run_id 三列,数字员工产出的
日报可以被反查回是哪次运行做的。

手工生成撞上数字员工的行 → 拒绝覆盖并说明原因。数字员工覆盖手工行允许。

端点单独放在 InsightInternalController:controller 级 @ExceptionHandler 保证
响应体永远是 R<T>,Feign 侧声明的 R<Map<String,Object>> 才解析得了;
而 DailyReportController 其它端点靠登录态取 companyId,InnerAuth 调用没有
登录态,不能混在一起。

列名用 report_source 而不是 source —— source 是 MySQL 的关键字,到处要加反引号,
而且读代码的人会以为是「数据来源」而不是「谁写的这行」。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: 工作流 persist —— 产出物真的落表（opc-agent-hub）

引擎跑完后执行 dag 里的 `persist` 声明，把产出物交给 insight 落表。

**Files:**
- Create: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/feign/RemoteInsightService.java`
- Create: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/workflow/WorkflowPersistHandler.java`
- Modify: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/workflow/WorkflowEngine.java`
- Modify: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/service/impl/WorkflowTriggerServiceImpl.java`
- Modify: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/service/impl/WorkflowTriggerServiceImpl.java`（run 状态）
- Modify: `springboot3/ruoyi-modules/opc-agent-hub/src/main/java/com/ruoyi/opc/agent/domain/OpcAgentWorkflowRun.java`（若需要）
- Test: `springboot3/ruoyi-modules/opc-agent-hub/src/test/java/com/ruoyi/opc/agent/workflow/WorkflowPersistHandlerTest.java`
- Test: `springboot3/ruoyi-modules/opc-agent-hub/src/test/java/com/ruoyi/opc/agent/service/impl/WorkflowTriggerServiceImplPersistTest.java`

**Interfaces:**
- Consumes: Task 4 的 `POST /opc/insight/internal/daily/upsert`；Task 3 的 `WorkflowContext` / `DeterministicAggregator`
- Produces:
  - `RemoteInsightService`：`R<Map<String,Object>> upsertDailyReport(@RequestBody Map<String,Object> body)`
  - `WorkflowPersistHandler#persist(JsonNode persistNode, WorkflowContext ctx) -> PersistOutcome`
  - `WorkflowEngine` 在节点循环结束后调用 persist；`WorkflowResult` 的 `partial` 字段反映降级
  - `WorkflowTriggerServiceImpl`：`run.status` ∈ {`SUCCESS`, `PARTIAL`, `FAILED`}
  - `OpcConstants.TASK_PARTIAL = "PARTIAL"`

- [ ] **Step 1: 写失败的测试**

新建 `WorkflowPersistHandlerTest`（Mockito mock `RemoteInsightService`）：

| 测试名 | 断言 |
|---|---|
| `persist_sendsKpiAndSummaryFromNodes` | `persist` 声明 `{"target":"daily_report","date":"${yesterday}","kpiFrom":"n1","summaryFrom":"n2"}` → 调 insight 的 body 里 `kpiJson` == n1 的 JSON、`summaryMd` == n2 的内容、`companyId` == ctx.companyId、`reportSource` == `"AGENT_WORKFLOW"`、`sourceWorkflowRunId` == ctx.runId |
| `persist_writesFallbackStatusWhenLlmFailed` | ctx.partial == true（LLM 挂了）→ body 的 `reportStatus` == `"PARTIAL"`、`llmUsed` == `"PARTIAL"`（**不是** `"FALLBACK"` —— FALLBACK 专指「LLM 完全没参与」的固定模板），`summaryMd` == 确定性聚合摘要（不是空） |
| `persist_unknownTarget_throws` | `target` 不是 `daily_report` → `OpcException` |
| `persist_missingKpiNode_throws` | `kpiFrom` 指向不存在的节点 → `OpcException` |
| `persist_remoteFailure_throws` | insight 返回 `R.fail` → 抛 `OpcException`（**产出物没落表 = 这件事没做成**，不能让 run 记成功） |
| `persist_noPersistDeclaration_isNoop` | dag 里没有 `persist` → 不调 insight |

新建 `WorkflowTriggerServiceImplPersistTest`：

| 测试名 | 断言 |
|---|---|
| `trigger_persistFails_marksRunFailed` | persist 失败 → `run.getStatus()` == `"FAILED"`、`errorMessage` 含 persist 的错误 |
| `trigger_llmFailedButDataFetched_marksRunPartial` | LLM 节点失败、TOOL 节点成功、persist 成功 → `run.getStatus()` == `"PARTIAL"` |
| `trigger_allGood_marksRunSuccess` | 全成功 → `"SUCCESS"` |

- [ ] **Step 2: 跑测试确认失败**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-agent-hub -am test -Dtest=WorkflowPersistHandlerTest+WorkflowTriggerServiceImplPersistTest
```
Expected: FAIL —— 编译错误。

- [ ] **Step 3: 实现 `RemoteInsightService`**

```java
@FeignClient(contextId = "remoteInsightService", value = ServiceNameConstants.INSIGHT_SERVICE)
public interface RemoteInsightService {
    @PostMapping("/opc/insight/internal/daily/upsert")
    R<Map<String, Object>> upsertDailyReport(@RequestBody Map<String, Object> body);
}
```
> `R<Map<String,Object>>` —— opc-agent-hub **不依赖** opc-insight（不能为了一个 VO 引入依赖，会成环）。

> ⚠️ **agent-hub 的 `@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})`** → 新 client 在 `com.ruoyi.opc.agent.feign` 包下，**会被自动扫到**，不需要改启动类。

- [ ] **Step 4: 实现 `WorkflowPersistHandler`**

```java
@Slf4j
@Component
public class WorkflowPersistHandler {

    private final RemoteInsightService insightService;

    /** 支持的落表目标 —— 目前只有日报。 */
    private static final String TARGET_DAILY_REPORT = "daily_report";

    public PersistOutcome persist(JsonNode persistNode, WorkflowContext ctx) { ... }
}
```

`persist` 的逻辑：
1. `target` 必须 == `"daily_report"`，否则 `OpcException("不支持的 persist 目标: " + target)`
2. `date` = `renderTemplate(persistNode.path("date").asText(""), ctx)` → 昨天
3. `kpiFrom` 指向的节点输出必须在 `ctx.outputs` 里，否则 `OpcException("persist 声明的 kpi 节点没有产出: " + kpiFrom)`
4. `summaryFrom` 同理
5. body：
```java
Map<String, Object> body = new HashMap<>();
body.put("companyId", ctx.companyId);
body.put("period", date);
body.put("summaryMd", summary);
body.put("adviceMd", null);              // 本轮没有独立的建议节点
body.put("kpiJson", kpiJson);           // n1 的原始 JSON —— 结构化数字的来源
body.put("llmUsed", ctx.partial ? "PARTIAL" : ctx.llmModel);
body.put("reportStatus", ctx.partial ? "PARTIAL" : "FULL");
body.put("reportSource", "AGENT_WORKFLOW");
body.put("sourceWorkflowRunId", ctx.runId);
```
6. `R` 的 `code != 200` → `throw new OpcException("日报落表失败: " + r.getMsg())`
7. 返回 `PersistOutcome(ok=true, rowId=...)`

**`kpi_json` 的确切形状**（落库的就是它，第 26.3 条断言与前端都按这个读）：

```json
{
  "query_bank_flow": {
    "source": "opc_finance_bank_flow", "companyId": 2,
    "fromDate": "2026-10-07", "toDate": "2026-11-01",
    "count": 6, "totalIn": 51892.00, "totalOut": 32430.00, "net": 19462.00,
    "yesterdayCount": 0, "yesterdayIn": 0.00, "yesterdayOut": 0.00,
    "items": [ { "tradeTime": "2026-10-07 12:49:03", "direction": "OUT", "amount": 13200.00, ... } ]
  },
  "query_voucher": {
    "source": "opc_finance_voucher", "companyId": 2,
    "fromDate": "2026-10-07", "toDate": "2026-11-01",
    "count": 5, "totalIn": 35400.00, "totalOut": 29750.00, "items": [ ... ]
  }
}
```

> **`kpi_json` 是 n1 的原始 JSON，一字不改** —— 引擎不重算、不改口径、不裁剪。数字的来源只有一处（工具的返回值），任何加工都会让 26.3 的对账失真。
> **`items` 里必须带每一笔的金额** —— 26.3 逐笔对表靠的就是它。
> insight 自己写的那份日报（`generate()`）的 `kpi_json` 是 `KpiSnapshot` 的形状（`totalRevenue`/`totalExpense`/`voucherCount`），**与这个不同**。前端要同时认这两种（见 Task 9），健康检查只对 `report_source='AGENT_WORKFLOW'` 的行做对账。

- [ ] **Step 5: 引擎接上 persist**

`WorkflowEngine.run(...)` 的节点循环**之后**（`finally` 之前）：

```java
JsonNode persistNode = dag.get("persist");
if (persistNode != null && !persistNode.isNull()) {
    persistHandler.persist(persistNode, ctx);
}
```
> persist 抛异常 → 冒泡到 `run()` 的 catch → `success=false` → run 记 FAILED。**这是有意的**：产出物没落表 = 这件事没做成。

`WorkflowContext` 加 `Long runId`（由 `WorkflowDefinition.runId` 带进来）。

`WorkflowTriggerServiceImpl`：
```java
def.setRunId(run.getId());
...
run.setStatus(result.isSuccess()
        ? (result.isPartial() ? OpcConstants.TASK_PARTIAL : OpcConstants.TASK_SUCCESS)
        : OpcConstants.TASK_FAILED);
```
`OpcConstants` 加 `public static final String TASK_PARTIAL = "PARTIAL";`（opc-common）。

- [ ] **Step 6: 跑测试确认通过**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-agent-hub -am test -Dtest=WorkflowPersistHandlerTest+WorkflowTriggerServiceImplPersistTest+WorkflowTriggerServiceImplTest
```
Expected: PASS。

- [ ] **Step 7: 跑全量 + install opc-common**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-common/opc-common -am install
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-agent-hub -am test
```
Expected: 全绿。

- [ ] **Step 8: 提交**

```bash
git add springboot3/ruoyi-modules/opc-agent-hub springboot3/ruoyi-common/opc-common
git commit -m "$(cat <<'EOF'
feat(agent-hub): 工作流跑完把产出物落进 opc_insight_daily_report

以前工作流产出只躺在 opc_agent_workflow_run.output_result 里 —— 那是运行日志,
不是业务表。业务上「今天的日报」在 opc_insight_daily_report 里查,那张表跟
工作流没有任何关系。

现在 dag 可以声明 persist,引擎在节点跑完后确定性执行:取 kpiFrom 节点的
工具结果当 kpi_json(结构化数字),取 summaryFrom 节点的散文当 summary_md,
带上 report_status / report_source / source_workflow_run_id 调 insight 的
@InnerAuth upsert。落表是确定性动作,不交给模型决定。

persist 失败 → 整条 run 记 FAILED。产出物没落表 = 这件事没做成,不能记成功。

新增 PARTIAL 状态:TOOL 取数成功、LLM 写文案失败时,真实数据照样落表,
report_status=PARTIAL,摘要用引擎侧的确定性聚合(只用工具返回里的真实数字)。
以前这种情形整条链报废,已经取到的真实数据被一起丢掉。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 供给层 —— LLM 重试 + DeepSeek 降级链（opc-ai-core + deploy）

MiniMax 的 Token Plan 在 10-30 / 10-31 / 11-01 连续三天 429，11-02 又 30 秒超时。**没有备胎、没有重试**。

**Files:**
- Create: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/gateway/llm/LlmHttpException.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/gateway/llm/ChatResponse.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/gateway/llm/HttpLlmClient.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/gateway/llm/DeepSeekProvider.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/gateway/llm/MiniMaxProvider.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/gateway/llm/LlmGateway.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/java/com/ruoyi/opc/ai/security/PromptGuard.java`
- Modify: `springboot3/ruoyi-modules/opc-ai-core/src/main/resources/application.yml`
- Modify: `springboot3/ruoyi-modules/opc-agent-hub/src/main/resources/application.yml`
- Modify: `springboot3/ruoyi-modules/opc-insight/src/main/resources/application.yml`
- Modify: `springboot3/deploy/nacos/application-dev.yml`
- Modify: `springboot3/deploy/docker-compose.yml`
- Modify: `springboot3/deploy/.env.example`
- Test: `springboot3/ruoyi-modules/opc-ai-core/src/test/java/com/ruoyi/opc/ai/gateway/llm/LlmGatewayRetryTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `LlmHttpException(int statusCode, String message)` —— 带 HTTP 状态码
  - `ChatResponse` 新增 `Integer httpStatus`
  - `LlmGateway` 新增 4 参构造器（`maxRetries`），3 参构造器保留（默认 3）
  - `opc.llm.max-retries` 的语义被真正接线：**每个 provider 最多尝试这么多次**
  - `PromptGuard.ALLOWED_TOOLS` 更新：删 `extract_voucher` / `create_voucher` / `query_daily_report`，加 `query_bank_flow` / `query_voucher`
  - `opc.llm.deepseek.enabled` 在 4 个地方统一为 `${OPC_LLM_DEEPSEEK_ENABLED:false}`

- [ ] **Step 1: 写失败的测试 —— 重试语义**

新建 `LlmGatewayRetryTest`（Mockito）：

| 测试名 | 断言 |
|---|---|
| `chat_retriesOnTimeout` | provider 第一次抛 `HttpTimeoutException`、第二次成功 → 返回成功；provider 被调 2 次 |
| `chat_doesNotRetryOn429` | provider 返回 `httpStatus=429` 的失败响应 → **只调 1 次**，直接换下一个 provider（429 是配额问题，重试只会再撞一次） |
| `chat_retriesOn5xx` | `httpStatus=503` → 调满 `maxRetries` 次后才换 provider |
| `chat_movesToNextProviderAfterRetriesExhausted` | 第一个 provider 全部失败 → 第二个 provider 被调用 |
| `chat_allProvidersFail_throwsWithLastError` | 全失败 → 抛 `OpcException`，消息含最后一个错误的 provider 名与原因 |
| `chat_doesNotRetryOn4xxOtherThan429` | `httpStatus=401` → 只调 1 次 |

- [ ] **Step 2: 跑测试确认失败**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-ai-core -am test -Dtest=LlmGatewayRetryTest
```
Expected: FAIL —— 编译错误（找不到 `httpStatus` / `LlmHttpException`）。

- [ ] **Step 3: `LlmHttpException` + `ChatResponse.httpStatus` + `HttpLlmClient`**

```java
package com.ruoyi.opc.ai.gateway.llm;

/** LLM 厂商返回非 2xx —— 带状态码,让上层能判断「该不该重试」。 */
public class LlmHttpException extends RuntimeException {
    private final int statusCode;
    public LlmHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
    public int getStatusCode() { return statusCode; }
}
```

`HttpLlmClient.callChatCompletions`：
```java
if (resp.statusCode() / 100 != 2) {
    throw new LlmHttpException(resp.statusCode(),
            "HTTP " + resp.statusCode() + " from " + baseUrl + ": " + resp.body());
}
```
并把 catch 改成**先原样抛 `LlmHttpException`**：
```java
} catch (LlmHttpException e) {
    throw e;
} catch (Exception e) {
    throw new RuntimeException("LLM HTTP call failed: " + e.getMessage(), e);
}
```

`ChatResponse` 加 `private Integer httpStatus;`（`@Data @Builder` → 自动有 getter/setter）。

`DeepSeekProvider` / `MiniMaxProvider`：
```java
} catch (LlmHttpException e) {
    log.error("[DeepSeek] 调用失败 httpStatus={}", e.getStatusCode(), e);
    return ChatResponse.builder()
            .model(modelName).success(false)
            .httpStatus(e.getStatusCode())
            .errorMessage("DeepSeek 调用失败(HTTP " + e.getStatusCode() + "): " + e.getMessage())
            .latencyMs(System.currentTimeMillis() - start)
            .build();
} catch (Exception e) {
    // 网络错 / 超时 —— httpStatus 留 null,上层按「可重试」处理
    ...
}
```

- [ ] **Step 4: `LlmGateway` 实现重试**

```java
public static final int DEFAULT_MAX_RETRIES = 3;

private final List<ChatModelProvider> providers;
private final TokenMeter tokenMeter;
private final LlmCache cache;
private final int maxRetries;

public LlmGateway(List<ChatModelProvider> providers, TokenMeter tokenMeter, LlmCache cache) {
    this(providers, tokenMeter, cache, DEFAULT_MAX_RETRIES);
}

@Autowired
public LlmGateway(List<ChatModelProvider> providers, TokenMeter tokenMeter, LlmCache cache,
                  @Value("${opc.llm.max-retries:3}") int maxRetries) {
    this.providers = providers;
    this.tokenMeter = tokenMeter;
    this.cache = cache;
    this.maxRetries = Math.max(1, maxRetries);
}
```
（`@RequiredArgsConstructor` 删掉，改显式构造器。既有测试用 `new LlmGateway(providers, tokenMeter, cache)` —— **3 参构造器保留，既有测试不用改**。）

重试循环：
```java
for (ChatModelProvider provider : ordered) {
    if (!provider.enabled()) continue;
    ChatResponse lastError = null;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            long start = System.currentTimeMillis();
            ChatResponse resp = provider.chat(messages, options);
            resp.setLatencyMs(System.currentTimeMillis() - start);
            if (Boolean.TRUE.equals(resp.getSuccess())) {
                if (ctx != null) tokenMeter.record(ctx, provider.model(), resp);
                cache.put(cacheKey, resp, OpcConstants.PRICE_DEEPSEEK_OUTPUT);
                return resp;
            }
            lastError = resp;
            if (!isRetryable(resp)) break;     // 429 / 4xx → 不重试,换下一个 provider
            log.warn("[LLM] provider={} 第 {} 次失败：{}", provider.name(), attempt, resp.getErrorMessage());
        } catch (Exception e) {
            lastError = ChatResponse.builder().model(provider.model()).success(false)
                    .errorMessage(e.getMessage()).build();
            log.warn("[LLM] provider={} 第 {} 次异常：{}", provider.name(), attempt, e.getMessage());
        }
    }
    ...
}

/** 429 与 4xx 不重试 —— 重试同样会撞,只是白等。5xx 与网络错才值得再试。 */
private boolean isRetryable(ChatResponse resp) {
    Integer status = resp.getHttpStatus();
    if (status == null) return true;      // 超时 / 连接错
    if (status == 429) return false;
    return status >= 500;
}
```

> 缓存命中路径**不变**（仍然计量、cost=0）。

- [ ] **Step 5: `PromptGuard` 白名单同步**

```java
private static final List<String> ALLOWED_TOOLS = List.of(
        "query_voucher", "query_bank_flow", "query_balance",
        "search_product", "query_inventory",
        "query_customer", "create_follow_up",
        "parse_resume", "schedule_interview");
```
删掉 `extract_voucher` / `create_voucher` / `query_daily_report`（**这三个工具本轮已下线，留着白名单等于说它们还能被调**）。

- [ ] **Step 6: 供给侧配置（4 个 yml + compose + .env）**

**四个 yml 的 `opc.llm.deepseek.enabled` 统一改成**：
```yaml
enabled: ${OPC_LLM_DEEPSEEK_ENABLED:false}
```
改的 4 处：
1. `opc-ai-core/src/main/resources/application.yml`（现在是写死的 `false`）
2. `opc-agent-hub/src/main/resources/application.yml`（同上）
3. `deploy/nacos/application-dev.yml`（同上）
4. `opc-insight/src/main/resources/application.yml` —— **这个文件现在完全没有 `opc.llm` 段**，要**新增一整段**，与 Nacos 那份**逐条一致**（primary / fallback / enable-cache / cache-ttl / max-retries / timeout-seconds / deepseek / minimax / openai）

> 仓库的规矩是「随 jar 走的本地 application.yml 是真相源，Nacos 那份是可运维副本」，两份必须逐条一致（见 `deploy/nacos/application-dev.yml` 的文件头）。用**占位符**而不是写死 `true`：开关在 compose 的环境变量里 → 改开关 = 改 compose + 重启容器，**不用重建镜像**。

**compose 改动**（`springboot3/deploy/docker-compose.yml`）：
- `aiopc-agent-hub`：`DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-sk-placeholder}` → `${DEEPSEEK_API_KEY:-}`
- `aiopc-ai-core`：同上
- `aiopc-insight`：**新增** `DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}`、`MINIMAX_API_KEY: ${MINIMAX_API_KEY:-}`
- 三个服务都新增：`OPC_LLM_DEEPSEEK_ENABLED: "true"`

> **去掉 `sk-placeholder` 默认值**：key 没配就该是 disabled（`enabled() = enabled && apiKey非空`），而不是拿一个假 key 去撞 401。
> **insight 之前一个 key 都没有** → 它的 12 条产出全是 `FALLBACK`，根因就在这。

**`.env.example`**：加一行说明（**只写键名，不写值**）：
```
# 第二个 LLM provider（降级用）。留空则只有 MiniMax 一条腿，429 时整条链报废
DEEPSEEK_API_KEY=
# 是否启用 DeepSeek 降级（true/false）
OPC_LLM_DEEPSEEK_ENABLED=true
```
> **`.env` 里的真实 key 由用户自己填** —— 实现阶段提醒用户，**不要替他写、不要打印值**。

- [ ] **Step 7: 跑测试确认通过**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-ai-core -am test
```
Expected: 全绿（既有 `LlmGatewayProviderPriorityTest` / `LlmGatewayCacheMeteringTest` 用 3 参构造器，**不用改**）。

- [ ] **Step 8: 提交**

```bash
git add springboot3/ruoyi-modules/opc-ai-core springboot3/ruoyi-modules/opc-agent-hub springboot3/ruoyi-modules/opc-insight springboot3/deploy
git commit -m "$(cat <<'EOF'
feat(llm): 真的重试 + 打开 DeepSeek 降级链

max-retries: 3 这个键在 7 个 yml 里写着,全仓没有一行 Java 读它 ——
重试数实际是 0。MiniMax 的 Token Plan 10-30/10-31/11-01 连续三天 429,
11-02 又被 30 秒超时掐断,一次失败整条链报废。

现在 LlmGateway 按 max-retries 重试每个 provider,但 429 和 4xx 不重试
(配额问题重试只会再撞一次),5xx 和网络错才再试。HTTP 状态码靠新的
LlmHttpException 带出来,不靠解析错误消息里的字符串。

降级链本来是现成的,只是没打开:deepseek.enabled=false 硬编码在三个地方,
而 compose 的默认值是 sk-placeholder —— 填了 enabled:true 也会拿假 key 撞 401。
现在四处统一成 ${OPC_LLM_DEEPSEEK_ENABLED:false} 占位符,开关在 compose 的
环境变量里,改开关不用重建镜像。

opc-insight 之前一个 LLM key 都没有,12 条产出全是 FALLBACK —— 界面上写着
「AI 数据洞察」,实际一条都不是 AI 生成的。这次给它补上 key 与 opc.llm 段。

PromptGuard 白名单同步:删掉本轮下线的三个假工具,加 query_bank_flow。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: 调度层 —— 分清「定时」和「手动」（ruoyi-job）

RuoYi 的「执行一次」走 `scheduler.triggerJob(jobKey, dataMap)` —— **同一个 CronTrigger、同一个 invoke_target、同一个 triggerSource**。所以健康检查自己触发的记录（run id 34）被记成了 `quartz:...`，第 26 节的「定时器跑成了」断言会被**健康检查自己喂绿**。

**Files:**
- Modify: `springboot3/ruoyi-modules/ruoyi-job/src/main/java/com/ruoyi/job/task/WorkflowCronJob.java`
- Modify: `springboot3/ruoyi-modules/ruoyi-job/src/main/java/com/ruoyi/job/util/QuartzJobExecution.java`
- Modify: `springboot3/ruoyi-modules/ruoyi-job/src/main/java/com/ruoyi/job/service/SysJobServiceImpl.java`
- Create: `springboot3/ruoyi-modules/ruoyi-job/src/main/java/com/ruoyi/job/util/ManualTriggerMarker.java`
- Test: `springboot3/ruoyi-modules/ruoyi-job/src/test/java/com/ruoyi/job/task/WorkflowCronJobTriggerSourceTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `WorkflowCronJob` 的触发源：`quartz:<code>`（定时） / `manual:<code>`（后台「执行一次」）
  - `ManualTriggerMarker`：`static ThreadLocal<Boolean> MANUAL` + `markManual()` / `clear()` / `isManual()`

- [ ] **Step 1: 写失败的测试**

新建 `WorkflowCronJobTriggerSourceTest`（Mockito mock `RemoteWorkflowService`）：

| 测试名 | 断言 |
|---|---|
| `execute_scheduledTrigger_usesQuartzPrefix` | 默认（无 manual 标记）→ 传给 Feign 的 `triggerSource` == `"quartz:finance_daily_report_v1"` |
| `execute_manualTrigger_usesManualPrefix` | `ManualTriggerMarker.markManual()` 后调用 → `triggerSource` == `"manual:finance_daily_report_v1"` |
| `execute_clearsMarkerAfterRun` | 调用结束后 `ManualTriggerMarker.isManual()` == false（ThreadLocal 不泄漏到下一个任务） |
| `execute_businessFailure_throws` | Feign 返回 `R.ok` 但 `data.status == "FAILED"` → 抛异常（**业务失败不能只记日志**） |

> `WorkflowCronJob` 现在只判 `result.getCode() != R.SUCCESS`（那是「Feign 受理成功」），响应体里**已经带了 `status`** —— 业务失败必须被看见。

- [ ] **Step 2: 跑测试确认失败**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/ruoyi-job -am test -Dtest=WorkflowCronJobTriggerSourceTest
```
Expected: FAIL —— 编译错误（找不到 `ManualTriggerMarker`）。

- [ ] **Step 3: 实现 `ManualTriggerMarker`**

```java
package com.ruoyi.job.util;

/**
 * 「后台点执行一次」的标记。
 *
 * <p>RuoYi 的手动执行走 scheduler.triggerJob(jobKey, dataMap) —— 同一个
 * CronTrigger、同一个 invoke_target，跟定时触发在 Quartz 侧完全一样。
 * 只有在 JobDataMap 里额外塞一个标记才能区分开。
 */
public final class ManualTriggerMarker {
    private static final ThreadLocal<Boolean> MANUAL = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private ManualTriggerMarker() {}
    public static void markManual() { MANUAL.set(Boolean.TRUE); }
    public static void clear() { MANUAL.remove(); }
    public static boolean isManual() { return Boolean.TRUE.equals(MANUAL.get()); }
}
```

- [ ] **Step 4: 三处接线**

`SysJobServiceImpl.run(SysJob job)`（手动执行）：在 `dataMap` 里放标记
```java
JobDataMap dataMap = new JobDataMap();
dataMap.put(ScheduleConstants.TASK_PROPERTIES, properties);
dataMap.put(ManualTriggerMarker.KEY, Boolean.TRUE);   // 新增
```

`QuartzJobExecution.doExecute(context, sysJob)`：
```java
@Override
protected void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception {
    boolean manual = context.getMergedJobDataMap().containsKey(ManualTriggerMarker.KEY);
    if (manual) ManualTriggerMarker.markManual();
    try {
        JobInvokeUtil.invokeMethod(sysJob);
    } finally {
        ManualTriggerMarker.clear();
    }
}
```
> `ManualTriggerMarker.KEY` = `"opc_manual_trigger"`（常量放 `ManualTriggerMarker` 里）。

`WorkflowCronJob.execute(code)`：
```java
String source = ManualTriggerMarker.isManual()
        ? "manual:" + workflowCode
        : "quartz:" + workflowCode;
R<Map<String, Object>> result = remoteWorkflowService.trigger(workflowCode, source, SecurityConstants.INNER);
if (result == null || result.getCode() != R.SUCCESS) {
    throw new IllegalStateException("触发工作流失败 code=" + workflowCode + ", msg=" + ...);
}
Map<String, Object> data = result.getData();
String status = data == null ? null : String.valueOf(data.get("status"));
if (status != null && !"SUCCESS".equals(status)) {
    throw new IllegalStateException("工作流业务失败 code=" + workflowCode
            + ", status=" + status + ", msg=" + (data == null ? "" : data.get("errorMessage")));
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/ruoyi-job -am test -Dtest=WorkflowCronJobTriggerSourceTest
```
Expected: PASS。

- [ ] **Step 6: 跑 ruoyi-job 全量测试**

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/ruoyi-job -am test
```
Expected: 全绿。

- [ ] **Step 7: 提交**

```bash
git add springboot3/ruoyi-modules/ruoyi-job
git commit -m "$(cat <<'EOF'
fix(job): 分清「定时触发」和「后台执行一次」

RuoYi 的「立即运行」走 scheduler.triggerJob(jobKey, dataMap) —— 同一个
CronTrigger、同一个 invoke_target,在 Quartz 侧和定时触发完全没法区分。
后果是健康检查第 25 节自己触发的那条运行记录被记成了 quartz:...,
第 26 节「定时器真的跑成了」的断言会被健康检查自己喂绿。

现在手动执行在 JobDataMap 里放一个标记,QuartzJobExecution 读出来放进
ThreadLocal,WorkflowCronJob 据此决定 triggerSource 是 quartz: 还是 manual:。

另外 WorkflowCronJob 以前只判 R.code==200 —— 那是「Feign 受理成功」,
业务失败只写一行日志就被当成成功。响应体里本来就带 status,现在业务失败
直接抛异常,让 sys_job_log 记成失败。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: 数据层 —— 迁移 + 种子（sql）

**Files:**
- Modify: `springboot3/sql/migrations/V20261102__finance_agent_truth.sql`（补本 Task 的数据部分）
- Modify: `springboot3/sql/seed/opc_agent_workflow_seed.sql`
- Modify: `springboot3/sql/seed/opc_demo_ecommerce_seed.sql`
- Modify: `springboot3/deploy/mysql-initdb.d/92-opc-agent-workflow-seed.sql`
- Modify: `springboot3/deploy/mysql-initdb.d/93-sys-job-workflow-seed.sql`
- Modify: `springboot3/deploy/mysql-initdb.d/07-opc-insight-schema.sql`

**Interfaces:**
- Consumes: Task 3/4/5 定下的 dag 结构与列名
- Produces: 数据库里的 `finance_daily_report_v1` 工作流 = 新 dag + `company_id=2` + `cron='0 0 1 * * ?'`；`opc_insight_daily_report` 里**没有**那 7 行编的日报

- [ ] **Step 1: 写迁移的数据部分**

`sql/migrations/V20261102__finance_agent_truth.sql` 追加（**幂等**）：

```sql
-- 1) 工作流改挂 company 2
--    全部财务数据都在 company 2(opc_finance_bank_flow / opc_finance_voucher 各 6/5 行,
--    company 1 是 0 行),而工作流挂在 company 1 → 工具恒查 company 1 → 永远 0 行 →
--    「如实说无流水」分不出「链路是真的」和「工具完全坏掉」。
UPDATE `opc_agent_workflow` SET `company_id` = 2
 WHERE `workflow_code` = 'finance_daily_report_v1' AND `company_id` <> 2;

-- 2) dag 换成 TOOL → LLM → persist
UPDATE `opc_agent_workflow`
   SET `dag_json` = '{...新的 dag...}'
 WHERE `workflow_code` = 'finance_daily_report_v1';

-- 3) cron 改成北京时间 09:00
--    容器时区是 UTC,`0 0 9 * * ?` 实际是北京 17:00 —— README 说「每天 09:00」是假的。
UPDATE `opc_agent_workflow` SET `cron_expression` = '0 0 1 * * ?'
 WHERE `workflow_code` = 'finance_daily_report_v1';

-- 4) sys_job 的 cron 同样改
UPDATE `sys_job` SET `cron_expression` = '0 0 1 * * ?'
 WHERE `invoke_target` = 'workflowCronJob.execute(''finance_daily_report_v1'')';

-- 5) 删掉 7 行种子日报 —— 那些数字是编的(9800/19600/6200/4200/3100/3580/890),
--    跟真实流水(3582/7080/9860/12150/13200/38450)一条都对不上。
DELETE FROM `opc_insight_daily_report`
 WHERE `report_source` IS NULL            -- 种子行没有来源标记
   AND `create_by` = 'system'
   AND `llm_used` = 'FALLBACK'
   AND `kpi_json` LIKE '%"totalRevenue":9800%';
```
> **第 5 条的删除条件必须精确**：只删那 7 行种子（用 `kpi_json` 的特征值 + `report_source IS NULL` 双重限定），**不要按日期范围删** —— 真实跑出来的日报也在同一张表里。

**新的 dag_json**（**逐字照抄**，注意 JSON 转义）：

```json
{"nodes":[
 {"id":"n1","type":"TOOL",
  "tools":["query_bank_flow","query_voucher"],
  "args":{},
  "label":"取真实流水与凭证"},
 {"id":"n2","type":"LLM",
  "stepTimeoutSeconds":60,
  "prompt":"你是企业财务助理。下面是系统从业务表里查出来的真实数据（JSON）。请据此写一份面向管理者的财务日报，包含【关键数字】【风险提示】【今日建议】三个小节。\n\n硬性要求：1) 所有数字必须直接引用给定数据里的值，不得推算或补充；2) 如果数据里昨日（${yesterday}）没有流水，必须明确写「昨日无资金流水」，不得写成「营收 0 元，运营平稳」；3) 不得出现给定数据里没有的数字。\n\n真实数据：\n${n1}"}
],
 "edges":[{"source":"n1","target":"n2"}],
 "persist":{"target":"daily_report","date":"${yesterday}","kpiFrom":"n1","summaryFrom":"n2"}}
```

> `args` 留空 `{}` —— 区间由工具自己算（缺省 = 该公司最早一条 ~ 昨天）。**不传 `fromDate`** 是刻意的：固定 30 天窗口过了 2026-11-21 就空了，日报会从「6 笔真流水」变成「0 笔」，对账断言会空转。

- [ ] **Step 2: 同步三个种子文件**

`springboot3/sql/seed/opc_agent_workflow_seed.sql` 与 `springboot3/deploy/mysql-initdb.d/92-opc-agent-workflow-seed.sql`（两份保持一致）：把 `company_id` 的 `1` 改成 `2`、dag_json 换成新的、`cron_expression` 换成 `'0 0 1 * * ?'`、`trigger_config` 里的 cron 同步改。

`springboot3/sql/seed/opc_demo_ecommerce_seed.sql`：**删掉那 7 行 `INSERT INTO opc_insight_daily_report` 的整段 VALUES**（从 `INSERT INTO \`opc_insight_daily_report\`` 到那个 VALUES 块结束），并把文件末尾统计报表里引用 `opc_insight_daily_report` 的那一行改成对真实数据的统计（或删掉那一列）。

> **种子文件里没有 `deploy/mysql-initdb.d` 的副本** —— 电商种子不在 initdb.d 里（它依赖手工注册的 company 2 / user 101）。`07-opc-insight-schema.sql` 要补上**三个新列**（`report_status` / `report_source` / `source_workflow_run_id`），否则新环境建出来的表没有这三列。

- [ ] **Step 3: 应用迁移并核对**

```bash
cd /www/Lumen-AI-OPC/springboot3
mysql -t ry-vue-opc < sql/migrations/V20261102__finance_agent_truth.sql
```
（迁移是幂等的，重复执行安全。）

核对：
```bash
mysql -t ry-vue-opc -e "SELECT workflow_code, company_id, cron_expression FROM opc_agent_workflow WHERE workflow_code='finance_daily_report_v1';"
mysql -t ry-vue-opc -e "SELECT COUNT(*) AS 剩余日报 FROM opc_insight_daily_report;"
mysql -t ry-vue-opc -e "SELECT column_name FROM information_schema.columns WHERE table_name='opc_insight_daily_report' AND column_name IN ('report_status','report_source','source_workflow_run_id');"
```
Expected:
- `company_id` = 2，`cron_expression` = `0 0 1 * * ?`
- `剩余日报` = **0**（那 7 行编的被删了）
- 三个新列都在

> `mysql` 走 `/root/.my.cnf`（600），**口令不上命令行**。

- [ ] **Step 4: 提交**

```bash
git add springboot3/sql springboot3/deploy/mysql-initdb.d
git commit -m "$(cat <<'EOF'
fix(sql): 工作流改挂 company 2 + 换新 dag + cron 改成北京 09:00 + 删掉编的日报

四处假话一起修:

1. 工作流挂在 company 1,全部财务数据在 company 2 —— 工具恒查 company 1,
   永远 0 行,「如实说无流水」分不出「链路是真的」和「工具完全坏掉」。
2. dag 还是两个纯 LLM 节点,不查任何表,模型一直在编。
3. cron 写的是 09:00,但容器时区是 UTC —— 实际是北京 17:00 才跑。
   改成 0 0 1 * * ?(UTC 01:00 = 北京 09:00)。不改成给容器加 TZ:
   那会改动所有 LocalDateTime.now() 的落库时间,影响面大得多。
4. 种子里的 7 行日报是编的数字(9800/19600/6200/4200/3100/3580/890),
   跟真实流水(3582/7080/9860/12150/13200/38450)一条都对不上 —— 连 3580
   和 3582 都差 2 块。新环境部署出来就不该带编的数字。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: 前端把日报的状态标出来 —— 顺手修三处「页面上是空的」（vue3-typescript）

**为什么这一节不能省**：spec 的反目标里有一条 ——「❌ 修好了工作流那条链，却让界面上的 `FALLBACK` 报告继续装作 AI 产出」。界面上现在既不标 FALLBACK，**数字列本身还是空的**（见下）。

**先说清楚现状**（已核对 `src/views/opc/insight/daily.vue` + `DailyReportVo` + `OpcInsightDailyReportMapper.xml`）：

| 界面上的东西 | 后端实际返回的 | 结果 |
|---|---|---|
| 列表列 `totalRevenue` / `totalExpense` / `voucherCount` | `DailyReportVo` **没有这三个字段**（只有 `kpiJson` 这个 JSON **字符串**） | **三列全是空的** |
| 详情弹窗 `current?.kpi?.voucherCount` | `current.kpi` **不存在**（`kpiJson` 是字符串） | **凭证数恒为 0，收入/支出恒为 0.00** |
| 「未走 LLM」的标记 | `llmUsed` 字段有，**界面从不显示** | FALLBACK 报告看起来跟真的一样 |

`daily.spec.ts` 的 fixture 里写着 `totalRevenue: 5000` —— **mock 里编了后端不返回的字段**，所以测试一直绿，页面一直是空的。

本 Task 三件事一起做：数字显示对、状态标出来、来源标出来。

**Files:**
- Modify: `vue3-typescript/src/api/opc/insight.ts`
- Modify: `vue3-typescript/src/views/opc/insight/daily.vue`
- Test: `vue3-typescript/src/views/opc/insight/__tests__/daily.spec.ts`

**Interfaces:**
- Consumes: Task 4 加在 `DailyReportVo` 上的 3 个字段（`reportStatus` / `reportSource` / `llmUsed`）；Task 5 定下的 `kpi_json` 形状
- Produces: `src/api/opc/insight.ts` 导出 `DailyReportVO` 类型；`daily.vue` 的「状态」列与两处 tag

- [ ] **Step 1: 写失败的测试**

在 `daily.spec.ts` 追加用例（既有 13 个用例**不动**）。新增 fixture —— **按后端真实返回的样子写**，不要再编字段：

```ts
function makeRealRows(): any[] {
  return [
    // 数字员工产出的日报：kpiJson 是字符串，内容是 Task 5 定下的嵌套形状
    {
      id: 11, period: '2026-11-01',
      kpiJson: JSON.stringify({
        query_bank_flow: { source: 'opc_finance_bank_flow', companyId: 2,
          fromDate: '2026-10-07', toDate: '2026-11-01', count: 6,
          totalIn: 51892.0, totalOut: 32430.0, net: 19462.0,
          yesterdayCount: 0, yesterdayIn: 0.0, yesterdayOut: 0.0, items: [] },
        query_voucher: { source: 'opc_finance_voucher', companyId: 2,
          fromDate: '2026-10-07', toDate: '2026-11-01', count: 5,
          totalIn: 35400.0, totalOut: 29750.0, items: [] },
      }),
      summaryMd: '昨日无资金流水', adviceMd: null, llmUsed: 'MiniMax-Text-01',
      reportStatus: 'FULL', reportSource: 'AGENT_WORKFLOW',
      createTime: '2026-11-01 09:00:00',
    },
    // insight 自己生成的日报：kpiJson 是 KpiSnapshot 形状
    {
      id: 12, period: '2026-10-31',
      kpiJson: JSON.stringify({ totalRevenue: 19600.0, totalExpense: 6200.0, voucherCount: 12 }),
      summaryMd: '[自动聚合·未走 LLM] ...', adviceMd: '...', llmUsed: 'FALLBACK',
      reportStatus: 'FALLBACK', reportSource: 'MANUAL',
      createTime: '2026-10-31 09:00:00',
    },
  ]
}
```

| 测试名 | 断言 |
|---|---|
| `列表「收入」列显示数字员工日报的 totalIn` | 找到显示 `51,892.00` 的单元格（**不是**空、不是 `0.00`） |
| `列表「收入」列显示手工日报的 totalRevenue` | 第二行显示 `19,600.00`（两种 kpi 形状都认） |
| `列表「流水笔数」列显示 6` | 数字员工行的流水笔数 == 6；手工行显示 `—`（**没有流水数据就不显示 0**） |
| `列表「凭证数」列显示 5` | 数字员工行的凭证数 == 5（来自 `query_voucher.count`） |
| `FALLBACK 的日报显示「未走 LLM」标记` | 存在文案为 `未走 LLM` 的 tag |
| `数字员工的日报显示「数字员工」来源标记` | 存在文案为 `数字员工` 的 tag |
| `PARTIAL 的日报显示「部分完成」标记` | fixture 里加一行 `reportStatus: 'PARTIAL'` → 存在 `部分完成` 的 tag |
| `kpiJson 解析失败时页面不崩` | `kpiJson: 'not json'` → 数字列显示 `—`，**没有未捕获异常** |
| `详情弹窗显示日报状态与来源` | 点「查看」→ 弹窗里有 `数字员工` 与 `完整` 的标记 |

- [ ] **Step 2: 跑测试确认失败**

```bash
cd vue3-typescript
npx vitest run src/views/opc/insight/__tests__/daily.spec.ts
```
Expected: FAIL —— 断言找不到（页面现在没有状态列，数字列是空的）。

- [ ] **Step 3: `api/opc/insight.ts` 补类型**

```ts
export interface DailyReportVO {
  id: number
  companyId: number
  period: string
  summaryMd?: string
  adviceMd?: string
  /** 后端存的是 JSON 字符串，不是对象 —— 页面要自己 parse */
  kpiJson?: string
  llmUsed?: string
  /** FULL / PARTIAL / FALLBACK */
  reportStatus?: 'FULL' | 'PARTIAL' | 'FALLBACK'
  /** AGENT_WORKFLOW / MANUAL */
  reportSource?: 'AGENT_WORKFLOW' | 'MANUAL'
  createTime?: string
}
```
并把 `listDaily` / `getDaily` 的返回标注成 `AxiosResponse`-ish 的 `data: DailyReportVO[]`（照 `src/api/opc/marketplace.ts` 里 `PublicationVO` 的写法，别自创一套）。

- [ ] **Step 4: `daily.vue` 解析 kpiJson + 取数**

```ts
/** 日报的 kpi_json 是 JSON 字符串；两种来源的形状不一样，都得认。 */
function parseKpi(raw?: string): any | null {
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return null }
}

function kpiOf(row: any): any {
  return parseKpi(row?.kpiJson) || {}
}

/** 收入：数字员工日报写 totalIn，insight 自己的日报写 totalRevenue */
function revenueOf(row: any): number | null {
  const k = kpiOf(row)
  const v = k?.query_bank_flow?.totalIn ?? k?.totalRevenue
  return v === undefined || v === null ? null : Number(v)
}
function expenseOf(row: any): number | null {
  const k = kpiOf(row)
  const v = k?.query_bank_flow?.totalOut ?? k?.totalExpense
  return v === undefined || v === null ? null : Number(v)
}
/** 流水笔数 —— 只有数字员工的日报有 */
function flowCountOf(row: any): number | null {
  const v = kpiOf(row)?.query_bank_flow?.count
  return v === undefined || v === null ? null : Number(v)
}
/** 凭证数 —— 数字员工日报取 query_voucher.count，insight 自己取 voucherCount */
function voucherCountOf(row: any): number | null {
  const k = kpiOf(row)
  const v = k?.query_voucher?.count ?? k?.voucherCount
  return v === undefined || v === null ? null : Number(v)
}
/** 数字员工的日报带真实区间；手工的不带 */
function intervalOf(row: any): string {
  const k = kpiOf(row)?.query_bank_flow
  return k?.fromDate && k?.toDate ? `${k.fromDate} ~ ${k.toDate}` : ''
}
```

- [ ] **Step 5: `daily.vue` 加「状态」列与两处 tag**

列定义（`columns` 数组）：

```ts
const columns: Column[] = [
  { key: 'period', label: '所属期', primary: true, width: 120 },
  { key: 'revenue', label: '收入', type: 'amount', width: 140, align: 'right' },
  { key: 'expense', label: '支出', type: 'amount', width: 140, align: 'right' },
  { key: 'flowCount', label: '流水笔数', type: 'number', width: 100, align: 'right' },
  { key: 'voucherCount', label: '凭证数', type: 'number', width: 100, align: 'right' },
  { key: 'status', label: '状态', width: 150 },
  { key: 'createTime', label: '生成时间', type: 'date', width: 160, hideOnMobile: true },
]
```

> `ResponsiveTable` 的 `type: 'amount'` 那一列取的是 row 上的同名 key → `list.value` 里的行**不能直接用后端返回的原始对象**。
> → 在 `load()` 里 `list.value = (r.data || []).map((row) => ({ ...row, revenue: revenueOf(row), expense: expenseOf(row), flowCount: flowCountOf(row), voucherCount: voucherCountOf(row) }))`。
> **取不到的值给 `null` 而不是 0** —— `0` 会被渲染成 `0.00`，那就是在说「收入是 0 元」；`null` 渲染成 `—`，说的是「这条日报没有这个数据」。**这两句话不一样**（这正是本项目的主题）。

「状态」列用 slot 渲染两个 tag：

```html
<template #status="{ row }">
  <el-tag v-if="row.reportStatus === 'FALLBACK'" type="danger" size="small">未走 LLM</el-tag>
  <el-tag v-else-if="row.reportStatus === 'PARTIAL'" type="warning" size="small">部分完成</el-tag>
  <el-tag v-else type="success" size="small">完整</el-tag>
  <el-tag v-if="row.reportSource === 'AGENT_WORKFLOW'" size="small" type="info">数字员工</el-tag>
  <el-tag v-else size="small">手工</el-tag>
</template>
```

> **「未走 LLM」这个 tag 是反目标那条的正面兑现**：FALLBACK 的报告不再装作 AI 产出。
> 数字员工产出的（`AGENT_WORKFLOW`）标「数字员工」，手工的标「手工」—— 两个日报不混为一谈（spec §4.4 的「命名冲突要处理」）。

详情弹窗同样加：状态 + 来源两个 tag + 「统计区间」一行（`intervalOf(current)`，为空就不显示这一行）+ 摘要/建议用 `current?.summaryMd` / `current?.adviceMd`（**注意字段名是 `summaryMd` 不是 `summary`** —— 现在弹窗读的是 `current?.summary`，**恒为 undefined，摘要框永远显示「（暂无）」**）。这一条顺手修掉。

- [ ] **Step 6: 跑测试确认通过**

```bash
cd vue3-typescript
npx vitest run src/views/opc/insight/__tests__/daily.spec.ts
```
Expected: PASS（既有 13 个 + 新增 9 个）。

- [ ] **Step 7: 跑前端全量测试**

```bash
cd vue3-typescript
npx vitest run
```
Expected: 全绿（66 文件 / 1294 用例 + 新增）。

- [ ] **Step 8: 提交**

```bash
cd /www/Lumen-AI-OPC
git add vue3-typescript
git commit -m "$(cat <<'EOF'
fix(insight-ui): 日报的数字列一直是空的,状态也没标出来

三处「页面上看着有、其实没有」:

1. 列表列读 row.totalRevenue / totalExpense / voucherCount,但后端
   DailyReportVo 根本没有这三个字段 —— 它只有 kpiJson 这个 JSON 字符串。
   所以收入/支出/凭证数三列永远是空的。
2. 详情弹窗读 current?.kpi?.voucherCount,current.kpi 不存在 —— 凭证数恒 0,
   收入支出恒 0.00。比空还糟:0.00 是在说「收入是 0 元」。
3. 摘要框读 current?.summary,字段名其实是 summaryMd —— 永远显示「（暂无）」。

daily.spec.ts 的 fixture 里编了 totalRevenue: 5000,后端从不返回这个字段,
所以测试一直是绿的,页面一直是空的。

现在前端自己 parse kpiJson,并且同时认两种形状:数字员工日报的
{query_bank_flow:{totalIn,totalOut,count}, query_voucher:{count}} 和
insight 自己日报的 {totalRevenue,totalExpense,voucherCount}。
取不到的值给 null 渲染成「—」,不给 0 —— 0.00 是在说「收入是 0 元」。

另外加「状态」列:FULL/PARTIAL/FALLBACK 显示成 完整/部分完成/未走 LLM,
来源显示成 数字员工/手工。反目标里有一条「别让界面上的 FALLBACK 报告
继续装作 AI 产出」—— 现在它自己承认没走 LLM。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: 验证层 —— 健康检查第 26 节 + 修第 25 节（deploy/scripts）

**这是让整件事「可复跑地证明」的一层。** 没有它，前面 8 个 Task 全绿也只是一句「我写完了」。

**Files:**
- Modify: `springboot3/deploy/scripts/health-check.sh`
- Modify: `springboot3/deploy/README.md`
- Modify: `CLAUDE.md`
- Modify: `README.md`（根）

**Interfaces:**
- Consumes: Task 1–8 的全部产出
- Produces: `check_finance_agent_truth()`（第 26 节，**10 条断言**）；第 25.5 追加「状态成功」断言；第 25.6 改成「sys_job_log 有记录 **且** 对应运行是 SUCCESS」

- [ ] **Step 1: 修第 25 节的两个漏洞**

**25.5 → 拆成 25.5（存在）+ 25.5b（状态成功）**：

```bash
# --- 25.5b 落库的记录必须是 SUCCESS ---
# 以前 25.5 只断言「记录存在」—— 4 条 FAILED 的记录照样通过。
# 现在必须断言状态是 SUCCESS。PARTIAL 不算成功(见第 26 节)。
local run_status
run_status=$(db_q "SELECT status FROM opc_agent_workflow_run WHERE id = ${new_run_id};" | tr -d '\r')
if [[ "${run_status}" == "SUCCESS" ]]; then
  ok "25.5b 这条运行记录 status=SUCCESS"
else
  nok "25.5b 这条运行记录 status=${run_status} —— 记录落库了,但业务没跑成(以前的 25.5 只看记录在不在)"
fi
```
（需要把 25.5 里查到的 `new_run` 的 id 单独存进 `new_run_id`。）

**25.6 → 不能再拿 `sys_job_log.status=0` 当成功证据**：
```bash
# --- 25.6 sys_job_log 有记录, 且对应的业务运行是 SUCCESS ---
# sys_job_log.status 记的是「Feign 触发受理成功」,不是业务成功 ——
# id 35/36/37/38 四次业务全 FAILED,sys_job_log 却全是 status=0。
local log_row run_status_by_log
...
if [[ "${log_status}" == "0" && "${run_status_by_log}" == "SUCCESS" ]]; then
  ok "25.6 调度器记成功,业务运行也成功"
elif ...
```
> `run_status_by_log` 通过 `sys_job_log.start_time` 与 `opc_agent_workflow_run.start_time` 关联取（两者都是 CST）。

**25.5 的等待条件也要改**：现在等的是 `trigger_source LIKE 'quartz:%'` 的新记录 —— 但 Task 7 之后，**健康检查自己触发的是 `manual:`**，所以这条等待**永远等不到**（在 40 次 × 3 秒 = 2 分钟里超时 → 25.5 报错）。

→ **把 25.5 的等待条件改成 `trigger_source LIKE 'manual:%'`**（健康检查自己触发的那条），断言文案相应改成「真跑出一条运行记录」；**「定时器有没有真的跑过」挪到第 26.1 节**（那里查 `quartz:` 的历史记录）。

> 这一条很容易漏 —— **改了 Task 7 就必须改这里**，否则第 25 节会从「放过失败」变成「误报失败」。

- [ ] **Step 2: 新增第 26 节**

在 `check_scheduler_truth()` 之后加 `check_finance_agent_truth()`，并在 `main()` 里加 `step 26 check_finance_agent_truth`。

| 断言 | 内容 | 判定 |
|---|---|---|
| 26.1 | 存在 `trigger_source LIKE 'quartz:%'` 且 `status='SUCCESS'` 的运行记录（48 小时内） | 有 → ok |
| 26.2 | `opc_insight_daily_report` 有一条 `report_source='AGENT_WORKFLOW'` 且 `report_status='FULL'` 且 `llm_used != 'FALLBACK'` 的行，且 `source_workflow_run_id` 指向一条 SUCCESS 的运行 | 有 → ok |
| 26.3 | **日报 `kpi_json.query_bank_flow.items[]` 里的金额集合 == `opc_finance_bank_flow` 里同公司同区间的金额集合**（逐笔相等、条数相等） | 相等 → ok |
| 26.4 | 工具结果的 `company_id` == 工作流的 `company_id` == 2 | 相等 → ok |
| 26.5 | `GET /opc/agent/tools`（Task 2 新增）返回的工具名里**没有** `query_daily_report` / `create_voucher` / `extract_voucher`，且**有** `query_bank_flow` / `query_voucher` / `query_balance` | 一致 → ok |
| 26.6 | **最近一次 quartz 触发的 run 是 SUCCESS** | 是 → ok |
| 26.7 | `opc_insight_daily_report` 最近一条**不是** `FALLBACK` | 不是 → ok |
| 26.8 | 同 `(company_id, period)` 下 `report_source='MANUAL'` 的行**没有**覆盖 `AGENT_WORKFLOW` 的行（同一 (company_id, period) 只可能有一行，因为有 uk） | 断言表里同一 (company_id, period) 最多一行，且它是 AGENT_WORKFLOW |
| 26.9 | **降级链真的接上了**：`GET /opc/llm/models` 返回的**已启用** provider ≥ 2 个，且其中有 DeepSeek | 是 → ok |
| 26.10 | **insight 自己的 LLM 也通了**：`opc_insight_advice` 最近一条 `llm_used != 'FALLBACK'` | 不是 → ok |

**26.3 的实现要点**（这一条最强，必须写对）：
1. 从最近一条 `report_source='AGENT_WORKFLOW'` 的日报里取 `kpi_json`
2. 从 `kpi_json` 里取 **`query_bank_flow` 这一层**（Task 5 定的嵌套形状）的 `companyId` / `fromDate` / `toDate` / `items[].amount`
3. 用**同样的 companyId + fromDate + toDate** 去 `opc_finance_bank_flow` 查金额集合
4. 两个集合排序后逐笔比较（**用字符串比较金额，不用浮点**）

```bash
local kpi_json
kpi_json=$(db_q "SELECT kpi_json FROM opc_insight_daily_report WHERE report_source='AGENT_WORKFLOW' ORDER BY id DESC LIMIT 1;" | tr -d '\r')
# 用 python 解析 + 对账（脚本里已有 python 解析 JSON 的先例,见 25.3）
# python 侧要取 kpi["query_bank_flow"]["items"]，不是 kpi["items"]
```
> `kpi_json` 是 MySQL 的 JSON 列 → `db_q` 取出来是 JSON 文本。**不要用 jq**（脚本里没有 jq 的依赖先例，用 python3 —— 25.3 已经在用）。

**26.3 必须非空**：日报的区间是「自有数据起算 ~ 昨天」，company 2 有 6 笔流水 → 集合是 6 个金额。断言时若 DB 侧集合**为空** → **报错**（说明日报的区间算错了），而不是「两个空集相等 → 通过」。

**26.9 的实现要点**：`GET /opc/llm/models`（`opc-ai-core`）返回**只含已启用的 provider**（`enabled()` = 配置开关 && key 非空）→ 直接数返回条数即可。断言里要能认出 DeepSeek 那个 provider（它的 `provider` 字段是 `"DeepSeek"` 一类，`id` 是模型名）。**这个端点不返回 key 的值** —— 它只返回 provider 名和模型名。

> **26.9 证明的是「降级链接上了」，不是「降级真的发生过」**。真发生过 = MiniMax 429 后落到了 DeepSeek，那要等一次真实的 429。26.9 是可复跑的那一半；另一半靠 26.6（最近一次 quartz 是 SUCCESS）+ `opc_agent_token_usage.model` 里出现 DeepSeek 的模型名 —— **两者合起来才说明「429 没把链打断」**。
> ⚠️ **不要写成「断言 token_usage 里必须有 DeepSeek 的行」** —— MiniMax 正常时不重试也不降级，那条断言会永远红，而且它红的时刻跟本轮改动无关。

**26.10 的实现要点**：`opc_insight_advice` 只在有人调 `POST /opc/insight/advice/generate?topic=...` 或 `POST /opc/insight/advice/{id}/regenerate` 时才写行（`AdviceServiceImpl`，7 天缓存）。
→ 健康检查的做法：**先 `GET /opc/insight/advice` 取最近一条**；没有行就 `POST /opc/insight/advice/generate?topic=财务` 造一条；**取到的 `llm_used` 是 `FALLBACK` 时，再调 `POST /opc/insight/advice/{id}/regenerate`**（regenerate 绕开 7 天缓存，直接调 LLM）→ 再断言。
> **这是第 26 节里唯一会真的花 LLM 配额的一条**（regenerate 一次 ≈ 一次小请求）。但它是验收标准 §6 第 8 条（「insight 自己的 LLM 也通了」）唯一的可复跑证明 —— 不写它，那条验收就只是嘴上说的。
> 端点需要登录态 → 复用健康检查第 3 节已经登录好的会话。

- [ ] **Step 3: 跑健康检查（需要用户授权）**

```bash
cd springboot3/deploy
bash deploy.sh health
```
> ⚠️ **这一步需要用户明确点名授权**（CLAUDE.md 坑 #11 与记忆里的「连 deploy.sh health 本身也要用户点名授权」）。

Expected: 26 节全绿。

- [ ] **Step 4: 更新文档**

`deploy/README.md`：
- 「健康检查的 25 节 / 184 项断言」→ 改成「**26 节**」并更新断言总数
- 表格里加第 26 行：`26 | 财务数字员工读的是真数据（日报逐笔对表）`

`docs/README.md`：
- 「健康检查 25 节 / 184 项断言」→ 26 节（**这页也有同样的数字**，别只改一处）
- 文档维护约定表里「加一条健康检查断言」那行提到的「健康检查的 25 节」→ 26 节

`CLAUDE.md`：
- §四 加一条坑：**「每天 09:00」是北京时间的假话**（容器 UTC，cron `0 0 9 * * ?` = 北京 17:00；已改成 `0 0 1 * * ?`）
- §一 「健康检查 25 节」→ 26 节

根 `README.md`：
- 「定时任务」段的 cron 时间口径改成北京时间，并写明容器时区是 UTC

- [ ] **Step 5: 提交**

```bash
git add springboot3/deploy CLAUDE.md README.md
git commit -m "$(cat <<'EOF'
feat(health-check): 第 26 节 —— 财务数字员工读的是真数据

新增 10 条断言,最强的一条是 26.3:日报 kpi_json.query_bank_flow.items[] 里的
金额集合要逐笔等于 opc_finance_bank_flow 里同公司同区间的金额集合。区间由
日报自己回写,所以复算用的是日报实际用的区间,不是猜的。而且集合非空是前置
条件 —— 空集相等那种空转会直接报错。

另外两条补的是「以前没有断言、现在有了」:
- 26.5 打 GET /opc/agent/tools —— 假工具下线这件事,从外面看得见了。
  以前「假工具没了」只是源码里的一个说法。
- 26.9 打 GET /opc/llm/models —— 降级链接上了(两个 provider 都 enabled),
  这个端点只返回 provider 名和模型名,不返回 key。
- 26.10 触发一次建议重生成,断言 opc_insight_advice 最近一条不是 FALLBACK ——
  insight 自己的 LLM 到底通没通,以前只能靠「12 条全是 FALLBACK」这句观察。

顺带修第 25 节两个漏洞:
- 25.5 以前只断言「运行记录存在」,4 条 FAILED 照样通过。现在追加断言
  status=SUCCESS。
- 25.6 以前拿 sys_job_log.status=0 当成功证据 —— 那个字段记的是「Feign
  触发受理成功」,不是业务成功。现在要求对应的业务运行也是 SUCCESS。

还有一处连带改动:25.5 的等待条件原来等 trigger_source LIKE 'quartz:%',
但手动执行改成 manual: 之后健康检查自己触发的不再是 quartz: —— 等待条件
要跟着改成 manual:,「定时器有没有真跑过」挪到 26.1。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 执行顺序与提交全景

```
Task 1  (finance 端点)        → 提交 1
Task 2  (工具搬家+真实现)      → 提交 2
Task 3  (引擎 TOOL 节点)       → 提交 3
Task 4  (insight upsert+列)   → 提交 4   ← 改 ruoyi-common-core，须 install
Task 5  (persist 落表)         → 提交 5   ← 改 opc-common，须 install
Task 6  (LLM 重试+降级链)      → 提交 6
Task 7  (调度触发源)           → 提交 7
Task 8  (迁移+种子)            → 提交 8
Task 9  (前端状态与数字)        → 提交 9
Task 10 (健康检查第 26 节)     → 提交 10
```

**依赖**：Task 2 依赖 1；Task 3 依赖 2；Task 5 依赖 3+4；Task 8 依赖 3+4 的列名；**Task 9 依赖 4（VO 上那 3 个字段）+ 5（`kpi_json` 形状）**；Task 10 依赖 1–9 的全部产出。**Task 6 / 7 之间无依赖，可并行。**

## 实现阶段需要用户单独授权的六件事（不要自己做）

1. **重建镜像**：`bash deploy.sh rebuild opc-agent-hub` / `opc-ai-core` / `opc-insight`（改了这三个模块的代码）
2. **重建前端**：`bash deploy.sh rebuild frontend`（Task 9 改了 `daily.vue`）
3. **重启容器**：compose 改了环境变量（`DEEPSEEK_API_KEY` / `OPC_LLM_DEEPSEEK_ENABLED`）→ `docker compose up -d aiopc-insight aiopc-agent-hub aiopc-ai-core`
4. **推 Nacos 配置**：`cd springboot3/deploy/nacos && bash import-dev.sh`（改了 `application-dev.yml`）
5. **跑健康检查**：`bash deploy.sh health`
6. **让用户自己填 key**：提醒用户把 `DEEPSEEK_API_KEY=sk-...` 写进 `springboot3/deploy/.env` —— **不要替他写、不要打印 key 的值**

## 明确不做

- **不补 HTTP 层的 `tool_calls` 透传** —— 本轮走引擎 TOOL 节点，那条路是下一轮的（spec §2 非目标）
- **不做数据加厚** —— 财务数据维持现状（6 笔流水 / 5 张凭证）
- **不实现 `create_voucher` / `extract_voucher`** —— 假工具**下线**而不是留着
- **不接 `InsightDailyReportJob` 的调度** —— 会和工作流产出两个日报
- **不改 `deploy/helm/` / `deploy/k8s/`** —— K8s 已划出范围
- **不重建空库** —— 103 张表现有数据不动，只用增量迁移
- **不删那 4 条历史 FAILED 运行记录** —— 保留当历史，不改历史
