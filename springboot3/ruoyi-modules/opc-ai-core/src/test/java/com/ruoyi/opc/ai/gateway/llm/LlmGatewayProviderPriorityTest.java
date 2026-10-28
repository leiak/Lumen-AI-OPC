package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmGateway} 的 provider 优先级回归测试。
 *
 * <p><b>修的 bug</b>：以前 {@code chat()} 直接遍历 Spring 注入顺序的 providers，
 * 而注入顺序取决于类名/注册顺序，<b>和 priority 无关</b>。后果是配置里写着
 * "MiniMax 优先"、实际先打的却是 DeepSeek —— 配置说了不算，而日志里看不出异常。
 * {@code chatStream()} 走 {@code pickPrimary()}（排序过），两条路行为一直不一致。</p>
 *
 * <p>本测试锁住：<b>无论注入顺序如何，chat 必须先打 priority 最小的那个</b>。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmGateway provider 优先级 单测")
class LlmGatewayProviderPriorityTest {

    @Mock
    private ChatModelProvider highPriority;  // priority = 5
    @Mock
    private ChatModelProvider lowPriority;   // priority = 10
    @Mock
    private TokenMeter tokenMeter;
    @Mock
    private LlmCache cache;

    private LlmGateway gateway(List<ChatModelProvider> providers) {
        return new LlmGateway(providers, tokenMeter, cache);
    }

    private void enable(ChatModelProvider p, int priority, String model) {
        when(p.enabled()).thenReturn(true);
        when(p.priority()).thenReturn(priority);
        when(p.model()).thenReturn(model);
    }

    private ChatResponse ok(String content) {
        return ChatResponse.builder()
                .content(content)
                .success(true)
                .tokenInput(10)
                .tokenOutput(5)
                .build();
    }

    @Test
    @DisplayName("注入顺序与优先级相反时 → 仍然先打 priority 最小的 provider")
    void chat_prefersHighestPriorityEvenIfInjectedLast() {
        enable(highPriority, 5, "MiniMax-Text-01");
        enable(lowPriority, 10, "deepseek-v3");
        when(highPriority.chat(anyList(), any())).thenReturn(ok("主模型的答案"));

        // 故意按"错"的顺序注入
        LlmGateway gw = gateway(List.of(lowPriority, highPriority));

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("主模型的答案", resp.getContent());
        verify(highPriority).chat(anyList(), any());
        verify(lowPriority, never()).chat(anyList(), any());
    }

    @Test
    @DisplayName("高优先级 provider 失败 → 回退到下一个")
    void chat_fallsBackToNextProviderOnFailure() {
        enable(highPriority, 5, "MiniMax-Text-01");
        enable(lowPriority, 10, "deepseek-v3");
        when(highPriority.chat(anyList(), any())).thenThrow(new RuntimeException("502 upstream"));
        when(lowPriority.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway(List.of(highPriority, lowPriority));

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        verify(highPriority).chat(anyList(), any());
        verify(lowPriority).chat(anyList(), any());
    }

    @Test
    @DisplayName("高优先级 provider 返回 success=false → 同样回退")
    void chat_fallsBackWhenProviderReturnsFailure() {
        enable(highPriority, 5, "MiniMax-Text-01");
        enable(lowPriority, 10, "deepseek-v3");
        when(highPriority.chat(anyList(), any())).thenReturn(ChatResponse.builder()
                .model("MiniMax-Text-01").success(false).errorMessage("rate limited").build());
        when(lowPriority.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway(List.of(highPriority, lowPriority));

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        verify(lowPriority).chat(anyList(), any());
    }

    @Test
    @DisplayName("高优先级 provider 被禁用 → 直接跳到下一个")
    void chat_skipsDisabledProvider() {
        when(highPriority.enabled()).thenReturn(false);
        enable(lowPriority, 10, "deepseek-v3");
        when(lowPriority.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway(List.of(highPriority, lowPriority));

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        verify(highPriority, never()).chat(anyList(), any());
        verify(lowPriority).chat(anyList(), any());
    }

    @Test
    @DisplayName("所有 provider 都失败 → 抛 OpcException")
    void chat_allProvidersFail_throwsOpcException() {
        enable(highPriority, 5, "MiniMax-Text-01");
        enable(lowPriority, 10, "deepseek-v3");
        when(highPriority.chat(anyList(), any())).thenThrow(new RuntimeException("boom-1"));
        when(lowPriority.chat(anyList(), any())).thenThrow(new RuntimeException("boom-2"));

        LlmGateway gw = gateway(List.of(highPriority, lowPriority));

        OpcException e = assertThrows(OpcException.class,
                () -> gw.chat(List.of(ChatMessage.user("hi")), null, null));

        assertTrue(e.getMessage().contains("boom"),
                "异常要带上最后一个真实错误，便于定位：" + e.getMessage());
    }

    @Test
    @DisplayName("chatStream 也挑 priority 最小的那个（与 chat 保持一致）")
    void chatStream_picksHighestPriorityProvider() {
        enable(highPriority, 5, "MiniMax-Text-01");
        enable(lowPriority, 10, "deepseek-v3");

        LlmGateway gw = gateway(List.of(lowPriority, highPriority));

        gw.chatStream(List.of(ChatMessage.user("hi")), null, null, new ChatModelProvider.StreamCallback() {
            @Override public void onChunk(String chunk) { }
            @Override public void onComplete(ChatResponse response) { }
            @Override public void onError(Throwable t) { }
        });

        verify(highPriority).chatStream(anyList(), any(), any());
        verify(lowPriority, never()).chatStream(anyList(), any(), any());
    }
}
