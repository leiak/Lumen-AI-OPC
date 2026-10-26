package com.ruoyi.opc.ai.gateway.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmGateway} 缓存命中路径的计量回归测试 —— 3b
 *
 * <p><b>缺陷</b>：缓存命中时 {@code LlmGateway.chat} 直接 return，
 * 根本没调 {@code tokenMeter.record(...)} —— 于是这次调用的 token / 成本
 * 在 {@code opc_agent_token_usage} 里<b>一行都没有</b>。
 * 而账单页（{@code /opc/finance/agg/token-usage}）读的正是那张表：
 * 缓存越命中，账单越空。</p>
 *
 * <p>修法：缓存命中也要计量，<b>token 数照记、费用记 0</b> ——
 * 缓存的意义是「同样的 prompt 不重复付 provider 的钱」，不是「不记账」。</p>
 *
 * <p>顺带锁住另一件事：缓存 key 必须<b>按租户隔离</b>（见 {@link LlmCacheTest}）。
 * 否则 A 公司付的钱让 B 公司白嫖，两家的用量数字还互相依赖。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmGateway 缓存命中计量 单测")
class LlmGatewayCacheMeteringTest {

    @Mock
    private ChatModelProvider provider;

    @Mock
    private TokenMeter tokenMeter;

    @Mock
    private LlmCache cache;

    private LlmGateway gateway;

    private void build() {
        gateway = new LlmGateway(List.of(provider), tokenMeter, cache);
    }

    private ChatResponse cachedResponse() {
        return ChatResponse.builder()
                .content("缓存里的答案")
                .model("deepseek-v3")
                .success(true)
                .tokenInput(100)
                .tokenOutput(50)
                .build();
    }

    @Test
    @DisplayName("缓存命中 → 照样计量(fromCache=true), provider 一次都不调")
    void cacheHit_isMetered() {
        build();
        when(cache.get(any())).thenReturn(cachedResponse());

        LlmGateway.ChatContext ctx = LlmGateway.ChatContext.builder()
                .companyId(7L).userId(42L).scene("unit").build();

        ChatResponse resp = gateway.chat(List.of(ChatMessage.user("hi")), null, ctx);

        assertThat(resp.getContent()).isEqualTo("缓存里的答案");
        assertThat(resp.getRequestId()).startsWith("cache-");
        verify(tokenMeter).record(eq(ctx), eq("deepseek-v3"), any(ChatResponse.class), eq(true));
        verify(provider, never()).chat(anyList(), any());
    }

    @Test
    @DisplayName("没有归属 (ctx=null) 时不计量, 也不炸")
    void cacheHit_withoutContext_doesNotMeter() {
        build();
        when(cache.get(any())).thenReturn(cachedResponse());

        gateway.chat(List.of(ChatMessage.user("hi")), null, null);

        verify(tokenMeter, never()).record(any(), any(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("缓存 key 带上租户: 不同公司算到不同的 key 上")
    void cacheKey_isCompanyScoped() {
        build();
        when(cache.get(any())).thenReturn(null);
        when(provider.enabled()).thenReturn(true);
        when(provider.model()).thenReturn("deepseek-v3");
        when(provider.chat(anyList(), any())).thenReturn(ChatResponse.builder()
                .content("x").success(true).tokenInput(1).tokenOutput(1).build());

        gateway.chat(List.of(ChatMessage.user("hi")), null,
                LlmGateway.ChatContext.builder().companyId(1L).userId(1L).build());
        gateway.chat(List.of(ChatMessage.user("hi")), null,
                LlmGateway.ChatContext.builder().companyId(2L).userId(2L).build());

        ArgumentCaptor<Long> companyCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cache, times(2)).buildKey(anyList(), any(), companyCaptor.capture());

        assertThat(companyCaptor.getAllValues()).containsExactly(1L, 2L);
    }
}
