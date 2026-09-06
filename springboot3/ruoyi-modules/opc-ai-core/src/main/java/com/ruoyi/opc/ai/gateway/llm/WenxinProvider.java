package com.ruoyi.opc.ai.gateway.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文心一言 4.0 适配器
 * 通过百度智能云千帆大模型平台 API 接入
 *
 * @author OAC
 */
@Slf4j
@Component
public class WenxinProvider implements ChatModelProvider {

    @Value("${opc.llm.wenxin.api-key:}")
    private String apiKey;
    @Value("${opc.llm.wenxin.base-url:https://qianfan.baidubce.com/v2}")
    private String baseUrl;
    @Value("${opc.llm.wenxin.model:ernie-4.0-8k}")
    private String modelName;
    @Value("${opc.llm.wenxin.enabled:false}")
    private boolean enabled;

    @Override
    public String name() { return "wenxin"; }

    @Override
    public String model() { return modelName; }

    @Override
    public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }

    @Override
    public int priority() { return 30; }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
        return ChatResponse.builder()
                .model(modelName)
                .content("[文心一言响应占位]")
                .tokenInput(messages.size() * 90)
                .tokenOutput(45)
                .success(true)
                .build();
    }

    @Override
    public void chatStream(List<ChatMessage> messages, ChatOptions options, StreamCallback callback) {
        callback.onComplete(chat(messages, options));
    }
}
