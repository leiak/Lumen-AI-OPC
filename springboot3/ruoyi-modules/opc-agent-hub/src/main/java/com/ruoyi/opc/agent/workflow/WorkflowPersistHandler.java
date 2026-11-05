package com.ruoyi.opc.agent.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.agent.feign.RemoteInsightService;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流的 persist 处理器 —— 把引擎的产出物<b>确定性地</b>交给 insight 落表。
 *
 * <p><b>为什么要有这一步</b>：以前工作流跑完，产出物只躺在
 * {@code opc_agent_workflow_run.output_result} 里 —— 那是<b>运行日志</b>，
 * 不是业务表。业务上「昨天的日报」是在 {@code opc_insight_daily_report} 里查的，
 * 而那张表跟工作流没有任何关系。于是工作流取到的真实流水/凭证，
 * 业务侧<b>一条都看不到</b>。</p>
 *
 * <p><b>为什么落表是确定性的</b>：产出物从哪个节点取、写成什么字段，
 * 全部由 dag 里的 {@code persist} 声明决定，<b>不交给模型决定</b>。
 * 模型只负责写文案（summary），数字一律来自工具的返回值。</p>
 *
 * <p><b>kpi_json 一字不改</b>（裁定 R-104）：引擎的 {@code ctx.outputs[<nodeId>]}
 * 就是 {@code {"query_bank_flow":{...},"query_voucher":{...}}} ——
 * 直接原样送出去，<b>不重算、不改口径、不裁剪</b>。数字的来源只有一处
 * （工具的返回值），任何加工都会让健康检查 26.3 的逐笔对账失真。</p>
 *
 * <p><b>为什么落表失败要抛</b>：产出物没落表 = 这件事没做成 →
 * 引擎 catch → 整条 run 记 FAILED。不能让 run 记成功。</p>
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowPersistHandler {

    /** 支持的落表目标 —— 目前只有日报。 */
    private static final String TARGET_DAILY_REPORT = "daily_report";

    /** report_status：LLM 也参与了（数字是真的 + 有解读）。 */
    private static final String REPORT_STATUS_FULL = "FULL";

    /** report_status：数字是真的，但缺了 LLM 解读。 */
    private static final String REPORT_STATUS_PARTIAL = "PARTIAL";

    /**
     * {@code llm_used} 列在「LLM 根本没参与」时的取值。
     *
     * <p><b>注意跟 report_status 的 "PARTIAL" 分得开</b>（裁定 R-96）：
     * {@code llm_used} 列的语义是「生成使用的 LLM 模型 ID」，
     * 往里塞状态是把状态塞进模型 ID 列。
     * {@code "FALLBACK"} = LLM 根本没参与；{@code report_status="PARTIAL"} =
     * 数字是真的、但缺了 LLM 解读。工作流路径<b>永远不写</b>
     * {@code report_status="FALLBACK"}。</p>
     */
    private static final String LLM_USED_FALLBACK = "FALLBACK";

    /**
     * 只用来校验 kpi_json 是不是合法 JSON —— <b>不重新序列化</b>（重新序列化会改数字的形态）。
     *
     * <p><b>比接收侧严一点是对的</b>：产出侧知道自己的形状（kpi_json 是个对象），
     * 而接收侧的 {@code validateKpiJson} 用的是宽松的 {@code readTree}。
     * 这里开 {@code FAIL_ON_TRAILING_TOKENS} —— 默认它是关的，
     * 于是 {@code {"a":1} {"b":2}}（两个 JSON 文档拼在一起）会被当成合法 JSON 放过去。</p>
     */
    private static final ObjectReader STRICT_JSON = new ObjectMapper()
            .reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final RemoteInsightService insightService;

    /**
     * 执行一次 persist 声明。
     *
     * @param persistNode dag 里的 {@code persist} 节点
     * @param ctx         工作流上下文（产出物、归属、LLM 成败）
     * @param vars        模板变量（引擎的 {@code buildVars(ctx)}，已含内置日期变量）
     * @return 落表结果（行 id）
     */
    public PersistOutcome persist(JsonNode persistNode, WorkflowEngine.WorkflowContext ctx,
                                   Map<String, Object> vars) {
        if (persistNode == null || persistNode.isNull()) {
            throw new OpcException("persist 声明为空");
        }
        String target = persistNode.path("target").asText("");
        if (!TARGET_DAILY_REPORT.equals(target)) {
            throw new OpcException("不支持的 persist 目标: " + target);
        }

        // ${yesterday} 等占位符由引擎的 vars 渲染 —— handler 不自己算日期（裁定 R-99）
        String date = WorkflowTemplate.render(persistNode.path("date").asText(""), vars);
        if (date.isBlank()) {
            throw new OpcException("persist 声明缺少 date");
        }

        String kpiFrom = persistNode.path("kpiFrom").asText("");
        String summaryFrom = persistNode.path("summaryFrom").asText("");

        String kpiJson = requireKpiJson(ctx, kpiFrom);
        String summary = requireSummary(ctx, summaryFrom);

        Map<String, Object> body = new HashMap<>();
        body.put("companyId", ctx.getCompanyId());
        body.put("period", date);
        body.put("summaryMd", summary);
        body.put("adviceMd", null);              // 本轮没有独立的建议节点
        body.put("kpiJson", kpiJson);            // 工具节点的原始 JSON —— 一字不改
        body.put("llmUsed", llmUsed(ctx));
        body.put("reportStatus", ctx.isPartial() ? REPORT_STATUS_PARTIAL : REPORT_STATUS_FULL);
        // 注意：body 里**没有** reportSource —— 来源由接收侧按端点身份写死（裁定 R-97）
        body.put("sourceWorkflowRunId", ctx.getRunId());

        R<Long> r;
        try {
            r = insightService.upsertDailyReport(body);
        } catch (Exception e) {
            // OpcException 没有 (String, Throwable) 构造器（共享库 opc-common 的现状，
            // 不为本 Task 改它）→ 至少把**异常类名**拼进消息，
            // 否则「连接被拒」这类故障在 run 记录里只剩一行说不清的消息。
            throw new OpcException("日报落表失败(" + e.getClass().getSimpleName() + "): " + e.getMessage());
        }
        // HTTP 200 不代表成功（CLAUDE.md 坑 #8）—— 必须查 body.code
        if (r == null) {
            throw new OpcException("日报落表失败: insight 没有返回结果");
        }
        if (r.getCode() != R.SUCCESS) {
            throw new OpcException("日报落表失败: " + r.getMsg());
        }
        log.info("[workflow-persist] 日报已落表 companyId={} period={} runId={} rowId={}",
                ctx.getCompanyId(), date, ctx.getRunId(), r.getData());
        return new PersistOutcome(true, r.getData());
    }

    /**
     * {@code llm_used} —— 模型名，或 {@code "FALLBACK"}（LLM 根本没参与）。
     *
     * <p>写成状态（{@code "PARTIAL"}）是把状态塞进模型 ID 列（裁定 R-96）。</p>
     *
     * <p><b>它是「最后一次成功的 LLM 节点用的模型名」</b>：
     * {@code ctx.llmModel} 由引擎在每个 LLM 节点成功时写入，后写的覆盖先写的。
     * 一条 dag 上有多个 LLM 节点时，它<b>只代表最后一个成功的那一个</b> ——
     * 比如 n1 成功、n2 失败，那 {@code llm_used} 是 n1 的模型名，
     * 而 {@code report_status} 是 {@code PARTIAL}。
     * 读这一列的时候别以为整份文案都是这个模型写的。</p>
     */
    private String llmUsed(WorkflowEngine.WorkflowContext ctx) {
        String model = ctx.getLlmModel();
        return (model == null || model.isBlank()) ? LLM_USED_FALLBACK : model;
    }

    /**
     * 取 kpi 节点的产出 —— 必须是<b>合法 JSON 的对象</b>。
     *
     * <p>取值失败 / 不是字符串 / 不是合法 JSON / 是 JSON 但不是对象 / JSON 后面还有尾巴
     * → 一律抛。别把坏 JSON 送出去等对方报错，也别把读不出来伪装成「0 笔」。</p>
     */
    private String requireKpiJson(WorkflowEngine.WorkflowContext ctx, String kpiFrom) {
        Object raw = output(ctx, kpiFrom, "kpi");
        if (!(raw instanceof String text)) {
            throw new OpcException("persist 声明的 kpi 节点产出不是字符串: " + kpiFrom);
        }
        JsonNode parsed;
        try {
            parsed = STRICT_JSON.readTree(text);
        } catch (Exception e) {
            // 分清两种坏法：根本不是 JSON / 是 JSON 但后面还跟着多余的东西
            String detail = (e.getMessage() != null && e.getMessage().contains("Trailing token"))
                    ? "JSON 后面还有多余的尾巴"
                    : "不是合法 JSON";
            throw new OpcException("persist 声明的 kpi 节点产出" + detail + ": " + kpiFrom);
        }
        if (parsed == null || parsed.isMissingNode()) {
            throw new OpcException("persist 声明的 kpi 节点产出不是合法 JSON: " + kpiFrom);
        }
        if (!parsed.isObject()) {
            throw new OpcException("persist 声明的 kpi 节点产出不是 JSON 对象"
                    + "（kpi_json 的形状是对象）: " + kpiFrom);
        }
        return text;
    }

    /**
     * 取 summary 节点的产出 —— LLM 成功时是文案，LLM 软失败时是引擎的确定性聚合摘要
     * （<b>非空</b>，见 {@code WorkflowEngine#runLlm}）。
     *
     * <p>空白摘要也挡掉：空的东西默默存进 {@code summary_md}，
     * 页面上就是一张没有正文的日报 —— 「读不出来」和「是空的」要分得开。</p>
     */
    private String requireSummary(WorkflowEngine.WorkflowContext ctx, String summaryFrom) {
        Object raw = output(ctx, summaryFrom, "summary");
        if (raw == null) {
            // null 不是「不是字符串」—— 是上游 LLM 没给出正文。
            // 2026-11-05 线上这条消息写的是「产出不是字符串: n2」，
            // 把锅甩给了一个完全无辜的节点（真凶是 provider 报成功但内容是空的）。
            throw new OpcException("persist 声明的 summary 节点产出为 null —— "
                    + "上游 LLM 没返回正文（provider 报成功但内容是空的，"
                    + "常见于思考 token 吃满了 max_tokens）: " + summaryFrom);
        }
        if (!(raw instanceof String text)) {
            throw new OpcException("persist 声明的 summary 节点产出不是字符串: " + summaryFrom);
        }
        if (text.isBlank()) {
            throw new OpcException("persist 声明的 summary 节点产出是空的: " + summaryFrom);
        }
        return text;
    }

    /** 取节点产出；没有产出 → 抛（跟「产出是空串」分得开）。 */
    private Object output(WorkflowEngine.WorkflowContext ctx, String nodeId, String kind) {
        Map<String, Object> outputs = ctx.getOutputs();
        if (nodeId == null || nodeId.isBlank()) {
            throw new OpcException("persist 声明缺少 " + kind + "From");
        }
        if (outputs == null || !outputs.containsKey(nodeId)) {
            throw new OpcException("persist 声明的 " + kind + " 节点没有产出: " + nodeId);
        }
        return outputs.get(nodeId);
    }

    /**
     * 落表结果。
     *
     * @param ok    是否落表成功（现在要么成功要么抛异常，所以恒为 true）
     * @param rowId 落表后的行 id（来自 {@code R<Long>.getData()}）
     */
    public record PersistOutcome(boolean ok, Long rowId) {
    }

}
