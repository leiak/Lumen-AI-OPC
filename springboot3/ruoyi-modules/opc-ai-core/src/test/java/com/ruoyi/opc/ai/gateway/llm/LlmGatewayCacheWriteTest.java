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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmGateway} 的<b>缓存写入时机</b>回归测试。
 *
 * <p><b>为什么必须有这条测试</b>：缓存的 TTL 是 3600 秒。
 * 一旦<b>失败响应</b>被写进缓存，同一个 prompt 一小时内会直接命中「失败缓存」，
 * 用户拿到的是<b>缓存里的错误</b>，而且<b>根本不会再去打 provider</b> ——
 * 比不缓存更糟（不缓存至少还会重试一次）。</p>
 *
 * <p>在此之前，{@code LlmGatewayCacheMeteringTest} 只 verify 了
 * {@code cache.buildKey} 的次数，<b>从没 verify 过 {@code cache.put}</b> ——
 * 把 {@code cache.put} 挪到每次 attempt 都执行，147 条测试仍然全绿。</p>
 *
 * <p><b>必须 verify 的是 {@code put} 本身</b>：只断言「provider 没被调」之类的
 * 间接现象，挡不住「失败响应被缓存」这个具体的病。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmGateway 缓存写入时机 单测")
class LlmGatewayCacheWriteTest {

    @Mock
    private ChatModelProvider provider;

    @Mock
    private TokenMeter tokenMeter;

    @Mock
    private LlmCache cache;

    private LlmGateway gateway() {
        return new LlmGateway(List.of(provider), tokenMeter, cache);
    }

    private void enableProvider() {
        when(cache.get(any())).thenReturn(null);
        when(provider.enabled()).thenReturn(true);
        when(provider.model()).thenReturn("deepseek-v3");
    }

    @Test
    @DisplayName("provider 全部失败 → 一次都不写缓存（失败响应进缓存 = 把错误缓存一小时）")
    void chat_failure_doesNotWriteCache() {
        enableProvider();
        when(provider.chat(anyList(), any())).thenReturn(ChatResponse.builder()
                .model("deepseek-v3")
                .success(false)
                .httpStatus(503)
                .errorMessage("服务端临时故障")
                .build());

        LlmGateway gw = gateway();

        assertThrows(OpcException.class,
                () -> gw.chat(List.of(ChatMessage.user("hi")), null, null),
                "provider 全失败时应该抛 OpcException");

        verify(cache, never()).put(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("provider 成功 → 缓存只写一次")
    void chat_success_writesCacheOnce() {
        enableProvider();
        when(provider.chat(anyList(), any())).thenReturn(ChatResponse.builder()
                .model("deepseek-v3")
                .content("答案")
                .success(true)
                .tokenInput(1)
                .tokenOutput(1)
                .build());

        LlmGateway gw = gateway();

        gw.chat(List.of(ChatMessage.user("hi")), null, null);

        verify(cache, times(1)).put(any(), any(), anyDouble());
    }
}
