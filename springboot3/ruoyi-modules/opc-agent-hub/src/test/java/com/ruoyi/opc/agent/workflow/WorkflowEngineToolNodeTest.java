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
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEngine TOOL 节点 + 内置日期变量 单测")
class WorkflowEngineToolNodeTest {

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

    @InjectMocks
    private WorkflowEngine engine;

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
    @DisplayName("args 里的 ${monthAgo} 被渲染成真实日期（北京时间口径），不留下原样占位符")
    void toolNode_rendersDateVariablesInArgs() throws Exception {
        stubTools();

        engine.run(def(DAG_TOOL), Map.of());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(toolExecutor).execute(eq("query_bank_flow"), captor.capture(), any());
        String argsJson = captor.getValue();

        String expectedMonthAgo = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(30).toString();
        assertThat(argsJson).doesNotContain("${");
        assertThat(argsJson).contains(expectedMonthAgo);
    }

    @Test
    @DisplayName("LLM 节点的 prompt 里注入内置日期变量（today/yesterday/weekAgo/monthAgo）+ companyId")
    void toolNode_injectsBuiltinDateVariables() throws Exception {
        stubTools();
        stubLlm();

        engine.run(def(DAG_TOOL_AND_LLM), Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(agentRuntime).run(captor.capture());
        String prompt = captor.getValue().getUserInput();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertThat(prompt).doesNotContain("${");
        assertThat(prompt).contains(today.toString());
        assertThat(prompt).contains(today.minusDays(1).toString());
        assertThat(prompt).contains(today.minusDays(7).toString());
        assertThat(prompt).contains(today.minusDays(30).toString());
        assertThat(prompt).contains("公司 2");
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
    @DisplayName("R-21：LLM 挂了就降级继续 —— 整条 run 不死，摘要用工具返回里的真实数字")
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
    @DisplayName("TOOL 失败 → 整条 run 失败（不会拿半截数据去写日报）")
    void toolNode_failure_doesNotProducePartialSummary() throws Exception {
        when(toolExecutor.execute(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("finance 不可用"));

        WorkflowEngine.WorkflowResult result = engine.run(def(DAG_TOOL_AND_LLM), Map.of());

        assertThat(result.isSuccess()).isFalse();
        verify(agentRuntime, never()).run(any());
    }

}
