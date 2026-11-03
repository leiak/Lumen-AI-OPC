package com.ruoyi.opc.ai.gateway.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DeepSeek-V3 适配器（OAC-W48.6：真实 HTTP 调用）
 *
 * <p>DeepSeek API 兼容 OpenAI {@code /v1/chat/completions} 协议，
 * base-url 默认 {@code https://api.deepseek.com/v1}。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekProvider implements ChatModelProvider {

    private final HttpLlmClient http;

    @Value("${opc.llm.deepseek.api-key:}")
    private String apiKey;
    @Value("${opc.llm.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;
    @Value("${opc.llm.deepseek.model:deepseek-chat}")
    private String modelName;
    @Value("${opc.llm.deepseek.enabled:true}")
    private boolean enabled;

    @Override public String name() { return "deepseek"; }
    @Override public String model() { return modelName; }
    @Override public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }
    @Override public int priority() { return 10; }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
        long start = System.currentTimeMillis();
        try {
            String raw = http.callChatCompletions(baseUrl, apiKey, modelName, messages, options);
            HttpLlmClient.ParsedChatResponse p = http.parseChatResponse(raw);
            return ChatResponse.builder()
                    .requestId(p.requestId() != null ? p.requestId() : ("ds-" + System.currentTimeMillis()))
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
        } catch (LlmHttpException e) {
            // 有状态码 → 上层能判断该不该重试（429/4xx 不重试，5xx 重试）。
            // catch 顺序：LlmHttpException 必须在 Exception 前面（它是子类）。
            log.error("[DeepSeek] 调用失败 httpStatus={}", e.getStatusCode(), e);
            return ChatResponse.builder()
                    .model(modelName)
                    .success(false)
                    .httpStatus(e.getStatusCode())
                    .errorMessage("DeepSeek 调用失败(HTTP " + e.getStatusCode() + "): " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            // 网络错 / 超时 / 解析错 → httpStatus 留 null → 上层按「可重试」处理。
            // 不要设 0 或 -1：isRetryable 判的是 status == null。
            log.error("[DeepSeek] 调用失败", e);
            return ChatResponse.builder()
                    .model(modelName)
                    .success(false)
                    .errorMessage("DeepSeek 调用失败: " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, ChatOptions options, StreamCallback callback) {
        http.chatStream(baseUrl, apiKey, modelName, messages, options, callback);
    }
}
