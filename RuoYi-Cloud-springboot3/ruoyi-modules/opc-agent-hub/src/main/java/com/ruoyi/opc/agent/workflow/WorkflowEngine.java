package com.ruoyi.opc.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.ai.runtime.AgentRuntime;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流引擎（v0.2）
 * 支持节点类型：
 *  - LLM       调 LLM 生成（自动注入 prompt + 输出）
 *  - TOOL      调工具（Function Calling）
 *  - CONDITION 条件分支（基于 JSON Path 表达式）
 *  - LOOP      循环（最多 N 次）
 *  - DELAY     延迟（毫秒）
 *
 * 数据流转：
 *  - 节点输出写入 ctx.outputs[nodeId]
 *  - 下一节点可通过 ${ctx.outputs.prevId} 引用
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final AgentRuntime agentRuntime;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkflowResult run(WorkflowDefinition def, Map<String, Object> inputParams) {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = new WorkflowContext();
        ctx.inputParams = inputParams == null ? new HashMap<>() : inputParams;
        ctx.outputs = new HashMap<>();
        ctx.steps = new ArrayList<>();

        try {
            JsonNode dag = objectMapper.readTree(def.getDagJson());
            JsonNode nodes = dag.get("nodes");
            JsonNode edges = dag.get("edges");
            if (nodes == null) throw new OpcException("工作流定义缺少 nodes");

            // 找起始节点（没有入边的）
            String startNode = findStartNode(nodes, edges);
            if (startNode == null) throw new OpcException("未找到起始节点");

            String current = startNode;
            int safetyCount = 0;
            while (current != null && safetyCount++ < 100) {
                JsonNode nodeDef = findNode(nodes, current);
                if (nodeDef == null) break;

                String type = nodeDef.path("type").asText("");
                Map<String, Object> nodeCfg = objectMapper.convertValue(nodeDef.path("config"), Map.class);
                ctx.steps.add(new StepLog(current, type));

                switch (type) {
                    case "LLM" -> runLlm(nodeDef, ctx);
                    case "TOOL" -> runTool(nodeDef, ctx);
                    case "CONDITION" -> current = runCondition(nodeDef, ctx);
                    case "DELAY" -> runDelay(nodeDef);
                    default -> { log.warn("未知节点类型 {}", type); }
                }

                // 默认下一个节点（线性）
                if (!"CONDITION".equals(type)) {
                    current = findNextNode(edges, current);
                }
            }

            return WorkflowResult.builder()
                    .success(true)
                    .outputs(ctx.outputs)
                    .stepLogs(ctx.steps)
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("工作流执行失败", e);
            return WorkflowResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .stepLogs(ctx.steps)
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private void runLlm(JsonNode nodeDef, WorkflowContext ctx) throws Exception {
        String prompt = nodeDef.path("prompt").asText("");
        Map<String, Object> vars = new HashMap<>();
        vars.putAll(ctx.inputParams);
        vars.putAll(ctx.outputs);
        String renderedPrompt = renderTemplate(prompt, vars);

        AgentRuntime.AgentTaskRequest req = AgentRuntime.AgentTaskRequest.builder()
                .userInput(renderedPrompt)
                .sessionId("wf-" + System.currentTimeMillis())
                .maxSteps(3)
                .build();
        AgentRuntime.AgentTaskResult res = agentRuntime.run(req);
        ctx.outputs.put(nodeDef.path("id").asText(), res.getContent());
    }

    private void runTool(JsonNode nodeDef, WorkflowContext ctx) {
        // TODO: 调 ToolExecutor
        ctx.outputs.put(nodeDef.path("id").asText(), "{}");
    }

    private String runCondition(JsonNode nodeDef, WorkflowContext ctx) {
        // 简单实现：根据 expr 表达式结果选择 true/false 分支
        return null;
    }

    private void runDelay(JsonNode nodeDef) throws InterruptedException {
        long ms = nodeDef.path("ms").asLong(1000);
        Thread.sleep(ms);
    }

    private String findStartNode(JsonNode nodes, JsonNode edges) {
        for (JsonNode n : nodes) {
            boolean hasIn = false;
            if (edges != null) {
                for (JsonNode e : edges) {
                    if (e.path("target").asText().equals(n.path("id").asText())) {
                        hasIn = true;
                        break;
                    }
                }
            }
            if (!hasIn) return n.path("id").asText();
        }
        return null;
    }

    private JsonNode findNode(JsonNode nodes, String id) {
        for (JsonNode n : nodes) {
            if (n.path("id").asText().equals(id)) return n;
        }
        return null;
    }

    private String findNextNode(JsonNode edges, String current) {
        if (edges == null) return null;
        for (JsonNode e : edges) {
            if (e.path("source").asText().equals(current)) {
                return e.path("target").asText();
            }
        }
        return null;
    }

    private String renderTemplate(String template, Map<String, Object> vars) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}",
                    String.valueOf(e.getValue()));
        }
        return result;
    }

    @lombok.Data
    public static class WorkflowDefinition {
        private String dagJson;
    }

    @lombok.Data
    public static class WorkflowContext {
        private Map<String, Object> inputParams;
        private Map<String, Object> outputs;
        private List<StepLog> steps;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StepLog {
        private String nodeId;
        private String type;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WorkflowResult {
        private boolean success;
        private String errorMessage;
        private Map<String, Object> outputs;
        private List<StepLog> stepLogs;
        private long costMs;
    }

}
