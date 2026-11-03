package com.ruoyi.opc.ai.gateway.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * {@link DeepSeekProvider} 把 HTTP 状态码透传到 {@link ChatResponse#getHttpStatus()} 的回归测试。
 *
 * <p><b>为什么必须有这条测试</b>：provider 里的 {@code .httpStatus(e.getStatusCode())}
 * 是「配额问题不重试」这条规则的<b>唯一输入</b>。少了它 → {@code httpStatus} 留 null →
 * {@code LlmGateway.isRetryable} 判成「状态码未知 → 可重试」→
 * <b>配额问题连撞 3 次</b>（MiniMax 的 Token Plan 10-30 / 10-31 / 11-01 连续三天 429，
 * 正是这个病）。而且这条链路此前<b>一条测试都没有</b>。</p>
 *
 * <p><b>为什么「网络错时状态码必须是 null」也要断言</b>：
 * {@code isRetryable} 判的是 {@code status == null}；
 * 设成 0 会被当成「4xx → 不重试」，方向<b>正好反了</b>（网络错恰恰该重试）。</p>
 *
 * <p><b>写 mock 时的坑</b>：{@code apiKey} / {@code baseUrl} / {@code modelName}
 * 都是 {@code @Value} 注入的私有字段，纯单测里是 null ——
 * 所以 stub 必须用 {@code any()} / {@code anyList()}，
 * <b>不能用 {@code anyString()}</b>（Mockito 2+ 的 {@code anyString()} 不匹配 null
 * → stub 不上 → 测试会红在一个假的地方）。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("DeepSeekProvider HTTP 状态码透传 单测")
class DeepSeekProviderTest {

    @Mock
    private HttpLlmClient http;

    private DeepSeekProvider provider() {
        return new DeepSeekProvider(http);
    }

    @Test
    @DisplayName("厂商返回 HTTP 错误 → 响应里带上结构化状态码，且 success=false")
    void chat_httpError_carriesHttpStatus() {
        when(http.callChatCompletions(any(), any(), any(), anyList(), any()))
                .thenThrow(new LlmHttpException(429, "HTTP 429 from https://api.deepseek.com/v1: rate limit"));

        ChatResponse resp = provider().chat(List.of(ChatMessage.user("hi")), null);

        assertEquals(429, resp.getHttpStatus(),
                "HTTP 错误必须把状态码落到 httpStatus —— 少了它上层会当成「未知错误」而重试");
        assertEquals(Boolean.FALSE, resp.getSuccess(), "失败响应必须 success=false");
    }

    @Test
    @DisplayName("网络错（没拿到 HTTP 响应）→ httpStatus 必须是 null，不能是 0 或 -1")
    void chat_networkError_httpStatusIsNull() {
        when(http.callChatCompletions(any(), any(), any(), anyList(), any()))
                .thenThrow(new RuntimeException("read timed out"));

        ChatResponse resp = provider().chat(List.of(ChatMessage.user("hi")), null);

        assertNull(resp.getHttpStatus(),
                "没拿到 HTTP 响应时状态码必须是 null：isRetryable 判的是 status == null，"
                        + "设成 0 会被当成「4xx → 不重试」，方向正好反了");
        assertEquals(Boolean.FALSE, resp.getSuccess(), "失败响应必须 success=false");
    }
}
