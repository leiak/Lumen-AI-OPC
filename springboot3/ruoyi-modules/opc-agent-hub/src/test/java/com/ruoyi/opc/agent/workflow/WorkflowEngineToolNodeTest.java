package com.ruoyi.opc.agent.workflow;

import com.ruoyi.opc.ai.runtime.AgentRuntime;
import com.ruoyi.opc.ai.tools.ToolExecutor;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowEngine} 的 TOOL 节点单测 —— Task 3
 *
 * <p><b>缺陷</b>：{@code runTool()} 以前是 {@code // TODO: 调 ToolExecutor} +
 * 塞一个 {@code "{}"} —— dag 里写什么工具都等于没写，产出的数字恒为空。
 *
 * <p>这些用例把「真的调了工具」「数字口径没变」「LLM 挂了不拖垮整条 run」
 * 三件事钉成会红的断言。
 *
 * <p><b>修复轮 1</b>：补上三条护栏 ——
 * F1（catch 必须是 {@code Exception}，不能只 catch {@code OpcException}）、
 * F2（业务时区必须是 Asia/Shanghai，不能用 {@code systemDefault()}）、
 * F3（工具返回解析失败 → 硬失败，不许伪装成「0 笔」）。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEngine TOOL 节点 + 内置日期变量 单测")
class WorkflowEngineToolNodeTest {

    /**
     * 固定业务时钟：{@code 2026-03-07T16:30Z} → UTC 是 <b>03-07</b>，北京是 <b>03-08</b>。
     *
     * <p>刻意挑一个「按 UTC 算和按北京算差一天」的瞬间 ——
     * 这样「实现到底用了哪个时区」就成了可断言的事实，而不是同义反复。
     */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-07T16:30:00Z"), ZoneId.of("UTC"));

    /** finance 端 {@code query_bank_flow} 的真实返回形状（金额是 scale 2 的 BigDecimal）。 */
    private static final String BANK_FLOW_JSON = """
            {"source":"opc_finance_bank_flow","companyId":2,
             "fromDate":"2026-10-07","toDate":"2026-11-01",
             "count":6,"totalIn":51892.00,"totalOut":32430.00,"net":19462.00,
             "yesterdayCount":0,"yesterdayIn":0.00,"yesterdayOut":0.00,"items":[]}
            """;

    /** finance 端 {@code query_voucher} 的真实返回形状。 */
    private static final String VOUCHER_JSON = """
            {"source":"opc_finance_voucher","companyId":2,
             "fromDate":"2026-10-07","toDate":"2026-11-01",
             "count":5,"totalIn":35400.00,"totalOut":29750.00,
             "yesterdayCount":0,"yesterdayIn":0.00,"yesterdayOut":0.00,"items":[]}
            """;

    /** brief 指定的 dag fixture：一个 TOOL 节点，声明两个工具，args 里用了 ${monthAgo}。 */
    private static final String DAG_TOOL = """
            {"nodes":[{"id":"n1","type":"TOOL",
                       "tools":["query_bank_flow","query_voucher"],
                       "args":{"fromDate":"${monthAgo}"}}],
             "edges":[]}
            """;

    /** TOOL → LLM：取数 + 写文案。 */
    private static final String DAG_TOOL_AND_LLM = """
            {"nodes":[{"id":"n1","type":"TOOL",
                       "tools":["query_bank_flow","query_voucher"],
                       "args":{"fromDate":"${monthAgo}"}},
                      {"id":"n2","type":"LLM",
                       "prompt":"昨天 ${yesterday}，今天 ${today}，一周前 ${weekAgo}，一月前 ${monthAgo}，公司 ${companyId}",
                       "config":{}}],
             "edges":[{"source":"n1","target":"n2"}]}
            """;

    @Mock
    private AgentRuntime agentRuntime;

    @Mock
    private ToolExecutor toolExecutor;

    /**
     * Task 5 起引擎多了 {@link WorkflowPersistHandler} 构造依赖 —— 不给 mock 的话
     * Mockito 构造注入会传 null（这里这些用例的 dag 都没有 persist 声明，
     * 引擎不会碰它，但别留隐患）。
     */
    @Mock
    private WorkflowPersistHandler persistHandler;

    @InjectMocks
    private WorkflowEngine engine;

    /**
     * 把业务时钟钉死的引擎 —— 让「今天」在测试里成为<b>可复现的事实</b>，
     * 而不是「用和实现一模一样的表达式算出来的同义反复」。
     */
    private static final class FixedClockEngine extends WorkflowEngine {

        private final Clock clock;

        FixedClockEngine(AgentRuntime agentRuntime, ToolExecutor toolExecutor,
                        WorkflowPersistHandler persistHandler, Clock clock) {
            super(agentRuntime, toolExecutor, persistHandler);
            this.clock = clock;
        }

        @Override
        protected Clock clock() {
            return clock;
        }
    }

    private WorkflowEngine engineWithFixedClock() {
        return new FixedClockEngine(agentRuntime, toolExecutor, persistHandler, FIXED_CLOCK);
    }

    private void stubTools() throws Exception {
        when(toolExecutor.execute(eq("query_bank_flow"), anyString(), any())).thenReturn(BANK_FLOW_JSON);
        when(toolExecutor.execute(eq("query_voucher"), anyString(), any())).thenReturn(VOUCHER_JSON);
    }

    private void stubLlm() {
        when(agentRuntime.run(any())).thenReturn(AgentRuntime.AgentTaskResult.builder()
                .success(true).content("今日摘要").model("MiniMax-M2.1").build());
    }

    private WorkflowEngine.WorkflowDefinition def(String dagJson) {
        WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
        def.setDagJson(dagJson);
        def.setCompanyId(2L);
        def.setUserId(0L);
        return def;
    }

    @Test
    @DisplayName("TOOL 节点把声明的每个工具都调一遍")
    void toolNode_callsEveryDeclaredTool() throws Exception {
        stubTools();

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL), Map.of());

        assertThat(result.isSuccess()).isTrue();
        verify(toolExecutor).execute(eq("query_bank_flow"), anyString(), any());
        verify(toolExecutor).execute(eq("query_voucher"), anyString(), any());
        verify(toolExecutor, times(2)).execute(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("TOOL 节点的产出落在节点 id 下（按工具名嵌套），单份工具结果另存一份")
    void toolNode_putsMergedResultUnderNodeId() throws Exception {
        stubTools();

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL), Map.of());

        Map<String, Object> outputs = result.getOutputs();
        assertThat(outputs).containsKeys("n1", "n1.query_bank_flow", "n1.query_voucher");

        String merged = (String) outputs.get("n1");
        assertThat(merged).contains("query_bank_flow").contains("query_voucher");
        assertThat(merged).contains("51892.00").contains("35400.00");

        assertThat(outputs.get("n1.query_bank_flow")).isEqualTo(BANK_FLOW_JSON);
        assertThat(outputs.get("n1.query_voucher")).isEqualTo(VOUCHER_JSON);
    }

    @Test
    @DisplayName("R-19：金额的 scale 2 不能在引擎这一跳被抹成 51892.0")
    void toolNode_preservesBigDecimalScale() throws Exception {
        stubTools();

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL), Map.of());

        String merged = (String) result.getOutputs().get("n1");
        assertThat(merged).contains("\"totalIn\":51892.00");
        assertThat(merged).doesNotContain("\"totalIn\":51892.0,");
        assertThat(merged).doesNotContain("\"totalIn\":51892.0}");
    }

    @Test
    @DisplayName("工具的归属来自工作流定义（不是 dag）")
    void toolNode_usesCompanyIdFromDefinition() throws Exception {
        stubTools();

        engine.run(def(DAG_TOOL), Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(toolExecutor).execute(eq("query_bank_flow"), anyString(), captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("args 里的 ${monthAgo} 被渲染成真实日期（业务时区口径），不留下原样占位符")
    void toolNode_rendersDateVariablesInArgs() throws Exception {
        stubTools();

        engineWithFixedClock().run(def(DAG_TOOL), Map.of());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(toolExecutor).execute(eq("query_bank_flow"), captor.capture(), any());
        String argsJson = captor.getValue();

        // 固定时钟的业务「今天」= 2026-03-08 → monthAgo = 2026-02-06
        assertThat(argsJson).doesNotContain("${");
        assertThat(argsJson).contains("2026-02-06");
    }

    @Test
    @DisplayName("F2：内置日期变量按业务时区算 —— 同一个此刻，UTC 是 03-07，北京是 03-08")
    void toolNode_injectsBuiltinDateVariables() throws Exception {
        stubTools();
        stubLlm();

        engineWithFixedClock().run(def(DAG_TOOL_AND_LLM), Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(agentRuntime).run(captor.capture());
        String prompt = captor.getValue().getUserInput();

        assertThat(prompt).isEqualTo("昨天 2026-03-07，今天 2026-03-08，"
                + "一周前 2026-03-01，一月前 2026-02-06，公司 2");
    }

    @Test
    @DisplayName("F2：businessToday 认业务时区，不认 JVM 默认时区")
    void businessToday_usesBusinessZoneNotSystemDefault() {
        // 2026-11-02T16:30Z → UTC 是 11-02，北京是 11-03（差一天）
        Clock fixed = Clock.fixed(Instant.parse("2026-11-02T16:30:00Z"), ZoneId.of("UTC"));

        assertThat(WorkflowEngine.BUSINESS_ZONE.getId()).isEqualTo("Asia/Shanghai");
        assertThat(WorkflowEngine.businessToday(fixed)).isEqualTo(LocalDate.of(2026, 11, 3));
        // 对照：同一个此刻按 UTC 算是 11-02 —— 少一天正是要防的那件事
        assertThat(LocalDate.now(fixed)).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    @DisplayName("TOOL 节点没声明 tools → run 失败")
    void toolNode_withoutTools_fails() {
        WorkflowEngine.WorkflowResult result = engine.run(
                def("{\"nodes\":[{\"id\":\"n1\",\"type\":\"TOOL\"}],\"edges\":[]}"), Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("没有声明 tools");
    }

    @Test
    @DisplayName("工具抛异常 → run 失败（TOOL 失败是硬失败，不吞）")
    void toolNode_toolThrows_failsRun() throws Exception {
        when(toolExecutor.execute(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("工具炸了"));

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL), Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("工具炸了");
    }

    @Test
    @DisplayName("F3：工具返回的不是 JSON → 整条 run 失败，不许聚合出「0 笔」")
    void toolNode_unparsableResult_failsRunWithoutFakeZeros() throws Exception {
        when(toolExecutor.execute(eq("query_bank_flow"), anyString(), any())).thenReturn("这不是 JSON");
        when(toolExecutor.execute(eq("query_voucher"), anyString(), any())).thenReturn(VOUCHER_JSON);
        // LLM 节点也失败 —— 这样「若 TOOL 节点没硬失败」的话，
        // 确定性摘要就会拿「解析不出来」的数据去聚合，聚合器会写出「共 0 笔」。
        when(agentRuntime.run(any())).thenThrow(new RuntimeException("provider 超时"));

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL_AND_LLM), Map.of());

        // 先查「不许出现编出来的零」—— 这条守的是「解析失败却把 run 放过去、
        // 让聚合器拿空数据写出『共 0 笔』」的那种回归。
        result.getOutputs().values()
                .forEach(v -> assertThat(String.valueOf(v)).doesNotContain("0 笔"));
        assertThat(result.getOutputs()).doesNotContainKey("n1");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("不是 JSON 对象");
    }

    @Test
    @DisplayName("F3：工具返回的是合法 JSON 但字段缺失 → 仍按 0 处理（与「解析失败」分得开）")
    void toolNode_missingFields_stillParses() throws Exception {
        when(toolExecutor.execute(eq("query_bank_flow"), anyString(), any()))
                .thenReturn("{\"source\":\"opc_finance_bank_flow\",\"companyId\":2}");
        when(toolExecutor.execute(eq("query_voucher"), anyString(), any())).thenReturn(VOUCHER_JSON);

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL), Map.of());

        assertThat(result.isSuccess()).isTrue();
        String merged = (String) result.getOutputs().get("n1");
        assertThat(merged).contains("query_bank_flow").contains("query_voucher");
    }

    @Test
    @DisplayName("节点级 stepTimeoutSeconds 传到 AgentTaskRequest；没写就是 null（走默认 30）")
    void llmNode_usesNodeLevelTimeout() {
        stubLlm();

        engine.run(def("{\"nodes\":[{\"id\":\"n1\",\"type\":\"LLM\","
                + "\"prompt\":\"写日报\",\"stepTimeoutSeconds\":60,\"config\":{}}],\"edges\":[]}"), Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(agentRuntime).run(captor.capture());
        assertThat(captor.getValue().getStepTimeoutSeconds()).isEqualTo(60);

        // 没写节点的 stepTimeoutSeconds
        engine.run(def("{\"nodes\":[{\"id\":\"n1\",\"type\":\"LLM\","
                + "\"prompt\":\"写日报\",\"config\":{}}],\"edges\":[]}"), Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor2 =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(agentRuntime, times(2)).run(captor2.capture());
        assertThat(captor2.getAllValues().get(1).getStepTimeoutSeconds()).isNull();
    }

    @Test
    @DisplayName("R-20：工作流的 LLM 节点不写记忆（整份财务数据不进长期记忆库）")
    void llmNode_skipsMemory() {
        stubLlm();

        engine.run(def("{\"nodes\":[{\"id\":\"n1\",\"type\":\"LLM\","
                + "\"prompt\":\"写日报\",\"config\":{}}],\"edges\":[]}"), Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(agentRuntime).run(captor.capture());
        assertThat(captor.getValue().getSkipMemory()).isTrue();
    }

    @Test
    @DisplayName("R-21：LLM 挂了（OpcException）就降级继续 —— 整条 run 不死，摘要用工具返回里的真实数字")
    void llmNode_failure_becomesPartialWithDeterministicSummary() throws Exception {
        stubTools();
        when(agentRuntime.run(any())).thenThrow(new OpcException("请求被安全策略拒绝：疑似提示注入"));

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL_AND_LLM), Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isPartial()).isTrue();
        assertThat(result.getLlmError()).contains("疑似提示注入");

        String summary = (String) result.getOutputs().get("n2");
        assertThat(summary).isNotBlank();
        assertThat(summary).startsWith("[自动聚合·未走 LLM]");
        assertThat(summary).contains("51892.00").contains("32430.00");
    }

    @Test
    @DisplayName("F1：LLM 抛的不是 OpcException（provider 超时）也一样软失败 —— catch 不能只 catch OpcException")
    void llmNode_failureWithPlainRuntimeException_becomesPartial() throws Exception {
        stubTools();
        when(agentRuntime.run(any())).thenThrow(new RuntimeException("provider 超时"));

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL_AND_LLM), Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isPartial()).isTrue();
        assertThat(result.getLlmError()).contains("provider 超时");

        String summary = (String) result.getOutputs().get("n2");
        assertThat(summary).isNotBlank();
        assertThat(summary).startsWith("[自动聚合·未走 LLM]");
        assertThat(summary).contains("51892.00");
    }

    @Test
    @DisplayName("LLM 报成功但正文是空的 → 降级成 PARTIAL，摘要用确定性聚合（不许把 null 塞进产出物）")
    void llmNode_blankContent_becomesPartialWithDeterministicSummary() throws Exception {
        stubTools();
        // 2026-11-05 线上就是这个形状：M3.1-Flash 的思考吃满了 max_tokens，
        // 响应体里没有 content 字段 —— provider「成功」、内容却是 null。
        when(agentRuntime.run(any())).thenReturn(AgentRuntime.AgentTaskResult.builder()
                .success(true).content(null).model("MiniMax-M3.1-Flash").build());

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL_AND_LLM), Map.of());

        // 以前：outputs["n2"] = null，run 记成功 → 真正炸掉的地方在 persist 层，
        // 错误消息还写着「产出不是字符串: n2」，指向了一个完全无辜的地方。
        assertThat(result.isPartial()).isTrue();
        String summary = (String) result.getOutputs().get("n2");
        assertThat(summary).isNotBlank();
        assertThat(summary).startsWith("[自动聚合·未走 LLM]");
        assertThat(summary).contains("51892.00");
    }

    @Test
    @DisplayName("TOOL 失败 → 整条 run 失败（不会拿半截数据去写日报）")
    void toolNode_failure_doesNotProducePartialSummary() throws Exception {
        when(toolExecutor.execute(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("finance 不可用"));

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL_AND_LLM), Map.of());

        assertThat(result.isSuccess()).isFalse();
        verify(agentRuntime, never()).run(any());
    }

}
