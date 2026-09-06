package com.ruoyi.opc.ai.runtime;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.ai.memory.Memory;
import com.ruoyi.opc.ai.security.PromptGuard;
import com.ruoyi.opc.ai.tools.ToolExecutor;
import com.ruoyi.opc.ai.tools.ToolSpec;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Runtime
 * 实现 ReAct（Reasoning + Acting）循环：
 *   1. 构造 Prompt（system + history + user）
 *   2. 调 LLM，让其决定"调用工具"或"输出最终答案"
 *   3. 若调工具：执行工具，把结果回填，再调 LLM
 *   4. 直至输出 final answer 或达到 maxSteps
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final LlmGateway llmGateway;
    private final Memory memory;
    private final ToolExecutor toolExecutor;
    private final PromptGuard promptGuard;

    /** 默认配置 */
    private static final int DEFAULT_MAX_STEPS = 8;
    private static final int DEFAULT_STEP_TIMEOUT_SECONDS = 30;

    /**
     * 同步执行一个 Agent 任务
     */
    public AgentTaskResult run(AgentTaskRequest request) {
        long startTime = System.currentTimeMillis();
        List<ChatMessage> messages = buildInitialMessages(request);
        List<StepLog> stepLogs = new ArrayList<>();

        int maxSteps = request.getMaxSteps() != null ? request.getMaxSteps() : DEFAULT_MAX_STEPS;
        int stepTimeout = request.getStepTimeoutSeconds() != null ? request.getStepTimeoutSeconds() : DEFAULT_STEP_TIMEOUT_SECONDS;

        LlmGateway.ChatContext ctx = LlmGateway.ChatContext.builder()
                .companyId(request.getCompanyId())
                .userId(request.getUserId())
                .instanceId(request.getInstanceId())
                .scene(request.getScene())
                .build();

        for (int step = 1; step <= maxSteps; step++) {
            log.info("[AgentRuntime] step={} instance={}", step, request.getInstanceId());

            ChatModelProvider.ChatOptions options = ChatModelProvider.ChatOptions.builder()
                    .temperature(request.getTemperature() != null ? request.getTemperature() : 0.3)
                    .maxTokens(2048)
                    .tools(request.getTools())
                    .toolChoice("auto")
                    .timeoutSeconds(stepTimeout)
                    .build();

            ChatResponse resp;
            try {
                resp = llmGateway.chat(messages, options, ctx);
            } catch (Exception e) {
                log.error("LLM 调用失败 step={}", step, e);
                stepLogs.add(StepLog.builder().step(step).type("ERROR")
                        .content(e.getMessage()).build());
                throw new OpcException("LLM 调用失败：" + e.getMessage());
            }

            if (!resp.getSuccess()) {
                throw new OpcException("LLM 调用失败：" + resp.getErrorMessage());
            }

            // 写入记忆
            memory.appendShortTerm(request.getSessionId(), ChatMessage.assistant(resp.getContent()));

            // 步骤 1：检查是否有 tool_calls
            if (resp.getToolCalls() != null && !resp.getToolCalls().isEmpty()) {
                messages.add(ChatMessage.builder()
                        .role(ChatMessage.Role.ASSISTANT)
                        .content(resp.getContent())
                        .toolCalls(resp.getToolCalls())
                        .build());

                for (ChatMessage.ToolCall tc : resp.getToolCalls()) {
                    log.info("[AgentRuntime] 工具调用 step={} tool={}", step, tc.getFunction().getName());
                    stepLogs.add(StepLog.builder().step(step).type("TOOL_CALL")
                            .toolName(tc.getFunction().getName())
                            .toolArgs(tc.getFunction().getArguments())
                            .build());

                    String toolResult;
                    try {
                        // v0.3：即使工具已注册，也必须在 PromptGuard 白名单内（防御纵深）
                        promptGuard.validateToolName(tc.getFunction().getName());
                        toolResult = toolExecutor.execute(tc.getFunction().getName(),
                                tc.getFunction().getArguments(), request);
                    } catch (Exception e) {
                        toolResult = "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
                        log.warn("[AgentRuntime] 工具执行失败 step={} tool={}", step, tc.getFunction().getName(), e);
                    }
                    stepLogs.get(stepLogs.size() - 1).setToolResult(toolResult);

                    messages.add(ChatMessage.builder()
                            .role(ChatMessage.Role.TOOL)
                            .name(tc.getFunction().getName())
                            .toolCallId(tc.getId())
                            .content(toolResult)
                            .build());
                }
                continue;
            }

            // 步骤 2：final answer
            stepLogs.add(StepLog.builder().step(step).type("FINAL")
                    .content(resp.getContent()).build());
            long cost = System.currentTimeMillis() - startTime;
            log.info("[AgentRuntime] 完成 steps={} cost={}ms", step, cost);

            return AgentTaskResult.builder()
                    .success(true)
                    .content(resp.getContent())
                    .totalSteps(step)
                    .tokenInput(resp.getTokenInput())
                    .tokenOutput(resp.getTokenOutput())
                    .tokenTotal(resp.getTokenTotal())
                    .costMs(cost)
                    .stepLogs(stepLogs)
                    .finishReason(resp.getFinishReason())
                    .build();
        }

        // 超过最大步数
        return AgentTaskResult.builder()
                .success(false)
                .content("超过最大步数 " + maxSteps)
                .totalSteps(maxSteps)
                .costMs(System.currentTimeMillis() - startTime)
                .stepLogs(stepLogs)
                .finishReason("MAX_STEPS")
                .build();
    }

    private List<ChatMessage> buildInitialMessages(AgentTaskRequest request) {
        List<ChatMessage> list = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            list.add(ChatMessage.system(request.getSystemPrompt()));
        }

        // 加载历史记忆（短期）
        List<ChatMessage> history = memory.loadShortTerm(request.getSessionId(), 10);
        list.addAll(history);

        // v0.3：用户输入进入 LLM 之前先过 PromptGuard（注入 + 红队 5 类攻击模式）
        list.add(ChatMessage.user(promptGuard.sanitize(request.getUserInput())));
        return list;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AgentTaskRequest {
        private Long companyId;
        private Long userId;
        private Long instanceId;
        private Long taskId;
        private String sessionId;
        private String scene;
        private String systemPrompt;
        private String userInput;
        private List<Map<String, Object>> tools;
        private Integer maxSteps;
        private Integer stepTimeoutSeconds;
        private Double temperature;
        private Map<String, Object> context;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AgentTaskResult {
        private boolean success;
        private String content;
        private int totalSteps;
        private Integer tokenInput;
        private Integer tokenOutput;
        private Integer tokenTotal;
        private long costMs;
        private String finishReason;
        private List<StepLog> stepLogs;
        private Map<String, Object> extra;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StepLog {
        private int step;
        private String type;
        private String content;
        private String toolName;
        private String toolArgs;
        private String toolResult;
    }

}
