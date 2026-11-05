package com.ruoyi.opc.agent.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.opc.ai.runtime.AgentRuntime;
import com.ruoyi.opc.ai.tools.ToolExecutor;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final ToolExecutor toolExecutor;

    /**
     * persist 处理器 —— 节点跑完后把产出物交给它落进业务表。
     *
     * <p>以前产出物只躺在 {@code opc_agent_workflow_run.output_result} 里（运行日志），
     * 业务上查日报的表跟工作流没有关系。现在 dag 里可以声明 {@code persist}，
     * 引擎在节点循环之后<b>确定性地</b>执行落表 —— 落不成功，run 就不能记成功。</p>
     */
    private final WorkflowPersistHandler persistHandler;

    /**
     * 引擎自己的 ObjectMapper —— <b>不</b>用 Spring 注入的那个。
     *
     * <p><b>为什么数字口径要钉住</b>：工具返回的 JSON 里金额是 {@code "totalIn":51892.00}
     * （BigDecimal，scale 2）。解析时若解成 {@code Double}，再序列化出去就成了 {@code 51892.0} ——
     * 落进 {@code kpi_json} 的数字跟工具返回的对不上，逐笔对账必失真。
     * 所以这里开 {@code USE_BIG_DECIMAL_FOR_FLOATS}，并解析成 {@code Map}（<b>不</b>走
     * {@code JsonNode}：Jackson 2.21 的 {@code STRIP_TRAILING_BIGDECIMAL_ZEROES} 默认开，
     * {@code DecimalNode} 会把末尾的零裁掉）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);

    public WorkflowResult run(WorkflowDefinition def, Map<String, Object> inputParams) {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = new WorkflowContext();
        ctx.inputParams = inputParams == null ? new HashMap<>() : inputParams;
        ctx.outputs = new HashMap<>();
        ctx.steps = new ArrayList<>();
        // 归属（租户 + 触发者）随定义带进来 ——
        // 工作流是定时触发的（@InnerAuth，只有 Quartz 能调），**没有登录态**，
        // 以前 LLM 节点构造 AgentTaskRequest 时不带任何归属，
        // 于是 TokenMeter 拿不到 company_id/user_id，这条用量记录直接被丢掉：
        // 工作流跑掉的 token/成本在账单里完全看不见。
        ctx.companyId = def.getCompanyId();
        ctx.userId = def.getUserId();
        // 运行记录 id —— 产出物落表时要带上它（溯源：从日报能反查回那次运行）。
        // 三处必须都接上（裁定 R-100）：WorkflowDefinition.runId → ctx.runId → body.sourceWorkflowRunId。
        ctx.runId = def.getRunId();

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

            // persist —— 节点跑完后把产出物落进业务表（确定性动作，不交给模型决定）。
            // 抛异常 → 冒泡到下面的 catch → success=false → run 记 FAILED：
            // 产出物没落表 = 这件事没做成，不能记成功（裁定 R-106）。
            //
            // 不接返回值（PersistOutcome）：落表成功与否在 DB 里可反查 ——
            // opc_insight_daily_report.source_workflow_run_id 指向**最近一次**写这一行的 run。
            // 注意日报是快照：同一 (company_id, period) upsert 时先删旧行再插新行，
            // 所以**更早**那次成功落表在表里不会留下痕迹（反查只对最近一次有效）。
            // persist 抛异常 → 整条 run 记 FAILED（没落表）。
            JsonNode persistNode = dag.get("persist");
            if (persistNode != null && !persistNode.isNull()) {
                persistHandler.persist(persistNode, ctx, buildVars(ctx));
            }

            return WorkflowResult.builder()
                    .success(true)
                    .partial(ctx.partial)
                    .llmError(ctx.llmError)
                    .outputs(ctx.outputs)
                    .stepLogs(ctx.steps)
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("工作流执行失败", e);
            return WorkflowResult.builder()
                    .success(false)
                    .partial(ctx.partial)
                    .llmError(ctx.llmError)
                    .errorMessage(e.getMessage())
                    .outputs(ctx.outputs)
                    .stepLogs(ctx.steps)
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    /**
     * LLM 节点 —— <b>失败是软失败</b>。
     *
     * <p>LLM 会挂：超时、provider 故障、PromptGuard 403（{@code PromptGuard.sanitize}
     * 命中注入模式就抛异常 —— 这是<b>正确</b>行为，别绕）。
     * 以前这类失败直接把整条 run 打死，于是<b>前面 TOOL 节点已经取到的真实数据被一起丢掉</b>。
     * 现在：标记 {@code partial}、记下错误、落一段<b>确定性</b>聚合摘要，继续往下走。
     *
     * <p>确定性摘要只由 {@link DeterministicAggregator} 从工具返回值里拼，
     * 数字一个都不多 —— 界面能认出「这段没走 LLM」。
     */
    private void runLlm(JsonNode nodeDef, WorkflowContext ctx) {
        String nodeId = nodeDef.path("id").asText();
        try {
            String prompt = nodeDef.path("prompt").asText("");
            Map<String, Object> vars = buildVars(ctx);
            String renderedPrompt = renderTemplate(prompt, vars);

            // 节点级超时；没写就 null（走 AgentRuntime 的默认 30 秒）
            Integer timeout = nodeDef.has("stepTimeoutSeconds")
                    ? Integer.valueOf(nodeDef.path("stepTimeoutSeconds").asInt())
                    : null;

            AgentRuntime.AgentTaskRequest req = AgentRuntime.AgentTaskRequest.builder()
                    .userInput(renderedPrompt)
                    .sessionId("wf-" + nodeId + "-" + System.currentTimeMillis())
                    .maxSteps(3)
                    .stepTimeoutSeconds(timeout)
                    // 归属必须一路带到 LLM 调用, 否则这次调用的 token/成本记不进账单
                    .companyId(ctx.companyId)
                    .userId(ctx.userId)
                    .scene("workflow_llm")
                    // 工作流的 prompt 里内联了整份财务 JSON（真流水、金额、对手方名）。
                    // 走记忆的话这段数据会被写进长期记忆库 —— 以后同一租户的普通聊天
                    // 可能检索到它，把「昨天日报的数字」当成历史事实说出来。
                    .skipMemory(Boolean.TRUE)
                    .build();
            AgentRuntime.AgentTaskResult res = agentRuntime.run(req);
            if (res == null) {
                throw new OpcException("LLM 节点 " + nodeId + " 没有返回结果");
            }
            if (!res.isSuccess()) {
                throw new OpcException("LLM 节点 " + nodeId + " 执行失败：" + res.getContent());
            }
            ctx.llmModel = res.getModel();
            // 「报成功但正文是空的」= 没有正文。
            //
            // 2026-11-05 线上踩过：provider 报成功、content 是 null，
            // 这里就把 null 塞进产出物 → 整条 run 记成功 →
            // 真正炸掉的地方在 persist 层，错误消息还写着「产出不是字符串: n2」，
            // 把锅甩给了一个完全无辜的节点。
            //
            // 现在当成软失败（下面的 catch 兜住）→ 降级成 PARTIAL，
            // 摘要用工具返回里的真实数字，产出物照样落表。
            String content = res.getContent();
            if (content == null || content.isBlank()) {
                throw new OpcException("LLM 节点 " + nodeId + " 没返回正文"
                        + "（provider 报成功但内容是空的 —— 常见于思考 token 吃满了 max_tokens）");
            }
            ctx.outputs.put(nodeId, content);
        } catch (Exception e) {
            // 软失败：捕 Exception（**不只是 OpcException**）—— 超时、NPE、序列化…都会走到这。
            // 收窄成 catch (OpcException) 会让「provider 超时」把整条 run 打死，
            // 已经取到的真实数据白取 —— R-21 明文要求，测试也钉住了这条。
            log.warn("LLM 节点 {} 失败，转为降级继续: {}", nodeId, e.getMessage());
            ctx.partial = true;
            ctx.llmError = e.getMessage();
            ctx.outputs.put(nodeId, deterministicSummary(ctx));
        }
    }

    /**
     * TOOL 节点 —— 真的调 {@link ToolExecutor}。
     *
     * <p>产物存成两种 key：
     * <ul>
     *   <li>{@code <nodeId>} = {@code {"query_bank_flow":{...},"query_voucher":{...}}}
     *       （按工具名<b>嵌套</b>的结构 —— persist 的 {@code kpi_json}、健康检查 26.3 的
     *       对表、前端取数都按这个形状读，<b>三处必须一致</b>）；</li>
     *   <li>{@code <nodeId>.<toolName>} = 工具返回的原始 JSON 字符串（单份）。</li>
     * </ul>
     *
     * <p><b>TOOL 失败是硬失败</b> —— 取不到数就没有数字，落一份空日报比不落更糟。
     */
    private void runTool(JsonNode nodeDef, WorkflowContext ctx) throws Exception {
        String nodeId = nodeDef.path("id").asText();
        JsonNode toolsNode = nodeDef.path("tools");
        if (toolsNode == null || !toolsNode.isArray() || toolsNode.isEmpty()) {
            throw new OpcException("TOOL 节点 " + nodeId + " 没有声明 tools");
        }
        Map<String, Object> vars = buildVars(ctx);
        AgentRuntime.AgentTaskRequest req = AgentRuntime.AgentTaskRequest.builder()
                .companyId(ctx.companyId)
                .userId(ctx.userId)
                .sessionId("wf-" + nodeId + "-" + System.currentTimeMillis())
                .build();
        // args 是**节点级**的（同一节点的所有工具共用一份 args）—— 本轮 dag 里两个工具区间相同。
        String argsJson = renderTemplate(nodeDef.path("args").toString(), vars);

        Map<String, Object> merged = new LinkedHashMap<>();
        for (JsonNode t : toolsNode) {
            String toolName = t.asText();
            String result = toolExecutor.execute(toolName, argsJson, req);
            // 先解析再落产出 —— 解析失败就抛，**不能**把读不出来的返回当成「0 笔」。
            Map<String, Object> parsed = parseToolResult(toolName, result);
            ctx.outputs.put(nodeId + "." + toolName, result);
            merged.put(toolName, parsed);
        }
        ctx.outputs.put(nodeId, objectMapper.writeValueAsString(merged));
    }

    /**
     * 工具返回值 → Map（保留 BigDecimal 的 scale，见 {@link #objectMapper}）。
     *
     * <p><b>解析失败 → 抛异常</b>（硬失败）。不许把「读不出来」伪装成「0 笔」：
     * 那样日报上会写着「共 0 笔，收 0，支 0」—— 那是一个<b>编出来的数字</b>。
     * 工具的返回读不出来 = 这件事没做成 → 整条 run FAILED、产出物不落表。
     *
     * <p>注意与「<b>字段缺失</b>但解析成功」分得开：后者按 brief 的口径取 {@code 0}
     * （见 {@link DeterministicAggregator}）—— 那不是编的，是「这个字段没有」。
     *
     * @param toolName 工具名（只进错误信息，便于定位）
     * @param result   工具返回的原始 JSON 字符串
     */
    private Map<String, Object> parseToolResult(String toolName, String result) {
        if (result == null || result.isBlank()) {
            throw new OpcException("工具 " + toolName + " 没有返回内容 —— 不当成「0 笔」");
        }
        try {
            return objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new OpcException("工具 " + toolName + " 的返回值不是 JSON 对象: " + e.getMessage());
        }
    }

    /**
     * LLM 失败时的确定性摘要。
     *
     * <p>扫 {@code ctx.outputs} 里形如 {@code <nodeId>.query_bank_flow} /
     * {@code <nodeId>.query_voucher} 的 key（<b>按 key 排序</b> → 顺序确定，
     * 健康检查可以按字面断言），逐个解析后交给 {@link DeterministicAggregator}，
     * 多行用换行拼接。
     */
    private String deterministicSummary(WorkflowContext ctx) {
        String yesterday = (String) builtinVars().get("yesterday");
        List<String> keys = new ArrayList<>(ctx.outputs.keySet());
        Collections.sort(keys);
        List<String> parts = new ArrayList<>();
        for (String key : keys) {
            Object value = ctx.outputs.get(key);
            if (!(value instanceof String text) || text.isBlank()) {
                continue;
            }
            if (key.endsWith(".query_bank_flow")) {
                parts.add(DeterministicAggregator.bankFlowSummary(
                        parseToolResult("query_bank_flow", text), yesterday));
            } else if (key.endsWith(".query_voucher")) {
                parts.add(DeterministicAggregator.voucherSummary(
                        parseToolResult("query_voucher", text), yesterday));
            }
        }
        if (parts.isEmpty()) {
            // 没有工具数据可聚合 —— 但**不能留空**：下游 persist 要拿这段内容。
            return DeterministicAggregator.PREFIX + " 未取到可聚合的工具数据。";
        }
        return String.join("\n", parts);
    }

    /**
     * 业务时区。容器 JVM 是 UTC —— 用 {@code systemDefault()} 会差一天。
     *
     * <p><b>为什么差一天这么要紧</b>：北京时间 16:00–24:00 之间，UTC 的「今天」比北京**少一天**
     * → {@code ${yesterday}} 渲染成**今天**、{@code ${monthAgo}} 整体偏移一天
     * → 工具查错区间、日报写错日期，<b>而没有任何一条测试会红</b>。
     */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 业务「今天」：{@code clock} 决定「此刻」，{@link #BUSINESS_ZONE} 决定「哪一天」。
     *
     * <p>抽成静态方法是为了让「用哪个时区」成为<b>可断言的事实</b>：
     * 传一个固定时钟进来，就能断言它算出来的是北京的那一天。
     */
    static LocalDate businessToday(Clock clock) {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    /**
     * 业务时钟 —— <b>子类可覆盖</b>（测试用固定时钟把「今天」钉成可复现的事实）。
     *
     * <p>为什么不是构造参数：{@code @InjectMocks} 的既有测试会因为多一个构造参数而拿到 null。
     */
    protected Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * 内置日期变量 —— 北京时间口径。
     *
     * <p>容器 JVM 是 UTC（实测 {@code NOW()} 与宿主机差 8 小时），
     * {@code LocalDate.now()} 会差 8 小时 → 显式用 {@link #BUSINESS_ZONE}。
     */
    private Map<String, Object> builtinVars() {
        LocalDate today = businessToday(clock());
        Map<String, Object> vars = new HashMap<>();
        vars.put("today", today.toString());
        vars.put("yesterday", today.minusDays(1).toString());
        vars.put("weekAgo", today.minusDays(7).toString());
        vars.put("monthAgo", today.minusDays(30).toString());
        return vars;
    }

    /** 模板变量：入参 + 上游产出 + 内置日期变量 + 只读的 companyId。 */
    private Map<String, Object> buildVars(WorkflowContext ctx) {
        Map<String, Object> vars = new HashMap<>();
        vars.putAll(ctx.inputParams);
        vars.putAll(ctx.outputs);
        vars.putAll(builtinVars());
        if (ctx.companyId != null) {
            vars.put("companyId", ctx.companyId);
        }
        return vars;
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

    /**
     * 渲染 {@code ${...}} 占位符 —— 委托 {@link WorkflowTemplate}。
     *
     * <p>渲染逻辑只有一份（裁定 R-99）：persist 处理器也要渲染
     * {@code ${yesterday}}，两份实现两份口径，迟早会漂。</p>
     */
    private String renderTemplate(String template, Map<String, Object> vars) {
        return WorkflowTemplate.render(template, vars);
    }

    @lombok.Data
    public static class WorkflowDefinition {
        private String dagJson;

        /**
         * 归属公司（租户）。工作流是定时触发的，没有登录态，
         * LLM 节点的用量要记到谁名下，只能由调用方显式带进来。
         */
        private Long companyId;

        /**
         * 触发者用户 ID。定时触发没有登录用户 → 0（系统）。
         */
        private Long userId;

        /**
         * 工作流运行记录 ID —— 调用方（触发服务）写完运行记录后回填，
         * 产出物落表时要带上它（溯源）。
         */
        private Long runId;
    }

    @lombok.Data
    public static class WorkflowContext {
        private Map<String, Object> inputParams;
        private Map<String, Object> outputs;
        private List<StepLog> steps;
        /** 归属公司（来自 {@link WorkflowDefinition#getCompanyId()}）。 */
        private Long companyId;
        /** 触发者（来自 {@link WorkflowDefinition#getUserId()}）。 */
        private Long userId;
        /**
         * 运行记录 id（来自 {@link WorkflowDefinition#getRunId()}）——
         * 产出物落表时要带上它（{@code source_workflow_run_id}）。
         */
        private Long runId;
        /** LLM 节点实际用的模型名；LLM 没跑成功时为 null。 */
        private String llmModel;
        /** 是否有节点降级（LLM 失败 → 走确定性聚合）。 */
        private boolean partial;
        /** LLM 失败的原因；没失败时为 null。 */
        private String llmError;
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
        /**
         * 是否有节点降级 —— true 表示「本次运行<b>有节点降级</b>」（LLM 软失败 → 走了确定性聚合）。
         *
         * <p><b>它不代表产出物落过表</b> —— 产出物落不落表取决于 dag 有没有
         * {@code persist} 声明。没有 persist 声明的 dag 记 {@code PARTIAL} 时，
         * 业务表（{@code opc_insight_daily_report}）里<b>一行都没有</b>。</p>
         */
        private boolean partial;
        /** LLM 失败的原因（降级时非空）；没降级时为 null。 */
        private String llmError;
    }

}
