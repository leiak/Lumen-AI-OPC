package com.ruoyi.opc.insight.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.domain.OpcInsightDailyReport;
import com.ruoyi.opc.insight.mapper.OpcInsightDailyReportMapper;
import com.ruoyi.opc.insight.service.IDailyReportService;
import com.ruoyi.opc.insight.service.IKpiService;
import com.ruoyi.opc.insight.vo.DailyReportUpsertRequest;
import com.ruoyi.opc.insight.vo.DailyReportVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * INSIGHT 日报生成服务实现（M4 Task 8）。
 *
 * <p><b>主流程</b>（{@link #generate(Long, String)}）：
 * <ol>
 *   <li>参数校验：companyId 非空、date 必填且符合 {@code yyyy-MM-dd}、不能晚于今天（业务时区）</li>
 *   <li>{@link IKpiService#snapshot} 取 KPI 快照（partial=true 表示数据源降级）</li>
 *   <li>查已有行 → 撞上数字员工写的行则拒绝覆盖（<b>在 LLM 之前</b>，R-91）</li>
 *   <li>调用 {@link LlmGateway#chat} 生成自然语言总结 + 建议</li>
 *   <li>LLM 任意异常 → 回退到固定模板 + 标记 {@code llm_used="FALLBACK"}，保证落库</li>
 *   <li>写入 {@code opc_insight_daily_report}（UK: {@code uk_company_period}），
 *       并标上 {@code report_source="MANUAL"} + {@code report_status}</li>
 * </ol>
 *
 * <p><b>第二条写入路径</b>（{@link #upsertFromWorkflow}）：工作流引擎把算好的内容
 * 调内部端点 {@code POST /opc/insight/internal/daily/upsert} 落库，
 * 来源写死 {@code report_source="AGENT_WORKFLOW"}，并带上 {@code source_workflow_run_id}
 * 让这行日报能反查回产出它的那次运行。</p>
 *
 * <p><b>为什么 LLM 失败不阻断落库</b>：日报的 KPI 数据本身有价值（数字摘要可人工读），
 * AI 解读是加分项而非必选项。{@code OpcFinanceTaxReportServiceImpl} 沿用同样的
 * 「LLM 异常被吞」策略（W2.1 lesson L5）。</p>
 *
 * <p><b>部分降级标记</b>：当 {@code kpi.isPartial()=true}（至少 1 个 Feign 数据源降级）
 * 时，summary 追加 {@code [数据降级]} 标记，供前端展示「数据不完整」徽标。
 * 类似 {@code AnomalyServiceImpl.scan} 跳过 LLM 软扫的设计 —— 数据不可信时
 * 仍要落库但显式标记，避免 AI 幻觉误导。</p>
 *
 * <p><b>依赖关系</b>：
 * <pre>
 *   DailyReportServiceImpl ──> IKpiService   (interface in opc-insight)
 *                        ──> LlmGateway    (opc-ai-core, 直接依赖)
 *                        ──> OpcInsightDailyReportMapper (pure MyBatis)
 * </pre>
 *
 * <p>模块解耦说明：spec 原本要求 {@code llmGateway.chatDailyReport(...)} 直接调用，
 * 但 opc-ai-core 的 {@link LlmGateway} 没有 {@code chatDailyReport} 方法
 * （仅通用 {@code chat}）—— 本实现通过 {@code chat()} 适配，
 * 期望 LLM 返回严格 JSON {@code {"summary":"...", "advice":"..."}}，
 * 解析失败同样走 fallback 路径。该偏差请参见 commit message。</p>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportServiceImpl implements IDailyReportService {

    private static final String LOG_PREFIX = "[DailyReportService] ";

    /** LLM 失败时的固定 summary 模板前缀（与 OpcFinanceTaxReportServiceImpl 对齐） */
    private static final String FALLBACK_SUMMARY_PREFIX = "[自动聚合·未走 LLM]";

    /** LLM 失败时的固定 advice */
    private static final String FALLBACK_ADVICE = "请检查数据完整性后重试，或联系平台支持。";

    /** 降级快照的标记 */
    private static final String PARTIAL_MARKER = "[数据降级]";

    /** system 自动跑时的 createBy / updateBy */
    private static final String SYSTEM_USER = "system";

    /**
     * 业务时区。容器 JVM 是 UTC —— 用 {@code systemDefault()} 会差一天。
     *
     * <p>北京时间 00:00–08:00 之间，{@code LocalDate.now()}（UTC）比北京少一天 →
     * 用户要生成「今天」的日报会被判定成「晚于今天」→ 抛异常拒绝。
     * 「哪一天是今天」是业务口径，不是机器口径。</p>
     */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** report_status 的合法取值集合 —— 存了第四个值前端就说不清它是什么 */
    static final Set<String> REPORT_STATUSES = Set.of("FULL", "PARTIAL", "FALLBACK");

    /** report_source = 数字员工（工作流）写的 */
    static final String SOURCE_AGENT_WORKFLOW = "AGENT_WORKFLOW";

    /** report_source = 手工生成的（公开接口 POST /opc/insight/daily/generate） */
    static final String SOURCE_MANUAL = "MANUAL";

    /** LLM 调用场景标识（用于 LlmGateway ChatContext.scene 计量埋点） */
    private static final String SCENE_DAILY_REPORT = "INSIGHT_DAILY_REPORT";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IKpiService kpiService;
    private final LlmGateway llmGateway;
    private final OpcInsightDailyReportMapper mapper;

    // ============================================================
    // 1. 生成（主流程）
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generate(Long companyId, String date) {
        // 1) 参数校验
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        if (date == null || date.isBlank()) {
            throw new OpcException("date 不能为空");
        }
        LocalDate period;
        try {
            period = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new OpcException("date 格式非法，应为 yyyy-MM-dd: " + date);
        }
        if (period.isAfter(businessToday(Clock.systemDefaultZone()))) {
            throw new OpcException("period 不能晚于今天: " + date);
        }

        // 2) 取 KPI 快照
        KpiSnapshot kpi = kpiService.snapshot(companyId, period.toString());
        if (kpi == null) {
            throw new OpcException("KPI 快照不可用: companyId=" + companyId + " date=" + date);
        }

        // 3) 查已有行 —— 必须在 LLM 之前：撞上数字员工写的行时这次请求确定要失败，
        //    不该先花一次 LLM 调用（裁定 R-91）。
        //    但它必须在 KPI 之后 —— 查重用的 companyId 是 KPI 里的那个（KPI 优先于入参）。
        Long targetCompanyId = kpi.getCompanyId() == null ? companyId : kpi.getCompanyId();
        DailyReportVo exist = mapper.selectByCompanyAndDate(targetCompanyId, period);
        if (exist != null && SOURCE_AGENT_WORKFLOW.equals(exist.getReportSource())) {
            // 数字员工已经产出过这一天的日报 —— 手工生成不许覆盖它。
            // 不许覆盖的理由：数字员工那一版可能已经被下游消费/引用了，
            // 悄悄换掉它等于让已发生的事在事后看起来没发生过。
            throw new OpcException(409,
                    "该日期日报已由数字员工产出，不支持手工覆盖: " + period);
        }

        // 4) 调 LLM 生成（失败 → 模板回退）
        LlmOutcome outcome = callLlmOrFallback(kpi, companyId, date);

        // 5) 部分降级时给 summary 加标记（无论 LLM 成功或回退都生效）
        String summaryMd = outcome.summary;
        if (kpi.isPartial() && !summaryMd.contains(PARTIAL_MARKER)) {
            summaryMd = summaryMd + " " + PARTIAL_MARKER;
        }

        // 6) 落库 (W48.5: idempotent — uk_company_period UNIQUE, 已存在则更新而非报错)
        OpcInsightDailyReport report = OpcInsightDailyReport.builder()
                .companyId(targetCompanyId)
                .period(period)
                .summaryMd(summaryMd)
                .kpiJson(toJson(kpi))
                .adviceMd(outcome.advice)
                .llmUsed(outcome.llmUsed)
                .reportSource(SOURCE_MANUAL)
                .reportStatus(resolveReportStatus(outcome.llmUsed, kpi.isPartial()))
                .createBy(SYSTEM_USER)
                .createTime(LocalDateTime.now())
                .updateBy(SYSTEM_USER)
                .updateTime(LocalDateTime.now())
                .build();
        if (exist == null) {
            mapper.insert(report);
        } else {
            // uk_company_period UNIQUE 冲突 → 先删旧的, 再插新的 (DailyReport 数据是 snapshot, 不需要保留历史)
            mapper.deleteByPrimaryKey(exist.getId());
            report.setId(null);  // 让 insert 回填新 id
            mapper.insert(report);
        }
        log.info("{} daily report generated: companyId={} period={} llmUsed={} (upsert={})",
                LOG_PREFIX, report.getCompanyId(), period, outcome.llmUsed, exist != null);
    }

    // ============================================================
    // 1b. 工作流写入路径（@InnerAuth 内部端点）
    // ============================================================

    /**
     * 工作流把产出物落进日报表。
     *
     * <p><b>与 {@link #generate(Long, String)} 的区别</b>：{@code generate} 自己取 KPI、
     * 调 LLM、落库；本方法的内容全部由调用方（工作流引擎）算好传进来。
     * 两条路径落在同一张表的同一行上 —— 所以落库时来源必须写死：
     * 走到这个方法就说明是工作流写的，调用方无权声明「我是谁写的」（裁定 R-85）。</p>
     *
     * <p><b>已有的同 (company_id, period) 行怎么办</b>：先删旧再插新（snapshot 语义），
     * 与 {@code generate} 一致。这里<b>不</b>做「不许覆盖手工行」的检查 ——
     * 数字员工跑一次工作流产出就该是当天那一份，手工行被覆盖是预期结果
     * （反过来手工生成撞上数字员工的行才要拒绝，见 {@code generate}）。</p>
     *
     * @param req 工作流产出的日报内容
     * @return 落库后的行 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upsertFromWorkflow(DailyReportUpsertRequest req) {
        if (req == null) {
            throw new OpcException(400, "请求体不能为空");
        }
        if (req.getCompanyId() == null) {
            throw new OpcException(400, "companyId 不能为空");
        }
        LocalDate period = parsePeriod(req.getPeriod());
        if (period.isAfter(businessToday(Clock.systemDefaultZone()))) {
            throw new OpcException(400, "period 不能晚于今天: " + req.getPeriod());
        }
        String reportStatus = req.getReportStatus();
        if (reportStatus == null || !REPORT_STATUSES.contains(reportStatus)) {
            // 不许默默存一个别的值 —— 前端要按这三个值渲染 tag，存了第四个页面就说不清它是什么
            throw new OpcException(400, "reportStatus 非法，只能是 FULL/PARTIAL/FALLBACK: " + reportStatus);
        }
        validateKpiJson(req.getKpiJson());

        DailyReportVo exist = mapper.selectByCompanyAndDate(req.getCompanyId(), period);
        OpcInsightDailyReport report = OpcInsightDailyReport.builder()
                .companyId(req.getCompanyId())
                .period(period)
                .summaryMd(req.getSummaryMd())
                // 原样落库 —— 引擎写进来的结构化数字不被改写（裁定 R-90）
                .kpiJson(req.getKpiJson())
                .adviceMd(req.getAdviceMd())
                .llmUsed(req.getLlmUsed())
                .reportSource(SOURCE_AGENT_WORKFLOW)
                .reportStatus(reportStatus)
                .sourceWorkflowRunId(req.getSourceWorkflowRunId())
                .createBy(SYSTEM_USER)
                .createTime(LocalDateTime.now())
                .updateBy(SYSTEM_USER)
                .updateTime(LocalDateTime.now())
                .build();
        if (exist != null) {
            // uk_company_period UNIQUE 冲突 → 先删旧的, 再插新的 (日报数据是 snapshot, 不留历史)
            mapper.deleteByPrimaryKey(exist.getId());
            report.setId(null);  // 让 insert 回填新 id
        }
        mapper.insert(report);
        log.info("{} daily report upserted from workflow: companyId={} period={} runId={} status={}",
                LOG_PREFIX, report.getCompanyId(), period, req.getSourceWorkflowRunId(), reportStatus);
        return report.getId();
    }

    /**
     * 业务「今天」。{@code clock} 决定「此刻」，{@link #BUSINESS_ZONE} 决定「哪一天」。
     *
     * <p>刻意做成静态方法 + 显式传 clock：这样测试能用固定时钟断言，
     * 而不是「测试里用同一个表达式算出期望值」（那种写法在实现被改坏时依然全绿）。</p>
     */
    static LocalDate businessToday(Clock clock) {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    /**
     * 把「LLM 用了没有」+「数据全不全」折成一个 report_status。
     *
     * <p>LLM 挂了 → FALLBACK（这一版只有数字摘要）；LLM 成功但数据降级 → PARTIAL；
     * 都好 → FULL。</p>
     */
    private static String resolveReportStatus(String llmUsed, boolean partial) {
        if ("FALLBACK".equals(llmUsed)) {
            return "FALLBACK";
        }
        return partial ? "PARTIAL" : "FULL";
    }

    /**
     * 解析 period（{@code yyyy-MM-dd}），格式非法 → 400。
     */
    private static LocalDate parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new OpcException(400, "period 不能为空");
        }
        try {
            return LocalDate.parse(period);
        } catch (DateTimeParseException e) {
            throw new OpcException(400, "period 格式非法，应为 yyyy-MM-dd: " + period);
        }
    }

    /**
     * kpiJson 必须是合法 JSON —— {@code kpi_json} 是 MySQL 的 {@code JSON} 列，
     * 塞进去一个坏字符串只会得到 MySQL 错误 3140（一句看不懂的错）。
     *
     * <p>空/空白放过（列可空）；非空白但解析不出来 → 400。</p>
     */
    private static void validateKpiJson(String kpiJson) {
        if (kpiJson == null || kpiJson.isBlank()) {
            return;
        }
        try {
            MAPPER.readTree(kpiJson);
        } catch (Exception e) {
            throw new OpcException(400, "kpiJson 不是合法 JSON: " + e.getMessage());
        }
    }

    // ============================================================
    // 2. 读侧
    // ============================================================

    @Override
    public List<DailyReportVo> listByDateRange(Long companyId, LocalDate from, LocalDate to, Integer limit) {
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        if (from == null || to == null) {
            throw new OpcException("from / to 不能为空");
        }
        if (from.isAfter(to)) {
            throw new OpcException("from 不能晚于 to");
        }
        return mapper.selectByCompanyAndDateRange(companyId, from, to, limit);
    }

    @Override
    public DailyReportVo getById(Long id) {
        if (id == null) {
            throw new OpcException("id 不能为空");
        }
        DailyReportVo vo = mapper.selectById(id);
        if (vo == null) {
            throw new OpcException("日报不存在: id=" + id);
        }
        return vo;
    }

    // ============================================================
    // 3. 内部：LLM 调用 + 回退
    // ============================================================

    /**
     * 调 LLM 拿 summary + advice。任意异常 → 返回 FALLBACK outcome。
     */
    private LlmOutcome callLlmOrFallback(KpiSnapshot kpi, Long companyId, String date) {
        try {
            ChatResponse resp = llmGateway.chat(
                    buildPrompt(kpi),
                    ChatModelProvider.ChatOptions.builder()
                            .temperature(0.3)
                            .maxTokens(800)
                            .build(),
                    LlmGateway.ChatContext.builder()
                            .companyId(companyId)
                            .scene(SCENE_DAILY_REPORT)
                            .build());

            if (resp != null
                    && Boolean.TRUE.equals(resp.getSuccess())
                    && resp.getContent() != null
                    && !resp.getContent().isBlank()) {
                ParsedLlm parsed = parseLlmContent(resp.getContent());
                String model = resp.getModel() != null ? resp.getModel() : "unknown";
                return new LlmOutcome(parsed.summary, parsed.advice, model);
            }
            log.warn("{} LLM 返回失败/空，走 fallback: companyId={} date={}",
                    LOG_PREFIX, companyId, date);
        } catch (Exception e) {
            log.warn("{} LLM 异常 ({})，走 fallback: companyId={} date={}",
                    LOG_PREFIX, e.getClass().getSimpleName() + ":" + e.getMessage(), companyId, date);
        }
        // fallback：固定模板 + KPI 数字摘要
        String fallbackSummary = FALLBACK_SUMMARY_PREFIX + " " + kpiSummaryString(kpi);
        return new LlmOutcome(fallbackSummary, FALLBACK_ADVICE, "FALLBACK");
    }

    private List<ChatMessage> buildPrompt(KpiSnapshot s) {
        String sys = "你是 OPC 数字员工——财务日报生成器。请基于用户给出的 KPI 快照, "
                + "生成一段中文日报（Markdown 格式），并以严格 JSON 形式返回: "
                + "{\"summary\":\"<日报正文, ≤300 字>\",\"advice\":\"<≤100 字的行动建议>\"}。"
                + "仅输出 JSON,不要解释。如果数据来源不完整（partial=true），"
                + "请在 summary 中显式提示「数据不完整」并降低建议的可信度。";
        String user = String.format(
                "companyId=%d; period=%s; revenue=%s; expense=%s; "
                        + "voucherCount=%s; pendingVoucherCount=%s; "
                        + "walletBalance=%s; tokenUsage=%s; activeDays=%s; partial=%s",
                s.getCompanyId(), s.getPeriod(),
                s.getTotalRevenue(), s.getTotalExpense(),
                s.getVoucherCount(), s.getPendingVoucherCount(),
                s.getWalletBalance(), s.getTokenUsage(),
                s.getCompanyActiveDays(), s.isPartial());
        return List.of(ChatMessage.system(sys), ChatMessage.user(user));
    }

    /**
     * 解析 LLM 响应内容为 {summary, advice}。
     * 解析失败或字段缺失 → summary 退回原 content，advice 为空。
     */
    private ParsedLlm parseLlmContent(String content) {
        if (content == null || content.isBlank()) {
            return new ParsedLlm(content == null ? null : content.trim(), null);
        }
        String trimmed = stripCodeFence(content);
        try {
            Map<String, String> map = MAPPER.readValue(trimmed, new TypeReference<>() {});
            String summary = map.get("summary");
            String advice = map.get("advice");
            return new ParsedLlm(
                summary == null ? trimmed : summary.trim(),
                advice == null ? null : advice.trim()
            );
        } catch (Exception e) {
            log.warn("{} LLM 返回非 JSON，原样作为 summary: {}", LOG_PREFIX, e.getMessage());
            return new ParsedLlm(content.trim(), null);
        }
    }

    /**
     * 移除 markdown ```json ... ``` 包裹。
     */
    private static String stripCodeFence(String s) {
        String trimmed = s.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    /**
     * 把 KPI 拍平成单行摘要，用于 fallback 文案。
     */
    private static String kpiSummaryString(KpiSnapshot kpi) {
        Map<String, Object> kv = new LinkedHashMap<>();
        kv.put("收入", kpi.getTotalRevenue());
        kv.put("支出", kpi.getTotalExpense());
        kv.put("凭证", kpi.getVoucherCount());
        kv.put("待审", kpi.getPendingVoucherCount());
        kv.put("钱包", kpi.getWalletBalance());
        kv.put("Token", kpi.getTokenUsage());
        return kv.toString();
    }

    /**
     * 用 Jackson 把 KpiSnapshot 序列化为 JSON 字符串（存 {@code kpi_json} 列）。
     * 失败 → {@code "{}"}（不阻断落库）。
     */
    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("{} KpiSnapshot 序列化失败：{}", LOG_PREFIX, e.getMessage());
            return "{}";
        }
    }

    // ============================================================
    // 内部 record-like 数据载体（private 静态类，避免暴露 public API）
    // ============================================================

    private record LlmOutcome(String summary, String advice, String llmUsed) {}

    private record ParsedLlm(String summary, String advice) {}
}
