package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.opc.common.constant.OpcConstants;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 网关（多模型路由 + 重试 + 失败 fallback + 缓存 + 计量埋点）
 *
 * <p><b>重试语义</b>：{@code opc.llm.max-retries} 的含义是「<b>每个 provider
 * 最多尝试这么多次</b>」（不是「总共几次」）。一个 provider 的重试次数用尽后，
 * 才降级到下一个 provider；全部 provider 都失败 → 抛 {@link OpcException}。</p>
 *
 * <p><b>两条失败路径必须分清</b>（混成一条就说不清该不该重试）：</p>
 * <ul>
 *   <li><b>抛异常</b>（{@code catch (Exception e)}）→ <b>无条件重试</b>。
 *       抛异常意味着「没拿到 HTTP 响应」，无从判断是不是配额问题。</li>
 *   <li><b>返回失败响应</b>（{@code success=false}）→ 由 {@link #isRetryable(ChatResponse)}
 *       判断：429 与其它 4xx <b>不重试</b>（配额/鉴权问题重试只会再撞一次，白等），
 *       5xx 与「没有状态码的未知错误」才重试。</li>
 * </ul>
 *
 * <p><b>退避</b>：当前<b>没有退避</b> —— 两次重试之间<b>不等待</b>。
 * 因此最坏耗时 = {@code max-retries × timeout-seconds × provider 数}
 * （默认 3 × 60 × 2 = <b>6 分钟</b>），这是上线后的<b>实际表现</b>，
 * 不写下来就等于对外说它不是这样。</p>
 *
 * @author OAC
 */
@Slf4j
@Component
public class LlmGateway {

    /** {@code opc.llm.max-retries} 的默认值 —— 每个 provider 最多尝试 3 次。 */
    public static final int DEFAULT_MAX_RETRIES = 3;

    private final List<ChatModelProvider> providers;
    private final TokenMeter tokenMeter;
    private final LlmCache cache;
    private final int maxRetries;

    /**
     * 3 参构造器 —— 默认 {@link #DEFAULT_MAX_RETRIES} 次重试。
     * 保留它是为了让既有测试（{@code new LlmGateway(providers, tokenMeter, cache)}）不用改。
     */
    public LlmGateway(List<ChatModelProvider> providers, TokenMeter tokenMeter, LlmCache cache) {
        this(providers, tokenMeter, cache, DEFAULT_MAX_RETRIES);
    }

    @Autowired
    public LlmGateway(List<ChatModelProvider> providers, TokenMeter tokenMeter, LlmCache cache,
                      @Value("${opc.llm.max-retries:3}") int maxRetries) {
        this.providers = providers;
        this.tokenMeter = tokenMeter;
        this.cache = cache;
        // 配 0 或负数 → 至少试一次（不能一个都不试）。
        this.maxRetries = Math.max(1, maxRetries);
    }

    /**
     * 统一聊天入口（带重试 + fallback）
     */
    public ChatResponse chat(List<ChatMessage> messages, ChatModelProvider.ChatOptions options, ChatContext ctx) {
        // 1. 缓存命中
        //
        // 缓存 key 按租户隔离（见 LlmCache.buildKey）：同一家公司的同一段 prompt
        // 才复用 —— 否则 A 公司付的钱会让 B 公司白嫖，账单就没法对账。
        Long companyId = ctx != null ? ctx.getCompanyId() : null;
        String cacheKey = cache.buildKey(messages, options, companyId);
        ChatResponse cached = cache.get(cacheKey);
        if (cached != null && isBlankWithoutToolCalls(cached)) {
            // 缓存里躺着一条「成功但正文是空的」——
            // 那是 2026-11-05 之前的 bug 写进去的（TTL 一小时）。
            // 命中它等于把空正文再喂一遍；当没命中，重新调一次。
            log.warn("[LLM] 缓存里是一条空正文, 当作没命中 key={}", cacheKey);
            cached = null;
        }
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
        //
        // 这里以前直接遍历注入顺序的 providers —— Spring 按类名/注册顺序注入，
        // 和 priority 无关。后果是「主模型」根本不一定是优先级最高的那个：
        // DeepSeekProvider(priority=10) 可能排在 MiniMaxProvider(priority=5) 前面，
        // 于是配置里写着「MiniMax 优先」而实际先打的是 DeepSeek。
        // chatStream() 走 pickPrimary()（排序过），两条路的行为因此一直不一致。
        // 现在统一按 priority 升序遍历 —— 和 chatStream 对齐。
        ChatResponse lastError = null;
        String lastProviderName = null;
        List<ChatModelProvider> ordered = providers.stream()
                .sorted(java.util.Comparator.comparingInt(ChatModelProvider::priority))
                .toList();
        for (ChatModelProvider provider : ordered) {
            if (!provider.enabled()) continue;
            // 重试循环**只包住 provider.chat(...) 这一段** ——
            // 缓存的查 / 写 / 计量三处在循环外面，位置与以前一致。
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    long start = System.currentTimeMillis();
                    ChatResponse resp = provider.chat(messages, options);
                    long cost = System.currentTimeMillis() - start;
                    resp.setLatencyMs(cost);
                    if (resp.getSuccess() == null || !resp.getSuccess()) {
                        // 失败响应：有状态码 → 由 isRetryable 判断该不该重试。
                        lastError = resp;
                        lastProviderName = provider.name();
                        if (!isRetryable(resp)) {
                            log.warn("[LLM] provider={} 第 {} 次失败（不重试）：{}",
                                    provider.name(), attempt, resp.getErrorMessage());
                            break;
                        }
                        log.warn("[LLM] provider={} 第 {} 次失败：{}",
                                provider.name(), attempt, resp.getErrorMessage());
                        continue;
                    }
                    // 「报成功但正文是空的」= <b>失败</b>。
                    //
                    // 这是 2026-11-05 线上真踩的：M3.1-Flash 的思考吃满了 max_tokens，
                    // 响应里没有 content 字段 → 解析成 null → provider 报「成功」。
                    // 以前这里直接当成功返回，于是 ①降级链根本不触发、②null 落进缓存
                    // （一小时内每次同样提问都拿到那份空）、③null 一路漏到工作流层
                    // 才炸成一句「产出不是字符串: n2」。
                    //
                    // 判成失败后<b>不重试</b>：同样的参数再调一次还是被截断，
                    // 而且每次截断的思考 token 都要真金白银。直接换下一个 provider。
                    if (isBlankWithoutToolCalls(resp)) {
                        resp.setSuccess(false);
                        resp.setErrorMessage("provider 返回了空正文(finish_reason="
                                + (resp.getFinishReason() == null ? "null" : resp.getFinishReason())
                                + ")");
                        lastError = resp;
                        lastProviderName = provider.name();
                        log.warn("[LLM] provider={} 返回空正文 → 换下一个 provider: {}",
                                provider.name(), resp.getErrorMessage());
                        break;
                    }
                    log.info("[LLM] provider={} 调用成功 cost={}ms tokens={}",
                            provider.name(), cost, resp.getTokenTotal());

                    // 3. Token 计量埋点
                    if (ctx != null) {
                        tokenMeter.record(ctx, provider.model(), resp);
                    }

                    // 4. 写缓存（只在成功时写一次 —— 不许每次重试都写缓存）
                    cache.put(cacheKey, resp, OpcConstants.PRICE_DEEPSEEK_OUTPUT);
                    return resp;
                } catch (Exception e) {
                    // 抛异常 = 没拿到 HTTP 响应，无从判断是不是配额问题 → **无条件重试**。
                    // （isRetryable 只作用于「返回了失败响应」的分支，两条路径别混成一条。）
                    log.warn("[LLM] provider={} 第 {} 次异常：{}", provider.name(), attempt, e.getMessage());
                    lastError = ChatResponse.builder()
                            .model(provider.model())
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                    lastProviderName = provider.name();
                }
            }
        }

        throw new OpcException("所有 LLM provider 失败，最后错误（provider=" + lastProviderName + "）：" +
                (lastError != null ? lastError.getErrorMessage() : "unknown"));
    }

    /**
     * 「正文是空的」判定。
     *
     * <p><b>带 tool_calls 的响应不算空</b>：模型决定调工具时，正文本来就可能是空的
     * （OpenAI 兼容协议里 {@code content} 与 {@code tool_calls} 二选一）。</p>
     */
    private boolean isBlankWithoutToolCalls(ChatResponse resp) {
        if (resp == null) {
            return false;
        }
        if (resp.getToolCalls() != null && !resp.getToolCalls().isEmpty()) {
            return false;
        }
        return resp.getContent() == null || resp.getContent().isBlank();
    }

    /**
     * 429 与 4xx 不重试 —— 重试同样会撞，只是白等；5xx 与网络错才值得再试。
     *
     * <p>{@code httpStatus == null}（超时 / 连接错 / 解析错）算<b>可重试</b>：
     * 未知错误 → 再试一次是合理的默认值。</p>
     */
    private boolean isRetryable(ChatResponse resp) {
        Integer status = resp.getHttpStatus();
        if (status == null) return true;      // 超时 / 连接错
        if (status == 429) return false;      // 配额问题 —— 再撞一次也是 429
        return status >= 500;                 // 5xx 才是服务端临时故障，值得再试
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
