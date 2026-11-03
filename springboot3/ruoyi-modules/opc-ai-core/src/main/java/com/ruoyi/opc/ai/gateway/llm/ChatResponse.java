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
    /**
     * 厂商返回的 HTTP 状态码。
     *
     * <p>{@code null} = <b>没拿到 HTTP 响应</b>（超时 / 连接错 / 解析失败），
     * 上层按「可重试」处理；非 null 时上层可据此判断 4xx（不重试）/ 5xx（重试）。</p>
     */
    private Integer httpStatus;
    private String errorMessage;
    private String fallbackUsed;
    private String rawJson;

}
