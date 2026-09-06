package com.ruoyi.opc.ai.controller;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OPC LLM 网关对外开放 API
 *
 * @author OAC
 */
@Tag(name = "OPC LLM 网关")
@RestController
@RequestMapping("/opc/llm")
@RequiredArgsConstructor
public class OpcLlmController {

    private final LlmGateway llmGateway;

    @Operation(summary = "统一聊天（带 fallback + 缓存 + 计量）")
    @PostMapping("/chat")
    public R<ChatResponse> chat(@RequestBody ChatRequest req) {
        ChatResponse resp = llmGateway.chat(req.messages, ChatModelProvider.ChatOptions.builder()
                .temperature(req.temperature)
                .maxTokens(req.maxTokens)
                .tools(req.tools)
                .toolChoice(req.toolChoice)
                .user(req.user)
                .build(), LlmGateway.ChatContext.builder()
                .companyId(req.companyId)
                .userId(req.userId)
                .instanceId(req.instanceId)
                .taskId(req.taskId)
                .scene(req.scene)
                .build());
        return R.ok(resp);
    }

    @Operation(summary = "列出可用模型")
    @GetMapping("/models")
    public R<List<Map<String, Object>>> models() {
        return R.ok(List.of(
                Map.of("id", "deepseek-v3", "name", "DeepSeek-V3", "priority", 10, "enabled", true),
                Map.of("id", "gpt-4o-mini", "name", "GPT-4o-mini", "priority", 20, "enabled", true),
                Map.of("id", "wenxin-4.0", "name", "文心一言 4.0", "priority", 30, "enabled", false)
        ));
    }

    @lombok.Data
    public static class ChatRequest {
        public List<ChatMessage> messages;
        public Double temperature;
        public Integer maxTokens;
        public List<Map<String, Object>> tools;
        public String toolChoice;
        public String user;
        public Long companyId;
        public Long userId;
        public Long instanceId;
        public Long taskId;
        public String scene;
    }

    /** 简单 R 包装 */
    public record R<T>(int code, String msg, T data) {
        public static <T> R<T> ok(T data) { return new R<>(200, "OK", data); }
    }

}
