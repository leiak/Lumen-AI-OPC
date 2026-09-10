package com.ruoyi.opc.ai.gateway.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MiniMax(MiniMaxAI) 适配器（OAC-W48.6）
 *
 * <p>API 协议：OpenAI 兼容 {@code /v1/chat/completions}。
 * base-url 默认 {@code https://api.minimaxi.com/v1}，
 * 鉴权走 {@code Authorization: Bearer <MINIMAX_API_KEY>}。
 *
 * <p>priority=5，比 DeepSeek 优先（10），让用户在 Nacos 配 {@code opc.llm.primary=MiniMax-Text-01}
 * 时默认走 MiniMax。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniMaxProvider implements ChatModelProvider {

    private final HttpLlmClient http;

    @Value("${opc.llm.minimax.api-key:}")
    private String apiKey;
    @Value("${opc.llm.minimax.base-url:https://api.minimaxi.com/v1}")
    private String baseUrl;
    @Value("${opc.llm.minimax.model:MiniMax-Text-01}")
    private String modelName;
    @Value("${opc.llm.minimax.enabled:true}")
    private boolean enabled;

    @Override public String name() { return "minimax"; }
    @Override public String model() { return modelName; }
    @Override public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }
    @Override public int priority() { return 5; }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
        long start = System.currentTimeMillis();
        try {
            String raw = http.callChatCompletions(baseUrl, apiKey, modelName, messages, options);
            HttpLlmClient.ParsedChatResponse p = http.parseChatResponse(raw);
            return ChatResponse.builder()
                    .requestId(p.requestId() != null ? p.requestId() : ("minimax-" + System.currentTimeMillis()))
                    .model(p.model() != null ? p.model() : modelName)
                    .content(p.content())
                    .tokenInput(p.inputTokens())
                    .tokenOutput(p.outputTokens())
                    .tokenTotal(p.totalTokens())
                    .finishReason(p.finishReason())
                    .latencyMs(System.currentTimeMillis() - start)
                    .success(true)
                    .rawJson(raw)
                    .build();
        } catch (Exception e) {
            log.error("[MiniMax] 调用失败", e);
            return ChatResponse.builder()
                    .model(modelName)
                    .success(false)
                    .errorMessage("MiniMax 调用失败: " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, ChatOptions options, StreamCallback callback) {
        http.chatStream(baseUrl, apiKey, modelName, messages, options, callback);
    }
}
