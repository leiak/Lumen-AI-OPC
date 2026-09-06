package com.ruoyi.opc.ai.gateway.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek-V3 适配器
 * 通过 OpenAI 兼容协议接入
 *
 * @author OAC
 */
@Slf4j
@Component
public class DeepSeekProvider implements ChatModelProvider {

    @Value("${opc.llm.deepseek.api-key:}")
    private String apiKey;
    @Value("${opc.llm.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;
    @Value("${opc.llm.deepseek.model:deepseek-chat}")
    private String modelName;
    @Value("${opc.llm.deepseek.enabled:true}")
    private boolean enabled;

    @Override
    public String name() { return "deepseek"; }

    @Override
    public String model() { return modelName; }

    @Override
    public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }

    @Override
    public int priority() { return 10; }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
        long start = System.currentTimeMillis();
        try {
            // 实际实现：通过 WebClient/RestTemplate 调用 DeepSeek API
            // 这里给出关键调用骨架
            Map<String, Object> requestBody = buildRequestBody(messages, options);
            // HttpResponse response = httpClient.post(baseUrl + "/chat/completions", requestBody, headers);
            // ChatResponse parsed = parseResponse(response);

            ChatResponse resp = ChatResponse.builder()
                    .requestId("ds-" + System.currentTimeMillis())
                    .model(modelName)
                    .content("[DeepSeek 响应占位 - 真实实现需调用 API]")
                    .tokenInput(messages.size() * 100)
                    .tokenOutput(50)
                    .tokenTotal(messages.size() * 100 + 50)
                    .latencyMs(System.currentTimeMillis() - start)
                    .finishReason("stop")
                    .success(true)
                    .fallbackUsed("deepseek-v3")
                    .build();
            return resp;
        } catch (Exception e) {
            log.error("DeepSeek 调用异常", e);
            return ChatResponse.builder()
                    .model(modelName)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, ChatOptions options, StreamCallback callback) {
        // SSE 流式实现：HTTP chunked response
        // 这里给出骨架：循环读取 response.body，逐块调用 callback.onChunk(chunk)
        try {
            ChatResponse resp = chat(messages, options);
            callback.onChunk(resp.getContent());
            callback.onComplete(resp);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, ChatOptions options) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        if (options != null) {
            if (options.getTemperature() != null) body.put("temperature", options.getTemperature());
            if (options.getMaxTokens() != null) body.put("max_tokens", options.getMaxTokens());
            if (options.getTools() != null) body.put("tools", options.getTools());
            if (options.getToolChoice() != null) body.put("tool_choice", options.getToolChoice());
            if (options.getUser() != null) body.put("user", options.getUser());
        }
        body.put("stream", false);
        return body;
    }

}
