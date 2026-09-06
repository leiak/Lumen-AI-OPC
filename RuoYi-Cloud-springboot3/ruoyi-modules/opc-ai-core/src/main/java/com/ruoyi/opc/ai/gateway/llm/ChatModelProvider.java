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
    }

    interface StreamCallback {
        void onChunk(String chunk);
        void onComplete(ChatResponse response);
        void onError(Throwable t);
    }

}
