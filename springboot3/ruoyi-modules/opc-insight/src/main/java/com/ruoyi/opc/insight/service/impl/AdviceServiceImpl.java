package com.ruoyi.opc.insight.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.domain.OpcInsightAdvice;
import com.ruoyi.opc.insight.mapper.OpcInsightAdviceMapper;
import com.ruoyi.opc.insight.service.IAdviceService;
import com.ruoyi.opc.insight.vo.AdviceVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * INSIGHT 决策建议服务实现（M4 Task 9）。
 *
 * <p><b>主流程</b>（{@link #generate(Long, String)}）：
 * <ol>
 *   <li>参数校验：companyId 非空且 >0、topic 必须在 SUPPORTED_TOPICS 内</li>
 *   <li>{@link OpcInsightAdviceMapper#selectRecent} 查 7 天内缓存</li>
 *   <li>命中 → 直接返回（跳过 LLM 调用）</li>
 *   <li>未命中 → 调 {@link LlmGateway#chat} 生成建议；任意异常走 fallback 模板</li>
 *   <li>落库 {@code opc_insight_advice}（append-only，无 UK 约束）</li>
 * </ol>
 *
 * <p><b>为什么 LLM 失败不阻断落库</b>：与 {@link DailyReportServiceImpl} 同源
 * 设计 —— 决策建议的"占位文本"也是有价值的（前端能展示「AI 服务暂不可用」，
 * 不至于 500）。LLM 失败时落库 {@code llm_used="FALLBACK"} + 固定模板，
 * 运维可通过此标记识别降级时段。</p>
 *
 * <p><b>缓存窗口选 7 天</b>：业务语义是「一周内同一公司同一主题的建议不应重复」。
 * 时间过短会增加 LLM 成本，过长会让建议失效。7 天是可配置项，参数从
 * {@link OpcInsightAdviceMapper#selectRecent} 传入。</p>
 *
 * <p><b>模块解耦说明</b>：spec 原本要求 {@code llmGateway.chatAdvice(topic, kpi)}
 * 直接调用，但 opc-ai-core 的 {@link LlmGateway} 没有 {@code chatAdvice} 方法
 * （仅通用 {@code chat}）—— 本实现通过 {@code chat()} 适配，与
 * {@link DailyReportServiceImpl} 的模式一致（参见 M4 plan §Task 8 lesson L8）。</p>
 *
 * <p><b>为什么不复用 {@code SecurityUtils.getCompanyId()}</b>：spec 在
 * test #5 提到 {@code param.companyId != SecurityUtils.getCompanyId()} 的
 * 安全检查，但 RuoYi 的 {@code SecurityUtils} 仅暴露 {@code getUserId()} /
 * {@code getUsername()}（无 {@code getCompanyId()}）；本 Task 9 把 controller
 * 入口限定为 controller 层做 userId→companyId 解析（M4 Task 10/AdviceController
 * 负责），service 层只接收已解析的 companyId 参数。{@link #generate} 内部用
 * {@code >0} 校验防止上游传入 0/负数。</p>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdviceServiceImpl implements IAdviceService {

    private static final String LOG_PREFIX = "[AdviceService] ";

    /** LLM 失败时的 advice 正文前缀（与 DailyReportServiceImpl 对齐） */
    private static final String FALLBACK_PREFIX = "[降级建议·未走 LLM] ";

    /** 缓存窗口（天）—— spec §Task 9 line 1059 显式指定 7 */
    private static final int CACHE_WINDOW_DAYS = 7;

    /** 默认 / 上限 listByCompany 条数 */
    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;

    /** LLM 调用场景标识（用于 LlmGateway ChatContext.scene 计量埋点） */
    private static final String SCENE_ADVICE = "INSIGHT_ADVICE";

    /** 默认 confidence（LLM 未返回时） */
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.50");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 允许的 topic 集合。
     *
     * <p>snake_case 是 LLM prompt 中 {@code [topic]} 占位符的预期格式；
     * 增加新主题需同时：1) 在此 Set 注册；2) 评估 LLM prompt 模板是否需要调整；
     * 3) 通知前端 {@code /opc/insight/advice} 页 topic 选择器更新。</p>
     */
    private static final Set<String> SUPPORTED_TOPICS = Set.of(
            "cost_optimization",
            "revenue_growth",
            "cashflow_health",
            "tax_planning",
            "risk_warning"
    );

    private final OpcInsightAdviceMapper mapper;
    private final LlmGateway llmGateway;

    /**
     * Per-(companyId, topic) 锁：保证同一公司同一主题的并发 generate() 串行化，
     * 避免两个并发请求都通过 7 天缓存检查、各自调一次 LLM、然后都 insert。
     *
     * <p><b>为什么不用分布式锁</b>：opc-insight 是单实例的 Spring Boot 服务
     * （部署文档见 deploy/helm/opc/templates/service-insight.yaml），多副本
     * 场景下锁粒度仍可能产生竞态，但 LLM 调用本身的幂等成本（双倍 token）
     * 在 MVP 阶段是可接受的；后续如需严格防重，可引入 Redis SETNX。
     *
     * <p><b>为什么不清理锁 entry</b>：每个 ReentrantLock 对象仅约 40 字节，
     * 即便全公司×全 topic 也只占几 KB，长期持有可接受。清理（如 remove on
     * unlock）需额外同步，反而引入新竞态。
     */
    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    // ============================================================
    // 1. 生成（带 7 天缓存）
    // ============================================================

    @Override
    public AdviceVo generate(Long companyId, String topic) {
        // 1) 参数校验
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        if (companyId <= 0) {
            throw new OpcException("companyId 必须大于 0: " + companyId);
        }
        if (topic == null || topic.isBlank()) {
            throw new OpcException("topic 不能为空");
        }
        if (!SUPPORTED_TOPICS.contains(topic)) {
            throw new OpcException(
                    "topic 不支持: " + topic + ", 仅支持 " + SUPPORTED_TOPICS);
        }

        // 2) 串行化：同一 (companyId, topic) 同时只能有一个线程进入 LLM 调用
        String lockKey = companyId + ":" + topic;
        ReentrantLock lock = LOCKS.computeIfAbsent(lockKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // 3) double-check：持锁后再查一次缓存，避免前一个并发线程已写入但我们没看到
            OpcInsightAdvice cached = mapper.selectRecent(companyId, topic, CACHE_WINDOW_DAYS);
            if (cached != null) {
                log.info("{}cache hit companyId={} topic={} id={}", LOG_PREFIX, companyId, topic, cached.getId());
                return toVo(cached);
            }

            // 4) 缓存未命中 → 调 LLM 生成（任意异常 → fallback 模板）
            LlmOutcome outcome = callLlmOrFallback(topic, companyId);

            // 5) 落库
            OpcInsightAdvice advice = OpcInsightAdvice.builder()
                    .companyId(companyId)
                    .topic(topic)
                    .adviceMd(outcome.advice)
                    .llmUsed(outcome.llmUsed)
                    .confidence(outcome.confidence)
                    .createTime(LocalDateTime.now())
                    .build();
            mapper.insert(advice);
            log.info("{} advice generated: companyId={} topic={} id={} llmUsed={}",
                    LOG_PREFIX, companyId, topic, advice.getId(), outcome.llmUsed);
            return toVo(advice);
        } finally {
            lock.unlock();
        }
    }

    // ============================================================
    // 2. 按公司列历史
    // ============================================================

    @Override
    public List<AdviceVo> listByCompany(Long companyId, Integer limit) {
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        int lim = (limit == null || limit <= 0) ? DEFAULT_LIST_LIMIT : Math.min(limit, MAX_LIST_LIMIT);
        return mapper.selectByCompany(companyId, lim).stream()
                .map(this::toVo)
                .toList();
    }

    // ============================================================
    // 3. 重生成（绕过缓存）
    // ============================================================

    @Override
    public AdviceVo regenerate(Long id) {
        if (id == null) {
            throw new OpcException("id 不能为空");
        }
        OpcInsightAdvice existing = mapper.selectById(id);
        if (existing == null) {
            throw new OpcException("advice 不存在: id=" + id);
        }
        // 防御性 topic 白名单校验：DB 中可能存在历史脏数据（旧版本写入了已废弃 topic），
        // regenerate 时若传给 LLM 会导致 prompt 不可控，违反 spec。
        if (!SUPPORTED_TOPICS.contains(existing.getTopic())) {
            throw new OpcException("advice.topic 不支持: " + existing.getTopic());
        }

        // 直接调 LLM，不走 7 天缓存
        LlmOutcome outcome = callLlmOrFallback(existing.getTopic(), existing.getCompanyId());
        existing.setAdviceMd(outcome.advice);
        existing.setLlmUsed(outcome.llmUsed);
        existing.setConfidence(outcome.confidence);
        // update_time 由 MySQL ON UPDATE CURRENT_TIMESTAMP 自动维护
        mapper.updateById(existing);
        log.info("{} advice regenerated: id={} topic={} llmUsed={}",
                LOG_PREFIX, id, existing.getTopic(), outcome.llmUsed);
        return toVo(existing);
    }

    // ============================================================
    // 4. 按主键查询
    // ============================================================

    @Override
    public AdviceVo getById(Long id) {
        if (id == null) {
            throw new OpcException("id 不能为空");
        }
        OpcInsightAdvice advice = mapper.selectById(id);
        if (advice == null) {
            throw new OpcException("advice 不存在: id=" + id);
        }
        return toVo(advice);
    }

    // ============================================================
    // 内部：LLM 调用 + 回退
    // ============================================================

    /**
     * 调 LLM 拿建议正文 + 置信度。任意异常 → fallback outcome。
     */
    private LlmOutcome callLlmOrFallback(String topic, Long companyId) {
        try {
            ChatResponse resp = llmGateway.chat(
                    buildPrompt(topic),
                    ChatModelProvider.ChatOptions.builder()
                            .temperature(0.4)
                            .maxTokens(600)
                            .build(),
                    LlmGateway.ChatContext.builder()
                            .companyId(companyId)
                            .scene(SCENE_ADVICE)
                            .build());

            if (resp != null
                    && Boolean.TRUE.equals(resp.getSuccess())
                    && resp.getContent() != null
                    && !resp.getContent().isBlank()) {
                ParsedLlm parsed = parseLlmContent(resp.getContent());
                String model = resp.getModel() != null ? resp.getModel() : "unknown";
                return new LlmOutcome(parsed.advice, model, parsed.confidence);
            }
            log.warn("{} LLM 返回失败/空，走 fallback: companyId={} topic={}",
                    LOG_PREFIX, companyId, topic);
        } catch (Exception e) {
            log.warn("{} LLM 异常 ({})，走 fallback: companyId={} topic={}",
                    LOG_PREFIX, e.getClass().getSimpleName() + ":" + e.getMessage(),
                    companyId, topic);
        }
        return new LlmOutcome(
                FALLBACK_PREFIX + "AI 服务暂不可用，请稍后重试。",
                "FALLBACK",
                DEFAULT_CONFIDENCE
        );
    }

    private List<ChatMessage> buildPrompt(String topic) {
        String sys = "你是 OPC 数字员工——决策建议生成器。请基于用户给出的 topic，"
                + "生成一段中文决策建议（Markdown 格式），并以严格 JSON 形式返回: "
                + "{\"advice\":\"<≤200 字的建议正文>\",\"confidence\":<0.00-1.00>}"
                + "。仅输出 JSON,不要解释。请聚焦可执行的下一步动作，不要泛泛而谈。";
        String user = String.format("companyId 维度；topic=%s", topic);
        return List.of(ChatMessage.system(sys), ChatMessage.user(user));
    }

    /**
     * 解析 LLM 响应为 {advice, confidence}。解析失败 → advice 取原 content，
     * confidence 给默认 0.50（不阻断落库）。
     *
     * <p><b>advice 为空/null 的处理</b>：LLM 返回 {@code {"advice": null, "confidence": 0.75}}
     * 或 {@code {"advice": ""}} 时，落库前会替换为 FALLBACK 模板正文，
     * 避免下游消费方拿到空白 markdown。前端展示「AI 服务暂不可用」比空白更友好。
     */
    private ParsedLlm parseLlmContent(String content) {
        if (content == null || content.isBlank()) {
            return new ParsedLlm(buildFallbackAdvice(), DEFAULT_CONFIDENCE);
        }
        String trimmed = stripCodeFence(content);
        try {
            Map<String, Object> map = MAPPER.readValue(trimmed, new TypeReference<>() {});
            String rawAdvice = map.get("advice") == null ? null : map.get("advice").toString().trim();
            String advice = (rawAdvice == null || rawAdvice.isBlank())
                    ? buildFallbackAdvice()
                    : rawAdvice;
            BigDecimal confidence = parseConfidence(map.get("confidence"));
            return new ParsedLlm(advice, confidence);
        } catch (Exception e) {
            log.warn("{} LLM 返回非 JSON，原样作为 advice：{}", LOG_PREFIX, e.getMessage());
            return new ParsedLlm(content.trim(), DEFAULT_CONFIDENCE);
        }
    }

    private static String buildFallbackAdvice() {
        return FALLBACK_PREFIX + "LLM 未返回有效建议正文，请稍后重试。";
    }

    /**
     * LLM 返回的 confidence 可能是 Double / String / BigDecimal；统一归一为
     * DECIMAL(3,2) 的 BigDecimal。范围越界 → 夹断到 [0, 1]。
     */
    private static BigDecimal parseConfidence(Object raw) {
        if (raw == null) {
            return DEFAULT_CONFIDENCE;
        }
        BigDecimal bd;
        try {
            bd = new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            return DEFAULT_CONFIDENCE;
        }
        if (bd.compareTo(BigDecimal.ZERO) < 0) {
            bd = BigDecimal.ZERO;
        } else if (bd.compareTo(BigDecimal.ONE) > 0) {
            bd = BigDecimal.ONE;
        }
        return bd.setScale(2, RoundingMode.HALF_UP);
    }

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

    // ============================================================
    // 内部转换
    // ============================================================

    private AdviceVo toVo(OpcInsightAdvice d) {
        return AdviceVo.builder()
                .id(d.getId())
                .companyId(d.getCompanyId())
                .topic(d.getTopic())
                .adviceMd(d.getAdviceMd())
                .llmUsed(d.getLlmUsed())
                .confidence(d.getConfidence())
                .createTime(d.getCreateTime())
                .build();
    }

    // ============================================================
    // 内部 record-like 数据载体（private 静态类，避免暴露 public API）
    // ============================================================

    private record LlmOutcome(String advice, String llmUsed, BigDecimal confidence) {}

    private record ParsedLlm(String advice, BigDecimal confidence) {}
}