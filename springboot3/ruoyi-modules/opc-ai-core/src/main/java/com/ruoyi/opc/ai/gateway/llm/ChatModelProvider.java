package com.ruoyi.opc.ai.gateway.llm;

import java.util.List;

/**
 * LLM 模型提供者抽象接口
 * 由不同厂商适配器实现：DeepSeekProvider / OpenAiProvider / WenxinProvider / QwenProvider
 *
 * @author OAC
 */
public interface ChatModelProvider {

    /** 提供者名称 */
    String name();

    /** 模型标识 */
    String model();

    /** 是否启用 */
    boolean enabled();

    /** 优先级（越小越优先） */
    default int priority() { return 100; }

    /** 同步聊天 */
    ChatResponse chat(List<ChatMessage> messages, ChatOptions options);

    /** 流式聊天（callback） */
    void chatStream(List<ChatMessage> messages, ChatOptions options, StreamCallback callback);

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class ChatOptions {
        private Double temperature;
        private Integer maxTokens;
        private List<java.util.Map<String, Object>> tools;
        private String toolChoice;
        private String user;
        private Integer timeoutSeconds;
        /**
         * 思考强度（{@code reasoning_effort}）—— <b>给「强制思考」模型用</b>。
         *
         * <p>MiniMax-M3.1-Flash 这类模型<b>不能关闭思考</b>，而且
         * {@code reasoning_effort} 不传时默认按 {@code max} 思考 ——
         * 思考 token 又计入 {@code max_tokens}，于是正文被挤掉，
         * 响应里<b>连 content 字段都没有</b>（2026-11-05 线上就是这个坑）。
         *
         * <p>取值：{@code low / medium / high / xhigh / max}。
         * 非思考模型（DeepSeek）不认这个字段，别给它传。</p>
         */
        private String reasoningEffort;
    }

    interface StreamCallback {
        void onChunk(String chunk);
        void onComplete(ChatResponse response);
        void onError(Throwable t);
    }

}
