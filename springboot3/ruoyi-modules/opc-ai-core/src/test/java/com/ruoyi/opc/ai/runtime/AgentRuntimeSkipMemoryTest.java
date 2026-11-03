package com.ruoyi.opc.ai.runtime;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.ai.memory.Memory;
import com.ruoyi.opc.ai.security.PromptGuard;
import com.ruoyi.opc.ai.tools.ToolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-20：工作流的 LLM 节点<b>不写记忆</b>。
 *
 * <p><b>为什么</b>：工作流 LLM 节点的 {@code userInput} 是<b>渲染后的完整 prompt</b>，
 * 里面内联了整份财务 JSON（真流水明细、金额、对手方名）。走记忆的话，这段数据会被写进
 * Qdrant 的长期记忆 —— 以后同一租户的<b>任何</b>普通聊天都可能检索到这段「历史记忆」，
 * 把「昨天日报的数字」当成历史事实说出来。
 *
 * <p>短期记忆也一并跳过：工作流的 sessionId 每次都是新的（{@code wf-<nodeId>-<毫秒>}），
 * 对它零检索价值，却会把整份财务 JSON 写进 Redis 一个没人再读的 key 里。
 *
 * <p><b>但安全校验不跳</b>：{@code PromptGuard.sanitize} 照跑（工具返回的数据里如果混进
 * 「忽略之前所有指令」，就该被拒）。{@code skipMemory} 只关记忆。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRuntime skipMemory 单测")
class AgentRuntimeSkipMemoryTest {

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private Memory memory;

    @Mock
    private ToolExecutor toolExecutor;

    @Mock
    private PromptGuard promptGuard;

    @InjectMocks
    private AgentRuntime runtime;

    private void stubLlm() {
        when(llmGateway.chat(any(), any(), any())).thenReturn(ChatResponse.builder()
                .success(true).content("日报正文").model("MiniMax-M2.1").build());
        when(promptGuard.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(memory.searchLongTerm(any(), any(), anyInt())).thenReturn(List.of());
        when(memory.loadShortTerm(anyString(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("skipMemory=true → 记忆的四个方法一次都不调")
    void skipMemory_true_neverTouchesMemory() {
        stubLlm();

        runtime.run(AgentRuntime.AgentTaskRequest.builder()
                .userInput("把这份流水写成日报")
                .companyId(2L)
                .sessionId("wf-n2-1762000000000")
                .skipMemory(Boolean.TRUE)
                .build());

        verify(memory, never()).searchLongTerm(any(), any(), anyInt());
        verify(memory, never()).loadShortTerm(any(), anyInt());
        verify(memory, never()).appendShortTerm(any(), any());
        verify(memory, never()).writeLongTerm(any(), any(), any(), any());
    }

    @Test
    @DisplayName("skipMemory=true → 安全校验照跑（不是绕过 PromptGuard）")
    void skipMemory_true_stillRunsPromptGuard() {
        stubLlm();

        runtime.run(AgentRuntime.AgentTaskRequest.builder()
                .userInput("把这份流水写成日报")
                .companyId(2L)
                .sessionId("wf-n2-1762000000000")
                .skipMemory(Boolean.TRUE)
                .build());

        verify(promptGuard).sanitize("把这份流水写成日报");
    }

    @Test
    @DisplayName("skipMemory 不设（null）→ 行为跟以前一样：记忆照读照写")
    void skipMemory_null_keepsMemoryBehaviour() {
        stubLlm();

        runtime.run(AgentRuntime.AgentTaskRequest.builder()
                .userInput("把这份流水写成日报")
                .companyId(2L)
                .sessionId("wf-n2-1762000000000")
                .build());

        verify(memory).searchLongTerm(eq("2"), eq("把这份流水写成日报"), eq(5));
        verify(memory).loadShortTerm(eq("wf-n2-1762000000000"), eq(10));
        // 用户那句 + 助手那句
        verify(memory, times(2)).appendShortTerm(eq("wf-n2-1762000000000"), any(ChatMessage.class));
        verify(memory).writeLongTerm(eq("wf-n2-1762000000000"), eq("2"), anyString(), any());
    }

    @Test
    @DisplayName("结果里带上模型名 —— llm_used 要记「这次真的调了哪个模型」")
    void result_carriesModelName() {
        stubLlm();

        AgentRuntime.AgentTaskResult result = runtime.run(AgentRuntime.AgentTaskRequest.builder()
                .userInput("把这份流水写成日报")
                .companyId(2L)
                .sessionId("wf-n2-1762000000000")
                .skipMemory(Boolean.TRUE)
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getModel()).isEqualTo("MiniMax-M2.1");
    }

}
