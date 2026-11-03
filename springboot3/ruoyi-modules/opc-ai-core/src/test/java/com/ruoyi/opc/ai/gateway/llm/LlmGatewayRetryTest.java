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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmGateway} 的重试语义回归测试。
 *
 * <p><b>修的 bug</b>：{@code opc.llm.max-retries: 3} 这个键在 4 个 yml 里写着，
 * 全仓却没有一行 Java 读它 —— 重试数实际是 <b>0</b>。MiniMax 的 Token Plan
 * 10-30 / 10-31 / 11-01 连续三天 429，11-02 又被 30 秒超时掐断，
 * 一次失败整条链报废。</p>
 *
 * <p><b>两条失败路径必须分清</b>：</p>
 * <ul>
 *   <li><b>抛异常</b> → 无条件重试（没拿到 HTTP 响应，无从判断是不是配额问题）。</li>
 *   <li><b>返回失败响应</b> → 429 与其它 4xx 不重试（再撞一次也是同样结果），
 *       5xx 与「没有状态码的未知错误」才重试。</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmGateway 重试与降级 单测")
class LlmGatewayRetryTest {

    @Mock
    private ChatModelProvider primary;   // priority = 5
    @Mock
    private ChatModelProvider backup;    // priority = 10
    @Mock
    private TokenMeter tokenMeter;
    @Mock
    private LlmCache cache;

    private LlmGateway gateway() {
        return new LlmGateway(List.of(primary, backup), tokenMeter, cache);
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

    /** 失败响应：httpStatus 为 null 表示「没拿到 HTTP 响应」→ 按可重试处理。 */
    private ChatResponse fail(String errorMessage) {
        return ChatResponse.builder()
                .model("MiniMax-Text-01")
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /** 失败响应：带 HTTP 状态码。 */
    private ChatResponse fail(int httpStatus, String errorMessage) {
        return ChatResponse.builder()
                .model("MiniMax-Text-01")
                .success(false)
                .httpStatus(httpStatus)
                .errorMessage(errorMessage)
                .build();
    }

    @Test
    @DisplayName("provider 抛异常（超时）→ 重试，第二次成功就返回")
    void chat_retriesOnTimeout() {
        enable(primary, 5, "MiniMax-Text-01");
        enable(backup, 10, "deepseek-v3");
        // 注意：这里用 RuntimeException 而不是 HttpTimeoutException ——
        // ChatModelProvider.chat 不声明 checked exception，Mockito 会直接拒绝那个 stub。
        when(primary.chat(anyList(), any()))
                .thenThrow(new RuntimeException("read timed out"))
                .thenReturn(ok("重试后拿到的答案"));

        LlmGateway gw = gateway();

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("重试后拿到的答案", resp.getContent());
        verify(primary, times(2)).chat(anyList(), any());
        verify(backup, never()).chat(anyList(), any());
    }

    @Test
    @DisplayName("httpStatus=429 → 不重试，直接换下一个 provider")
    void chat_doesNotRetryOn429() {
        enable(primary, 5, "MiniMax-Text-01");
        enable(backup, 10, "deepseek-v3");
        when(primary.chat(anyList(), any())).thenReturn(fail(429, "配额用尽"));
        when(backup.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway();

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        // 429 是配额问题，重试只会再撞一次 → 只调 1 次
        verify(primary, times(1)).chat(anyList(), any());
        verify(backup, times(1)).chat(anyList(), any());
    }

    @Test
    @DisplayName("httpStatus=503 → 调满 maxRetries 次后才换 provider")
    void chat_retriesOn5xx() {
        enable(primary, 5, "MiniMax-Text-01");
        enable(backup, 10, "deepseek-v3");
        when(primary.chat(anyList(), any())).thenReturn(fail(503, "服务端临时故障"));
        when(backup.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway();

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        verify(primary, times(3)).chat(anyList(), any());
        verify(backup, times(1)).chat(anyList(), any());
    }

    @Test
    @DisplayName("第一个 provider 重试耗尽 → 第二个 provider 被调用")
    void chat_movesToNextProviderAfterRetriesExhausted() {
        enable(primary, 5, "MiniMax-Text-01");
        enable(backup, 10, "deepseek-v3");
        when(primary.chat(anyList(), any())).thenThrow(new RuntimeException("502 upstream"));
        when(backup.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway();

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        verify(primary, times(3)).chat(anyList(), any());
        verify(backup, times(1)).chat(anyList(), any());
    }

    @Test
    @DisplayName("所有 provider 都失败 → 抛 OpcException，消息带最后一个 provider 名与原因")
    void chat_allProvidersFail_throwsWithLastError() {
        enable(primary, 5, "MiniMax-Text-01");
        enable(backup, 10, "deepseek-v3");
        when(primary.name()).thenReturn("minimax");
        when(backup.name()).thenReturn("deepseek");
        when(primary.chat(anyList(), any())).thenReturn(fail(503, "主模型挂了"));
        when(backup.chat(anyList(), any())).thenReturn(fail(401, "备模型拒绝服务"));

        LlmGateway gw = gateway();

        OpcException e = assertThrows(OpcException.class,
                () -> gw.chat(List.of(ChatMessage.user("hi")), null, null));

        assertTrue(e.getMessage().contains("deepseek"),
                "异常要带上最后一个失败的 provider 名：" + e.getMessage());
        assertTrue(e.getMessage().contains("备模型拒绝服务"),
                "异常要带上最后一个 provider 的错误原因：" + e.getMessage());
    }

    @Test
    @DisplayName("httpStatus=401（4xx 非 429）→ 不重试，只调 1 次")
    void chat_doesNotRetryOn4xxOtherThan429() {
        enable(primary, 5, "MiniMax-Text-01");
        enable(backup, 10, "deepseek-v3");
        when(primary.chat(anyList(), any())).thenReturn(fail(401, "鉴权失败"));
        when(backup.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway();

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        verify(primary, times(1)).chat(anyList(), any());
        verify(backup, times(1)).chat(anyList(), any());
    }

    @Test
    @DisplayName("失败响应没有 httpStatus（未知错误）→ 算可重试，调满 3 次")
    void chat_failureWithoutHttpStatus_isRetried() {
        enable(primary, 5, "MiniMax-Text-01");
        enable(backup, 10, "deepseek-v3");
        when(primary.chat(anyList(), any())).thenReturn(fail("rate limited"));
        when(backup.chat(anyList(), any())).thenReturn(ok("备模型的答案"));

        LlmGateway gw = gateway();

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, null);

        assertEquals("备模型的答案", resp.getContent());
        // 没有状态码 = 不知道是不是配额问题 → 未知错误重试是合理的默认值
        verify(primary, times(3)).chat(anyList(), any());
        verify(backup, times(1)).chat(anyList(), any());
    }

    @Test
    @DisplayName("缓存命中 → provider 一次都不调，且照样计量(fromCache=true)")
    void chat_cacheHit_doesNotCallProvider() {
        enable(primary, 5, "MiniMax-Text-01");
        ChatResponse cached = ChatResponse.builder()
                .content("缓存里的答案")
                .model("deepseek-v3")
                .success(true)
                .tokenInput(100)
                .tokenOutput(50)
                .build();
        when(cache.get(any())).thenReturn(cached);

        LlmGateway gw = gateway();
        LlmGateway.ChatContext ctx = LlmGateway.ChatContext.builder()
                .companyId(7L).userId(42L).scene("unit").build();

        ChatResponse resp = gw.chat(List.of(ChatMessage.user("hi")), null, ctx);

        assertEquals("缓存里的答案", resp.getContent());
        verify(primary, never()).chat(anyList(), any());
        verify(tokenMeter).record(eq(ctx), eq("deepseek-v3"), any(ChatResponse.class), eq(true));
    }
}
