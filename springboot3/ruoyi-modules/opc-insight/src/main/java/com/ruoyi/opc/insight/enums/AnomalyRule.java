package com.ruoyi.opc.insight.enums;

import com.ruoyi.opc.insight.vo.KpiSnapshot;

import java.math.BigDecimal;
import java.util.function.Predicate;

/**
 * INSIGHT 异常检测硬规则枚举（M4 Task 6）。
 *
 * <p>8 条硬编码规则按优先级排序：每条规则描述一个可在 {@link KpiSnapshot}
 * 单字段上判定的不变量。规则匹配后由 {@code AnomalyServiceImpl.scan}
 * 持久化到 {@code opc_insight_anomaly}。
 *
 * <p><b>规则语义</b>：
 * <ol>
 *   <li>{@link #VOUCHER_OVER_100K} — 单笔凭证合计超 ¥100k（HIGH）</li>
 *   <li>{@link #PENDING_VOUCHER_OVER_7D} — 仍存在待审凭证（HIGH）——
 *       简化版：snapshot 中 pendingVoucherCount>0 即视为异常；
 *       真正判断"待审超 7 天"需另查 oldest_pending_voucher.create_time，
 *       此版本不在硬规则范围内，留待 LLM 软扫补强。</li>
 *   <li>{@link #WALLET_BALANCE_LOW} — 钱包余额不足 ¥100（MEDIUM）</li>
 *   <li>{@link #TOKEN_USAGE_SPIKE} — 本月 Token 用量超 100k（MEDIUM）</li>
 *   <li>{@link #REVENUE_DROP} — 本月收入为 0（MEDIUM）</li>
 *   <li>{@link #EXPENSE_EXCEEDS_REVENUE} — 支出超过收入（HIGH）</li>
 *   <li>{@link #HIGH_VOUCHER_REJECTION_RATE} — 凭证退单率高（MEDIUM）——
 *       需要 voucher.rejected_count / voucher.total 比例，KpiSnapshot 单字段
 *       不足以判定，predicate 恒返回 false，由 LLM 软扫替代。</li>
 *   <li>{@link #MULTIPLE_HIGH_VALUE_FLOWS} — 多笔大额流水（LOW）——
 *       简化版：voucherCount>50 即视为"大量凭证=存在大额流水风险"。</li>
 * </ol>
 *
 * <p>{@link #PENDING_VOUCHER_OVER_7D} 和 {@link #HIGH_VOUCHER_REJECTION_RATE}
 * 两个规则属于"半成品"——predicate 仅是 partial 实现，真实场景需另查数据源。
 * 设计取舍：先把 8 条规则枚举骨架 + 测试就位；后续 Sub-task 7.x 通过扩展
 * KpiSnapshot 字段（如增加 oldestPendingAgeDays / rejectionRate）来闭环。
 *
 * @author OAC
 */
public enum AnomalyRule {

    VOUCHER_OVER_100K("VOUCHER_OVER_100K", AnomalyLevel.HIGH,
            "单笔凭证超 ¥100,000",
            s -> s.getTotalExpense() != null
                    && s.getTotalExpense().compareTo(new BigDecimal("100000")) > 0),

    PENDING_VOUCHER_OVER_7D("PENDING_VOUCHER_OVER_7D", AnomalyLevel.HIGH,
            "凭证待审超 7 天",
            s -> s.getPendingVoucherCount() != null && s.getPendingVoucherCount() > 0),

    WALLET_BALANCE_LOW("WALLET_BALANCE_LOW", AnomalyLevel.MEDIUM,
            "钱包余额不足 ¥100",
            s -> s.getWalletBalance() != null
                    && s.getWalletBalance().compareTo(new BigDecimal("100")) < 0),

    TOKEN_USAGE_SPIKE("TOKEN_USAGE_SPIKE", AnomalyLevel.MEDIUM,
            "Token 用量激增 (本月 > 100k)",
            s -> s.getTokenUsage() != null && s.getTokenUsage() > 100_000L),

    REVENUE_DROP("REVENUE_DROP", AnomalyLevel.MEDIUM,
            "本月收入为 0",
            s -> s.getTotalRevenue() != null
                    && s.getTotalRevenue().compareTo(BigDecimal.ZERO) == 0),

    EXPENSE_EXCEEDS_REVENUE("EXPENSE_EXCEEDS_REVENUE", AnomalyLevel.HIGH,
            "支出 > 收入 (亏损)",
            s -> s.getTotalExpense() != null && s.getTotalRevenue() != null
                    && s.getTotalExpense().compareTo(s.getTotalRevenue()) > 0),

    HIGH_VOUCHER_REJECTION_RATE("HIGH_VOUCHER_REJECTION_RATE", AnomalyLevel.MEDIUM,
            "凭证退单率高",
            s -> false),  // 需要 rejected_count / voucher_count 比例，超出 KpiSnapshot 单字段能力，留给 LLM 软扫

    MULTIPLE_HIGH_VALUE_FLOWS("MULTIPLE_HIGH_VALUE_FLOWS", AnomalyLevel.LOW,
            "多笔大额流水",
            s -> s.getVoucherCount() != null && s.getVoucherCount() > 50L);

    /** 规则编码（写入 {@code rule_code} 列） */
    private final String code;

    /** 异常等级（写入 {@code level} 列） */
    private final AnomalyLevel level;

    /** 中文描述（写入 {@code description} 列） */
    private final String description;

    /** 判定谓词（pure function，无副作用） */
    private final Predicate<KpiSnapshot> predicate;

    AnomalyRule(String code, AnomalyLevel level, String description, Predicate<KpiSnapshot> predicate) {
        this.code = code;
        this.level = level;
        this.description = description;
        this.predicate = predicate;
    }

    public String getCode() {
        return code;
    }

    public AnomalyLevel getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判定规则是否命中。
     *
     * @param snapshot KPI 快照
     * @return true = 命中；false = 不命中
     */
    public boolean match(KpiSnapshot snapshot) {
        return predicate.test(snapshot);
    }
}
