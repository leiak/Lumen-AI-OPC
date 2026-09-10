package com.ruoyi.opc.ai.gateway.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    // W48.5: 前端 chat.vue 用 'user' / 'assistant' / 'system' 小写,这里 @JsonCreator 大小写兼容
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL;

        @JsonCreator
        public static Role fromString(String s) {
            if (s == null) return null;
            return Role.valueOf(s.toUpperCase());
        }
    }

    private Role role;
    private String content;
    private String name;
    private String toolCallId;
    private List<ToolCall> toolCalls;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role(Role.SYSTEM).content(content).build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder().role(Role.USER).content(content).build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role(Role.ASSISTANT).content(content).build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private Function function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Function {
            private String name;
            private String arguments;
        }
    }

    public static class ChatRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        private Integer maxTokens;
        private List<Map<String, Object>> tools;
        private String toolChoice;
        private Boolean stream;
        private String user;
    }

}
