package com.ruoyi.opc.insight.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.domain.OpcInsightAnomaly;
import com.ruoyi.opc.insight.enums.AnomalyLevel;
import com.ruoyi.opc.insight.enums.AnomalyRule;
import com.ruoyi.opc.insight.mapper.OpcInsightAnomalyMapper;
import com.ruoyi.opc.insight.service.IAnomalyService;
import com.ruoyi.opc.insight.service.ISoftAnomalyDetector;
import com.ruoyi.opc.insight.vo.AnomalyVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * INSIGHT 异常检测服务实现（M4 Task 6）。
 *
 * <p><b>扫描算法</b>：
 * <ol>
 *   <li>遍历 {@link AnomalyRule} 8 条硬规则，命中即写入 {@code opc_insight_anomaly}。</li>
 *   <li>若硬规则未命中任何 HIGH 等级异常，调用 {@link ISoftAnomalyDetector}
 *       执行 LLM 软扫；仅持久化置信度 &gt; 0.60 的结果。</li>
 * </ol>
 *
 * <p><b>为什么 HIGH 抑制 LLM</b>：HIGH 异常已说明业务存在严重偏离（如支出&gt;收入、
 * 超大额凭证），LLM 软扫在这种场景下大概率会重复触发相同语义，徒增噪声。
 * 设计目标：<b>硬规则提供确定性，LLM 提供语义补强</b>，不重复告警。
 *
 * <p><b>异常容忍</b>：LLM 软扫抛任何异常仅记 WARN，不影响硬规则结果。整体设计
 * 与 KpiService 的"部分降级"原则保持一致。
 *
 * <p><b>依赖关系</b>：
 * <pre>
 *   AnomalyServiceImpl ──> OpcInsightAnomalyMapper (pure MyBatis)
 *                      └─> ISoftAnomalyDetector (interface in opc-insight)
 *                              └─> LlmSoftAnomalyDetector (impl, 默认 Spring bean)
 *                                      └─> LlmGateway (opc-ai-core)
 * </pre>
 *
 * <p>模块解耦说明：spec 原本要求 {@code llmGateway.chatSoftAnomaly(...)} 直接调用，
 * 但 opc-ai-core 的 {@link com.ruoyi.opc.ai.gateway.llm.LlmGateway} 没有
 * {@code chatSoftAnomaly} 方法（仅通用 {@code chat}）。本实现通过
 * {@link ISoftAnomalyDetector} 接口适配 LLM 调用，单测可独立 mock。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyServiceImpl implements IAnomalyService {

    private final OpcInsightAnomalyMapper mapper;
    private final ISoftAnomalyDetector softDetector;

    /**
     * LLM 软扫置信度阈值；低于此值的返回结果视为噪声，丢弃。
     */
    static final BigDecimal LLM_CONFIDENCE_THRESHOLD = new BigDecimal("0.60");

    private static final String LOG_PREFIX = "[AnomalyService] ";

    /** 默认创建人（schema DEFAULT '' per V20260908 SQL） */
    private static final String DEFAULT_CREATE_BY = "";

    // ============================================================
    // 1. 扫描主流程
    // ============================================================

    @Override
    public List<AnomalyVo> scan(KpiSnapshot snapshot) {
        if (snapshot == null) {
            return Collections.emptyList();
        }

        List<AnomalyVo> anomalies = new ArrayList<>();

        // ----- 1) 硬规则 -----
        for (AnomalyRule rule : AnomalyRule.values()) {
            if (rule.match(snapshot)) {
                anomalies.add(persistHardRule(snapshot, rule));
            }
        }

        // ----- 1.5) 部分降级快照不应触发 LLM 软扫（数据不完整 + LLM = 幻觉异常） -----
        if (snapshot.isPartial()) {
            log.warn("[AnomalyService] scan(companyId={}, period={}, partial=true) — LLM 软扫跳过，仅返回硬规则结果",
                    snapshot.getCompanyId(), snapshot.getPeriod());
            return anomalies;
        }

        // ----- 2) LLM 软扫（仅在无 HIGH 异常时） -----
        boolean hasHigh = anomalies.stream()
                .anyMatch(a -> a.getLevel() == AnomalyLevel.HIGH);
        if (!hasHigh) {
            try {
                List<AnomalyVo> soft = softDetector.detect(snapshot);
                if (soft != null) {
                    for (AnomalyVo vo : soft) {
                        if (vo.getLlmConfidence() != null
                                && vo.getLlmConfidence()
                                    .compareTo(LLM_CONFIDENCE_THRESHOLD) > 0) {
                            vo.setCompanyId(snapshot.getCompanyId());
                            vo.setPeriod(snapshot.getPeriod());
                            vo.setStatus("OPEN");
                            vo.setCreateTime(LocalDateTime.now());
                            anomalies.add(persistAnomaly(vo));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("{} LLM 软扫失败：{}", LOG_PREFIX, e.getMessage());
            }
        }

        return anomalies;
    }

    // ============================================================
    // 2. 查询 / 处置
    // ============================================================

    @Override
    public List<AnomalyVo> listOpen(Long companyId, Integer limit) {
        if (companyId == null) {
            return Collections.emptyList();
        }
        return mapper.selectOpenByCompany(companyId, limit).stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public AnomalyVo getById(Long id) {
        if (id == null) {
            throw new OpcException("id 不能为空");
        }
        OpcInsightAnomaly domain = mapper.selectById(id);
        if (domain == null) {
            throw new OpcException("异常不存在: id=" + id);
        }
        return toVo(domain);
    }

    @Override
    public void acknowledge(Long id) {
        mapper.updateStatus(id, "ACK");
    }

    // ============================================================
    // 3. 内部转换
    // ============================================================

    /**
     * 硬规则命中 → 构建 VO + 持久化 + 回填 id。
     */
    private AnomalyVo persistHardRule(KpiSnapshot snapshot, AnomalyRule rule) {
        AnomalyVo vo = AnomalyVo.builder()
                .companyId(snapshot.getCompanyId())
                .period(snapshot.getPeriod())
                .level(rule.getLevel())
                .ruleCode(rule.name())           // 用 enum.name() 而非 rule.getCode() —— 冗余但保留 code 字段供未来区分
                .description(rule.getDescription())
                .status("OPEN")
                .createTime(LocalDateTime.now())
                .build();
        return persistAnomaly(vo);
    }

    /**
     * 把 VO 转 domain 并写入表，回填 id 到 VO。
     */
    private AnomalyVo persistAnomaly(AnomalyVo vo) {
        OpcInsightAnomaly domain = toDomain(vo);
        mapper.insert(domain);
        vo.setId(domain.getId());
        return vo;
    }

    /**
     * VO → domain。负责 period String → LocalDate、level 枚举 → String 转换；
     * createBy / updateBy 默认 '' per schema。
     */
    private OpcInsightAnomaly toDomain(AnomalyVo vo) {
        return OpcInsightAnomaly.builder()
                .companyId(vo.getCompanyId())
                .period(parsePeriod(vo.getPeriod()))
                .level(vo.getLevel() == null ? null : vo.getLevel().name())
                .ruleCode(vo.getRuleCode())
                .description(vo.getDescription())
                .status(vo.getStatus() == null ? "OPEN" : vo.getStatus())
                .llmConfidence(vo.getLlmConfidence())
                .createBy(DEFAULT_CREATE_BY)
                .updateBy(DEFAULT_CREATE_BY)
                .createTime(vo.getCreateTime())
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * domain → VO。负责 period LocalDate → String、level String → 枚举转换。
     */
    private AnomalyVo toVo(OpcInsightAnomaly d) {
        AnomalyLevel level = null;
        if (d.getLevel() != null) {
            try {
                level = AnomalyLevel.valueOf(d.getLevel());
            } catch (IllegalArgumentException ex) {
                log.warn("{} 未知 level 字符串：{}", LOG_PREFIX, d.getLevel());
            }
        }
        return AnomalyVo.builder()
                .id(d.getId())
                .companyId(d.getCompanyId())
                .period(d.getPeriod() == null ? null : d.getPeriod().toString())
                .level(level)
                .ruleCode(d.getRuleCode())
                .description(d.getDescription())
                .status(d.getStatus())
                .llmConfidence(d.getLlmConfidence())
                .createTime(d.getCreateTime())
                .build();
    }

    /**
     * "YYYY-MM" 或 "YYYY-MM-DD" → LocalDate。解析失败回退到当月 1 号，
     * 与 KpiSnapshot.period 默认逻辑保持一致。
     */
    static LocalDate parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now(ZoneId.systemDefault()).atDay(1);
        }
        try {
            // 优先 YYYY-MM-DD
            if (period.length() >= 10) {
                return LocalDate.parse(period.substring(0, 10));
            }
            // 回退 YYYY-MM → 当月 1 号
            return YearMonth.parse(period).atDay(1);
        } catch (DateTimeParseException e) {
            log.warn("{} period 解析失败，回退当月：{}", LOG_PREFIX, period);
            return YearMonth.now(ZoneId.systemDefault()).atDay(1);
        }
    }
}
