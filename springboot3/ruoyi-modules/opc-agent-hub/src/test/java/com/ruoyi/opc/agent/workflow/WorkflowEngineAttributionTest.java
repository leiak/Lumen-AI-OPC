package com.ruoyi.opc.agent.workflow;

import com.ruoyi.opc.ai.runtime.AgentRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowEngine} 的 LLM 节点归属回归测试 —— 3b
 *
 * <p><b>缺陷</b>：LLM 节点构造 {@code AgentTaskRequest} 时<b>不带任何归属</b>
 * （companyId / userId 都是 null）。工作流是定时触发的（{@code @InnerAuth}，
 * 只有 Quartz 能调），本来就没有登录态 —— 于是这条链路上的每次 LLM 调用，
 * 到了 {@code TokenMeter} 那里既没有 ctx 归属、也没有登录态可补，
 * 整条用量记录被丢掉：工作流烧掉的 token/成本在账单里完全看不见。</p>
 *
 * <p>修法：归属由 {@link WorkflowEngine.WorkflowDefinition} 带进来，
 * 引擎再把它放进 {@code AgentTaskRequest}。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEngine LLM 节点归属 单测")
class WorkflowEngineAttributionTest {

    private static final String DAG_WITH_LLM =
            "{\"nodes\":[{\"id\":\"n1\",\"type\":\"LLM\",\"prompt\":\"生成今日摘要\",\"config\":{}}],\"edges\":[]}";

    @Mock
    private AgentRuntime agentRuntime;

    @InjectMocks
    private WorkflowEngine engine;

    private void stubRuntime() {
        when(agentRuntime.run(any())).thenReturn(AgentRuntime.AgentTaskResult.builder()
                .success(true).content("今日摘要").build());
    }

    @Test
    @DisplayName("LLM 节点把定义的 companyId/userId 带到 AgentTaskRequest")
    void llmNode_carriesAttribution() {
        stubRuntime();

        WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
        def.setDagJson(DAG_WITH_LLM);
        def.setCompanyId(7L);
        def.setUserId(0L);

        engine.run(def, Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(agentRuntime).run(captor.capture());

        assertThat(captor.getValue().getCompanyId()).isEqualTo(7L);
        assertThat(captor.getValue().getUserId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("定义没带归属 → 请求里就是 null(不编造, 也不静默兜成别的公司)")
    void llmNode_withoutAttribution_staysNull() {
        stubRuntime();

        WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
        def.setDagJson(DAG_WITH_LLM);

        engine.run(def, Map.of());

        ArgumentCaptor<AgentRuntime.AgentTaskRequest> captor =
                ArgumentCaptor.forClass(AgentRuntime.AgentTaskRequest.class);
        verify(agentRuntime).run(captor.capture());

        assertThat(captor.getValue().getCompanyId()).isNull();
        assertThat(captor.getValue().getUserId()).isNull();
    }
}
