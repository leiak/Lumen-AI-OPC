package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.opc.common.constant.OpcConstants;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 网关（多模型路由 + 失败 fallback + 缓存 + 计量埋点）
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGateway {

    private final List<ChatModelProvider> providers;
    private final TokenMeter tokenMeter;
    private final LlmCache cache;

    /**
     * 统一聊天入口（带 fallback）
     */
    public ChatResponse chat(List<ChatMessage> messages, ChatModelProvider.ChatOptions options, ChatContext ctx) {
        // 1. 缓存命中
        //
        // 缓存 key 按租户隔离（见 LlmCache.buildKey）：同一家公司的同一段 prompt
        // 才复用 —— 否则 A 公司付的钱会让 B 公司白嫖，账单就没法对账。
        Long companyId = ctx != null ? ctx.getCompanyId() : null;
        String cacheKey = cache.buildKey(messages, options, companyId);
        ChatResponse cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("[LLM] 缓存命中 key={}", cacheKey);
            cached.setRequestId("cache-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            // 缓存命中**同样要计量** ——
            // 以前这里直接 return, 于是缓存命中的那次调用在 opc_agent_token_usage 里
            // 完全没有记录: token 数、成本、调用次数统统不计入账单。
            // 而缓存存在的意义是「不重复付 provider 的钱」, 不是「不记账」——
            // token 数照记, 费用记 0 (fromCache=true)。
            if (ctx != null) {
                tokenMeter.record(ctx, cached.getModel(), cached, true);
            }
            return cached;
        }

        // 2. 按优先级遍历 provider（主→备）
        ChatResponse lastError = null;
        for (ChatModelProvider provider : providers) {
            if (!provider.enabled()) continue;
            try {
                long start = System.currentTimeMillis();
                ChatResponse resp = provider.chat(messages, options);
                long cost = System.currentTimeMillis() - start;
                resp.setLatencyMs(cost);
                if (resp.getSuccess() == null || !resp.getSuccess()) {
                    lastError = resp;
                    log.warn("[LLM] provider={} 调用失败：{}", provider.name(), resp.getErrorMessage());
                    continue;
                }
                log.info("[LLM] provider={} 调用成功 cost={}ms tokens={}",
                        provider.name(), cost, resp.getTokenTotal());

                // 3. Token 计量埋点
                if (ctx != null) {
                    tokenMeter.record(ctx, provider.model(), resp);
                }

                // 4. 写缓存
                cache.put(cacheKey, resp, OpcConstants.PRICE_DEEPSEEK_OUTPUT);
                return resp;
            } catch (Exception e) {
                log.warn("[LLM] provider={} 异常：{}", provider.name(), e.getMessage());
                lastError = ChatResponse.builder()
                        .model(provider.model())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        }

        throw new OpcException("所有 LLM provider 失败，最后错误：" +
                (lastError != null ? lastError.getErrorMessage() : "unknown"));
    }

    /** 流式调用 */
    public void chatStream(List<ChatMessage> messages, ChatModelProvider.ChatOptions options,
                            ChatContext ctx, ChatModelProvider.StreamCallback callback) {
        ChatModelProvider primary = pickPrimary();
        if (primary == null) throw new OpcException("无可用 LLM provider");
        primary.chatStream(messages, options, callback);
        // 流式暂不计量（由 callback 在 onComplete 处自行打点）
    }

    private ChatModelProvider pickPrimary() {
        return providers.stream()
                .filter(ChatModelProvider::enabled)
                .min((a, b) -> Integer.compare(a.priority(), b.priority()))
                .orElseThrow(() -> new OpcException("无可用 LLM provider"));
    }

    /** 调用上下文（用于计量） */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChatContext {
        private Long companyId;
        private Long userId;
        private Long instanceId;
        private Long taskId;
        private String scene;
    }

}
