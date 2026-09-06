package com.ruoyi.opc.ai.gateway.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI GPT-4o-mini 适配器（备用模型）
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpenAiMiniProvider implements ChatModelProvider {

    @Value("${opc.llm.openai.api-key:}")
    private String apiKey;
    @Value("${opc.llm.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    @Value("${opc.llm.openai.model:gpt-4o-mini}")
    private String modelName;
    @Value("${opc.llm.openai.enabled:true}")
    private boolean enabled;

    @Override
    public String name() { return "openai"; }

    @Override
    public String model() { return modelName; }

    @Override
    public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }

    @Override
    public int priority() { return 20; }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
        long start = System.currentTimeMillis();
        try {
            ChatResponse resp = ChatResponse.builder()
                    .requestId("oai-" + System.currentTimeMillis())
                    .model(modelName)
                    .content("[OpenAI 响应占位]")
                    .tokenInput(messages.size() * 80)
                    .tokenOutput(40)
                    .tokenTotal(messages.size() * 80 + 40)
                    .latencyMs(System.currentTimeMillis() - start)
                    .finishReason("stop")
                    .success(true)
                    .fallbackUsed("gpt-4o-mini")
                    .build();
            return resp;
        } catch (Exception e) {
            log.error("OpenAI 调用异常", e);
            return ChatResponse.builder()
                    .model(modelName)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, ChatOptions options, StreamCallback callback) {
        try {
            ChatResponse resp = chat(messages, options);
            callback.onChunk(resp.getContent());
            callback.onComplete(resp);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

}
