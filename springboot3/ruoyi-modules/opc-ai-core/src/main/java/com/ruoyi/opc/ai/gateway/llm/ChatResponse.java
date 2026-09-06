package com.ruoyi.opc.ai.gateway.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM 聊天响应
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String requestId;
    private String model;
    private String content;
    private List<ChatMessage.ToolCall> toolCalls;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Integer tokenTotal;
    private Long latencyMs;
    private String finishReason;
    private Boolean success;
    private String errorMessage;
    private String fallbackUsed;
    private String rawJson;

}
