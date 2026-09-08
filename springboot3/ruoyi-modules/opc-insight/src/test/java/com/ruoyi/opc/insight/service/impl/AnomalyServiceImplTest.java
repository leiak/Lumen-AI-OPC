package com.ruoyi.opc.insight.service.impl;

import com.ruoyi.opc.insight.domain.OpcInsightAnomaly;
import com.ruoyi.opc.insight.enums.AnomalyLevel;
import com.ruoyi.opc.insight.enums.AnomalyRule;
import com.ruoyi.opc.insight.mapper.OpcInsightAnomalyMapper;
import com.ruoyi.opc.insight.service.ISoftAnomalyDetector;
import com.ruoyi.opc.insight.vo.AnomalyVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AnomalyServiceImpl} 单元测试（M4 Task 6，18 个用例）。
 *
 * <p>覆盖分布：
 * <ol>
 *   <li>16 个硬规则测试（8 规则 × 命中/不命中）</li>
 *   <li>1 个 LLM 软扫参数化测试（{@code @ParameterizedTest}，{@code @ValueSource(ints={0,1,2,3,5})} 5 个用例）</li>
 *   <li>1 个 HIGH 抑制 LLM 软扫测试</li>
 * </ol>
 *
 * <p><b>依赖注入</b>：服务实现使用 {@link ISoftAnomalyDetector} 接口（而非直接
 * LlmGateway），原因是 opc-ai-core 的 {@code LlmGateway} 没有 {@code chatSoftAnomaly}
 * 方法 —— 通过 {@code LlmSoftAnomalyDetector} 适配 {@code LlmGateway.chat}。
 *
 * <p><b>ID 回填模拟</b>：{@code mapper.insert(...)} 用 {@code thenAnswer + AtomicLong}
 * 通过 {@code @Data} 生成的 setter 写 {@code OpcInsightAnomaly.id}，模拟 MyBatis
 * useGeneratedKeys 回填主键（沿用 W2.1 TaxReport 模式）。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyServiceImplTest {

    @Mock
    private OpcInsightAnomalyMapper mapper;

    @Mock
    private ISoftAnomalyDetector softDetector;

    @InjectMocks
    private AnomalyServiceImpl service;

    private static final Long COMPANY_ID = 1001L;
    private static final String PERIOD = "2026-09";

    /** 模拟 MyBatis useGeneratedKeys="true" keyProperty="id" */
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        NEXT_ID.set(1L);
        // 模拟 mapper.insert 后回填 id（@Data 已生成 public setId）
        when(mapper.insert(any(OpcInsightAnomaly.class))).thenAnswer(inv -> {
            OpcInsightAnomaly d = inv.getArgument(0);
            d.setId(NEXT_ID.getAndIncrement());
            return 1;
        });
    }

    // ============================================================
    // 1. 8 条硬规则的命中 / 不命中（16 个测试）
    // ============================================================

    @Test
    void scan_voucherOver100K_matches_high() {
        KpiSnapshot s = baseSnapshot().totalExpense(new BigDecimal("150000")).build();
        List<AnomalyVo> result = service.scan(s);
        assertMatchedRule(result, AnomalyRule.VOUCHER_OVER_100K, AnomalyLevel.HIGH);
    }

    @Test
    void scan_voucherOver100K_noMatch_belowThreshold() {
        KpiSnapshot s = baseSnapshot().totalExpense(new BigDecimal("50000")).build();
        List<AnomalyVo> result = service.scan(s);
        assertRuleNotPresent(result, AnomalyRule.VOUCHER_OVER_100K);
    }

    @Test
    void scan_pendingVoucherOver7D_matches_high() {
        KpiSnapshot s = baseSnapshot().pendingVoucherCount(3L).build();
        List<AnomalyVo> result = service.scan(s);
        assertMatchedRule(result, AnomalyRule.PENDING_VOUCHER_OVER_7D, AnomalyLevel.HIGH);
    }

    @Test
    void scan_pendingVoucherOver7D_noMatch_zeroPending() {
        KpiSnapshot s = baseSnapshot().pendingVoucherCount(0L).build();
        List<AnomalyVo> result = service.scan(s);
        assertRuleNotPresent(result, AnomalyRule.PENDING_VOUCHER_OVER_7D);
    }

    @Test
    void scan_walletBalanceLow_matches_medium() {
        KpiSnapshot s = baseSnapshot().walletBalance(new BigDecimal("50")).build();
        List<AnomalyVo> result = service.scan(s);
        assertMatchedRule(result, AnomalyRule.WALLET_BALANCE_LOW, AnomalyLevel.MEDIUM);
    }

    @Test
    void scan_walletBalanceLow_noMatch_aboveThreshold() {
        KpiSnapshot s = baseSnapshot().walletBalance(new BigDecimal("500")).build();
        List<AnomalyVo> result = service.scan(s);
        assertRuleNotPresent(result, AnomalyRule.WALLET_BALANCE_LOW);
    }

    @Test
    void scan_tokenUsageSpike_matches_medium() {
        KpiSnapshot s = baseSnapshot().tokenUsage(200_000L).build();
        List<AnomalyVo> result = service.scan(s);
        assertMatchedRule(result, AnomalyRule.TOKEN_USAGE_SPIKE, AnomalyLevel.MEDIUM);
    }

    @Test
    void scan_tokenUsageSpike_noMatch_belowThreshold() {
        KpiSnapshot s = baseSnapshot().tokenUsage(50_000L).build();
        List<AnomalyVo> result = service.scan(s);
        assertRuleNotPresent(result, AnomalyRule.TOKEN_USAGE_SPIKE);
    }

    @Test
    void scan_revenueDrop_matches_medium_zeroRevenue() {
        KpiSnapshot s = baseSnapshot().totalRevenue(BigDecimal.ZERO).build();
        List<AnomalyVo> result = service.scan(s);
        assertMatchedRule(result, AnomalyRule.REVENUE_DROP, AnomalyLevel.MEDIUM);
    }

    @Test
    void scan_revenueDrop_noMatch_nonzeroRevenue() {
        KpiSnapshot s = baseSnapshot().totalRevenue(new BigDecimal("1000")).build();
        List<AnomalyVo> result = service.scan(s);
        assertRuleNotPresent(result, AnomalyRule.REVENUE_DROP);
    }

    @Test
    void scan_expenseExceedsRevenue_matches_high() {
        // expense=8000, revenue=5000：expense > revenue 但 < 100000 → 不会同时触发 VOUCHER_OVER_100K
        KpiSnapshot s = baseSnapshot()
                .totalRevenue(new BigDecimal("5000"))
                .totalExpense(new BigDecimal("8000"))
                .build();
        List<AnomalyVo> result = service.scan(s);
        assertMatchedRule(result, AnomalyRule.EXPENSE_EXCEEDS_REVENUE, AnomalyLevel.HIGH);
    }

    @Test
    void scan_expenseExceedsRevenue_noMatch_revenueDominant() {
        KpiSnapshot s = baseSnapshot()
                .totalRevenue(new BigDecimal("10000"))
                .totalExpense(new BigDecimal("3000"))
                .build();
        List<AnomalyVo> result = service.scan(s);
        assertRuleNotPresent(result, AnomalyRule.EXPENSE_EXCEEDS_REVENUE);
    }

    @Test
    void scan_highVoucherRejectionRate_alwaysFalse_noAnomaly() {
        // 该规则的 predicate 恒返回 false → 永远不命中，靠 LLM 软扫补强
        KpiSnapshot s = baseSnapshot()
                .totalRevenue(new BigDecimal("10000"))
                .totalExpense(new BigDecimal("3000"))
                .voucherCount(10L)
                .pendingVoucherCount(0L)
                .walletBalance(new BigDecimal("500"))
                .tokenUsage(10_000L)
                .build();
        when(softDetector.detect(any())).thenReturn(Collections.emptyList());

        List<AnomalyVo> result = service.scan(s);

        assertRuleNotPresent(result, AnomalyRule.HIGH_VOUCHER_REJECTION_RATE);
        // 应调用 LLM 软扫（无 HIGH 异常 + 该规则未命中 + 其他规则也都未命中）
        verify(softDetector, times(1)).detect(any());
    }

    @Test
    void scan_multipleHighValueFlows_matches_low() {
        KpiSnapshot s = baseSnapshot().voucherCount(60L).build();
        List<AnomalyVo> result = service.scan(s);
        assertMatchedRule(result, AnomalyRule.MULTIPLE_HIGH_VALUE_FLOWS, AnomalyLevel.LOW);
    }

    @Test
    void scan_multipleHighValueFlows_noMatch_belowThreshold() {
        KpiSnapshot s = baseSnapshot().voucherCount(10L).build();
        List<AnomalyVo> result = service.scan(s);
        assertRuleNotPresent(result, AnomalyRule.MULTIPLE_HIGH_VALUE_FLOWS);
    }

    @Test
    void scan_onlyOneRule_highLightByExpenseOverRevenue() {
        // 构造只触发 EXPENSE_EXCEEDS_REVENUE（HIGH）的快照
        KpiSnapshot s = baseSnapshot()
                .totalRevenue(new BigDecimal("5000"))
                .totalExpense(new BigDecimal("8000"))
                .voucherCount(10L)        // 不触发 MULTIPLE_HIGH_VALUE_FLOWS (>50)
                .pendingVoucherCount(0L)  // 不触发 PENDING_VOUCHER_OVER_7D
                .walletBalance(new BigDecimal("500"))  // 不触发 WALLET_BALANCE_LOW
                .tokenUsage(10_000L)      // 不触发 TOKEN_USAGE_SPIKE (>100k)
                .build();

        List<AnomalyVo> result = service.scan(s);

        assertEquals(1, result.size(), "应仅命中 EXPENSE_EXCEEDS_REVENUE 一条");
        assertEquals(AnomalyRule.EXPENSE_EXCEEDS_REVENUE.name(), result.get(0).getRuleCode());
        verifyNoInteractions(softDetector);
    }

    // ============================================================
    // 2. LLM 软扫（参数化 5 种情形：0/1/2/3/5）
    // ============================================================

    /**
     * 无硬规则命中 → 调用 LLM 软扫；返回 N 条 confidence=0.70 的异常 + 1 条 0.40 的噪声；
     * 全部 0.70 应通过 0.60 阈值过滤，0.40 应被丢弃。
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 5})
    void scan_noHardAnomaly_callsLlmAndFiltersByConfidence(int softCount) {
        // 构造一个不触发任何硬规则的 snapshot
        KpiSnapshot s = baseSnapshot()
                .totalRevenue(new BigDecimal("10000"))
                .totalExpense(new BigDecimal("3000"))
                .voucherCount(10L)
                .pendingVoucherCount(0L)
                .walletBalance(new BigDecimal("500"))
                .tokenUsage(10_000L)
                .build();

        // stub soft detector 返回 softCount 条 confidence=0.70 + 1 条 confidence=0.40 噪声
        List<AnomalyVo> llmReturns = new ArrayList<>();
        for (int i = 0; i < softCount; i++) {
            llmReturns.add(AnomalyVo.builder()
                    .level(AnomalyLevel.MEDIUM)
                    .ruleCode("LLM_SOFT_" + i)
                    .description("LLM 软异常 " + i)
                    .llmConfidence(new BigDecimal("0.70"))
                    .build());
        }
        llmReturns.add(AnomalyVo.builder()
                .level(AnomalyLevel.LOW)
                .ruleCode("LLM_NOISE")
                .description("LLM 噪声（应被过滤）")
                .llmConfidence(new BigDecimal("0.40"))
                .build());
        when(softDetector.detect(any())).thenReturn(llmReturns);

        List<AnomalyVo> result = service.scan(s);

        // 期望：softCount 条全过阈值 + 0 条噪声
        assertEquals(softCount, result.size(), "softCount=" + softCount + " 应通过 0.60 阈值");
        for (AnomalyVo vo : result) {
            assertTrue(vo.getRuleCode().startsWith("LLM_SOFT_"),
                    "过滤后只应剩 LLM_SOFT_*, 实际：" + vo.getRuleCode());
            assertNotNull(vo.getId(), "id 应由 mapper 回填");
        }
        verify(softDetector, times(1)).detect(any());
        verify(mapper, times(softCount)).insert(any(OpcInsightAnomaly.class));
    }

    // ============================================================
    // 3. HIGH 抑制 LLM 软扫
    // ============================================================

    @Test
    void scan_hardAnomalyExists_skipsLlm() {
        // 触发 EXPENSE_EXCEEDS_REVENUE（HIGH）
        KpiSnapshot s = baseSnapshot()
                .totalRevenue(new BigDecimal("5000"))
                .totalExpense(new BigDecimal("8000"))
                .voucherCount(10L)
                .pendingVoucherCount(0L)
                .walletBalance(new BigDecimal("500"))
                .tokenUsage(10_000L)
                .build();

        List<AnomalyVo> result = service.scan(s);

        assertEquals(1, result.size());
        assertEquals(AnomalyLevel.HIGH, result.get(0).getLevel());
        // 关键断言：LLM 软扫完全不被调用
        verifyNoInteractions(softDetector);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /** 默认 healthy snapshot（不触发任何硬规则） */
    private KpiSnapshot.KpiSnapshotBuilder baseSnapshot() {
        return KpiSnapshot.builder()
                .companyId(COMPANY_ID)
                .period(PERIOD)
                .totalRevenue(new BigDecimal("50000"))
                .totalExpense(new BigDecimal("10000"))
                .voucherCount(20L)
                .pendingVoucherCount(0L)
                .walletBalance(new BigDecimal("500"))
                .tokenUsage(10_000L)
                .companyActiveDays(30L)
                .partial(false);
    }

    private void assertMatchedRule(List<AnomalyVo> result, AnomalyRule rule, AnomalyLevel level) {
        AnomalyVo matched = result.stream()
                .filter(a -> rule.name().equals(a.getRuleCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(matched, "期望命中规则 " + rule.name() + "，实际：" + describeCodes(result));
        assertEquals(level, matched.getLevel());
        assertNotNull(matched.getId(), "id 应由 mapper 回填");
        // 落库验证：mapper.insert 至少一次收到 ruleCode == rule.name()
        ArgumentCaptor<OpcInsightAnomaly> captor = ArgumentCaptor.forClass(OpcInsightAnomaly.class);
        verify(mapper, atLeastOnce()).insert(captor.capture());
        boolean saved = captor.getAllValues().stream()
                .anyMatch(d -> rule.name().equals(d.getRuleCode()));
        assertTrue(saved, "期望 mapper.insert 收到规则 " + rule.name());
    }

    private void assertRuleNotPresent(List<AnomalyVo> result, AnomalyRule rule) {
        boolean present = result.stream()
                .anyMatch(a -> rule.name().equals(a.getRuleCode()));
        assertTrue(!present, "期望规则 " + rule.name() + " 不命中，实际：" + describeCodes(result));
    }

    private static String describeCodes(List<AnomalyVo> list) {
        return list.stream().map(AnomalyVo::getRuleCode).reduce((a, b) -> a + "," + b).orElse("(empty)");
    }
}
