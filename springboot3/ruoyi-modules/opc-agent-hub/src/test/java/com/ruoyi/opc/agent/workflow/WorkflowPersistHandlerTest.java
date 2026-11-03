package com.ruoyi.opc.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.agent.feign.RemoteInsightService;
import com.ruoyi.opc.ai.runtime.AgentRuntime;
import com.ruoyi.opc.ai.tools.ToolExecutor;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowPersistHandler} 单测 —— Task 5
 *
 * <p><b>为什么要有这些用例</b>：工作流的产出物以前只躺在
 * {@code opc_agent_workflow_run.output_result} 里（运行日志），
 * 业务上查日报的 {@code opc_insight_daily_report} 跟工作流没有任何关系 ——
 * 工作流取到的真实流水/凭证，业务侧一条都看不到。</p>
 *
 * <p>这些用例把「产出物真的落表」「落的是真数据」「来源不由调用方声明」
 * 「LLM 挂了要标 PARTIAL 而不是把整条链报废」「坏数据不许送出去」
 * 钉成会红的断言。</p>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowPersistHandler 单测")
class WorkflowPersistHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 引擎 TOOL 节点产出的合并 JSON —— <b>这就是 {@code kpi_json}</b>。
     *
     * <p>形状一字不改：{@code {"query_bank_flow":{...},"query_voucher":{...}}}，
     * 金额是 scale 2 的 BigDecimal（{@code 51892.00} 不是 {@code 51892.0}）。</p>
     */
    private static final String KPI_JSON = """
            {"query_bank_flow":{"source":"opc_finance_bank_flow","companyId":2,
             "fromDate":"2026-10-07","toDate":"2026-11-01",
             "count":6,"totalIn":51892.00,"totalOut":32430.00,"net":19462.00,
             "yesterdayCount":0,"yesterdayIn":0.00,"yesterdayOut":0.00,
             "items":[{"tradeTime":"2026-10-07 12:49:03","direction":"OUT","amount":13200.00}]},
             "query_voucher":{"source":"opc_finance_voucher","companyId":2,
             "fromDate":"2026-10-07","toDate":"2026-11-01",
             "count":5,"totalIn":35400.00,"totalOut":29750.00,"items":[]}}
            """;

    /** 单个工具的原始返回（引擎里 {@code ctx.outputs["n1.query_bank_flow"]}）。 */
    private static final String BANK_FLOW_JSON = """
            {"source":"opc_finance_bank_flow","companyId":2,
             "fromDate":"2026-10-07","toDate":"2026-11-01",
             "count":6,"totalIn":51892.00,"totalOut":32430.00,"net":19462.00,
             "yesterdayCount":0,"yesterdayIn":0.00,"yesterdayOut":0.00,"items":[]}
            """;

    /** brief 指定的 persist 声明。 */
    private static final String PERSIST_DECL = """
            {"target":"daily_report","date":"${yesterday}","kpiFrom":"n1","summaryFrom":"n2"}
            """;

    /** 引擎给 handler 的模板变量（已含内置日期变量）。 */
    private static final Map<String, Object> VARS = Map.of("yesterday", "2026-11-02");

    @Mock
    private RemoteInsightService insightService;

    @Mock
    private AgentRuntime agentRuntime;

    @Mock
    private ToolExecutor toolExecutor;

    private WorkflowPersistHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WorkflowPersistHandler(insightService);
    }

    // ==================== 工具方法 ====================

    private JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private WorkflowEngine.WorkflowContext ctx() {
        WorkflowEngine.WorkflowContext ctx = new WorkflowEngine.WorkflowContext();
        ctx.setCompanyId(2L);
        ctx.setRunId(1001L);
        ctx.setOutputs(new HashMap<>());
        ctx.setLlmModel("MiniMax-M2.1");
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBody() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(insightService).upsertDailyReport(captor.capture());
        return captor.getValue();
    }

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("产出物真的落表：kpiJson 一字不改、summaryMd 取自 summary 节点")
    void persist_sendsKpiAndSummaryFromNodes() {
        when(insightService.upsertDailyReport(any())).thenReturn(R.ok(88L));

        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);
        ctx.getOutputs().put("n2", "今日小结：流水正常。");

        WorkflowPersistHandler.PersistOutcome outcome =
                handler.persist(node(PERSIST_DECL), ctx, VARS);

        Map<String, Object> body = captureBody();
        assertThat(body.get("kpiJson")).isEqualTo(KPI_JSON);
        assertThat(body.get("summaryMd")).isEqualTo("今日小结：流水正常。");
        assertThat(body.get("companyId")).isEqualTo(2L);
        assertThat(body.get("period")).isEqualTo("2026-11-02");
        assertThat(body.get("reportStatus")).isEqualTo("FULL");
        assertThat(body.get("llmUsed")).isEqualTo("MiniMax-M2.1");
        assertThat(body.get("sourceWorkflowRunId")).isEqualTo(1001L);

        // R-97：来源由接收侧按端点身份写死，**不由调用方声明** → body 里没有这个键
        assertThat(body).doesNotContainKey("reportSource");

        assertThat(outcome.ok()).isTrue();
        assertThat(outcome.rowId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("R-96：LLM 挂了 → reportStatus=PARTIAL、llmUsed=FALLBACK（模型 ID 列不塞状态）")
    void persist_writesPartialStatusWhenLlmFailed() {
        when(insightService.upsertDailyReport(any())).thenReturn(R.ok(88L));

        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.setPartial(true);
        ctx.setLlmModel(null);
        ctx.getOutputs().put("n1", KPI_JSON);
        ctx.getOutputs().put("n2", "[自动聚合·未走 LLM] 昨日流水 6 笔。");

        handler.persist(node(PERSIST_DECL), ctx, VARS);

        Map<String, Object> body = captureBody();
        assertThat(body.get("reportStatus")).isEqualTo("PARTIAL");
        assertThat(body.get("llmUsed")).isEqualTo("FALLBACK");
        // 摘要用引擎侧的确定性聚合，不是空的
        assertThat((String) body.get("summaryMd")).isNotBlank()
                .startsWith("[自动聚合·未走 LLM]");
    }

    // ==================== 非法声明 → 抛 ====================

    @Test
    @DisplayName("persist 目标不认识 → 抛，且不调 insight")
    void persist_unknownTarget_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);

        assertThatThrownBy(() -> handler.persist(
                node("{\"target\":\"monthly_report\",\"kpiFrom\":\"n1\"}"), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("不支持的 persist 目标");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("kpiFrom 指向没有产出的节点 → 抛，且不调 insight")
    void persist_missingKpiNode_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("kpi 节点没有产出");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("kpi 不是合法 JSON → 抛，且不调 insight（别把坏 JSON 送出去等对方报错）")
    void persist_kpiJsonNotJson_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", "这不是 JSON");
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("不是合法 JSON");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("R-154：kpi 是合法 JSON 但不是对象 → 抛（kpi_json 的形状是对象）")
    void persist_kpiJsonNotObject_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", "\"abc\"");
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("不是 JSON 对象");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("R-154：kpi 是合法 JSON 但后面还跟着另一个 JSON → 抛（FAIL_ON_TRAILING_TOKENS）")
    void persist_kpiJsonTrailingGarbage_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        // 两个 JSON 文档拼在一起 —— 前面那个本身合法，尾巴要靠 FAIL_ON_TRAILING_TOKENS 拦
        ctx.getOutputs().put("n1", "{\"query_bank_flow\":{}} {\"query_voucher\":{}}");
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("尾巴");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("R-155：summary 是空白串 → 抛（空的东西不默默存进 summary_md）")
    void persist_blankSummary_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);
        ctx.getOutputs().put("n2", "   ");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("summary 节点产出是空的");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("R-156：persist 声明没有 date → 抛，且不调 insight")
    void persist_missingDate_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(
                node("{\"target\":\"daily_report\",\"kpiFrom\":\"n1\",\"summaryFrom\":\"n2\"}"), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("缺少 date");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("R-156：persist 声明没有 summaryFrom → 抛，且不调 insight")
    void persist_missingSummaryFrom_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);

        assertThatThrownBy(() -> handler.persist(
                node("{\"target\":\"daily_report\",\"date\":\"${yesterday}\",\"kpiFrom\":\"n1\"}"), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("缺少 summaryFrom");

        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("R-156 / R-105：summaryFrom 指向没有产出的节点 → 抛，且不调 insight")
    void persist_missingSummaryNode_throws() {
        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("summary 节点没有产出");

        verify(insightService, never()).upsertDailyReport(any());
    }

    // ==================== 远端失败 → 抛 ====================

    @Test
    @DisplayName("insight 返回失败 → 抛（产出物没落表 = 这件事没做成）")
    void persist_remoteFailure_throws() {
        when(insightService.upsertDailyReport(any())).thenReturn(R.fail("日报落表炸了"));

        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("日报落表失败");
    }

    @Test
    @DisplayName("insight 返回 null → 也抛（不许 NPE 冒出去）")
    void persist_remoteReturnsNull_throws() {
        when(insightService.upsertDailyReport(any())).thenReturn(null);

        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("日报落表失败");
    }

    @Test
    @DisplayName("R-157：Feign 调用本身抛异常 → 消息里带异常类名（别把 cause 弄丢了）")
    void persist_remoteThrows_carriesExceptionClassName() {
        when(insightService.upsertDailyReport(any()))
                .thenThrow(new IllegalStateException("连接被拒"));

        WorkflowEngine.WorkflowContext ctx = ctx();
        ctx.getOutputs().put("n1", KPI_JSON);
        ctx.getOutputs().put("n2", "今日小结");

        assertThatThrownBy(() -> handler.persist(node(PERSIST_DECL), ctx, VARS))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("日报落表失败")
                .hasMessageContaining("IllegalStateException")
                .hasMessageContaining("连接被拒");
    }

    // ==================== 引擎接线 ====================

    @Test
    @DisplayName("dag 里没有 persist 声明 → 不调 insight（不落表也不报错）")
    void persist_noPersistDeclaration_isNoop() throws Exception {
        when(toolExecutor.execute(eq("query_bank_flow"), anyString(), any()))
                .thenReturn(BANK_FLOW_JSON);

        WorkflowEngine engine = new WorkflowEngine(agentRuntime, toolExecutor, handler);
        WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
        def.setDagJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"TOOL\","
                + "\"tools\":[\"query_bank_flow\"],\"args\":{}}],\"edges\":[]}");
        def.setCompanyId(2L);

        WorkflowEngine.WorkflowResult result = engine.run(def, Map.of());

        assertThat(result.isSuccess()).isTrue();
        verify(insightService, never()).upsertDailyReport(any());
    }

    @Test
    @DisplayName("R-100：ctx.runId 为 null → sourceWorkflowRunId 为 null（不编一个 run id 出来）")
    void engine_persistNode_missingRunId_sendsNullRunId() throws Exception {
        when(toolExecutor.execute(eq("query_bank_flow"), anyString(), any()))
                .thenReturn(BANK_FLOW_JSON);
        when(insightService.upsertDailyReport(any())).thenReturn(R.ok(88L));

        WorkflowEngine engine = new WorkflowEngine(agentRuntime, toolExecutor, handler);
        WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
        def.setDagJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"TOOL\","
                + "\"tools\":[\"query_bank_flow\"],\"args\":{}}],\"edges\":[],"
                + "\"persist\":{\"target\":\"daily_report\",\"date\":\"${yesterday}\","
                + "\"kpiFrom\":\"n1\",\"summaryFrom\":\"n1\"}}");
        def.setCompanyId(2L);
        // 刻意不设 runId —— 守住「三处接法没漏」这条契约

        WorkflowEngine.WorkflowResult result = engine.run(def, Map.of());

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> body = captureBody();
        assertThat(body).containsKey("sourceWorkflowRunId");
        assertThat(body.get("sourceWorkflowRunId")).isNull();
    }

    @Test
    @DisplayName("引擎跑完节点后真的落表：kpiJson 是工具返回的原始 JSON")
    void engine_persistNode_writesRealToolOutput() throws Exception {
        when(toolExecutor.execute(eq("query_bank_flow"), anyString(), any()))
                .thenReturn(BANK_FLOW_JSON);
        when(insightService.upsertDailyReport(any())).thenReturn(R.ok(88L));

        WorkflowEngine engine = new WorkflowEngine(agentRuntime, toolExecutor, handler);
        WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
        def.setDagJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"TOOL\","
                + "\"tools\":[\"query_bank_flow\"],\"args\":{}}],\"edges\":[],"
                + "\"persist\":{\"target\":\"daily_report\",\"date\":\"${yesterday}\","
                + "\"kpiFrom\":\"n1\",\"summaryFrom\":\"n1\"}}");
        def.setCompanyId(2L);
        def.setRunId(4242L);

        WorkflowEngine.WorkflowResult result = engine.run(def, Map.of());

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> body = captureBody();
        assertThat((String) body.get("kpiJson")).contains("query_bank_flow").contains("51892.00");
        assertThat(body.get("sourceWorkflowRunId")).isEqualTo(4242L);
    }

    @Test
    @DisplayName("persist 失败 → 整条 run 失败（产出物没落表，不能记成功）")
    void engine_persistFails_runFails() throws Exception {
        when(toolExecutor.execute(eq("query_bank_flow"), anyString(), any()))
                .thenReturn(BANK_FLOW_JSON);
        when(insightService.upsertDailyReport(any())).thenReturn(R.fail("insight 挂了"));

        WorkflowEngine engine = new WorkflowEngine(agentRuntime, toolExecutor, handler);
        WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
        def.setDagJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"TOOL\","
                + "\"tools\":[\"query_bank_flow\"],\"args\":{}}],\"edges\":[],"
                + "\"persist\":{\"target\":\"daily_report\",\"date\":\"${yesterday}\","
                + "\"kpiFrom\":\"n1\",\"summaryFrom\":\"n1\"}}");
        def.setCompanyId(2L);

        WorkflowEngine.WorkflowResult result = engine.run(def, Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("日报落表失败");
        // 诊断信息：工具的产出仍然留在 outputs 里（这不是 bug）
        assertThat(result.getOutputs()).containsKey("n1");
    }

}
