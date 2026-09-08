package com.ruoyi.opc.insight.enums;

/**
 * INSIGHT 异常等级（M4 Task 6）。
 *
 * <p>对应 {@code opc_insight_anomaly.level} 列（VARCHAR(8)，与 SQL schema 一致）。
 * 规则选择语义：
 * <ul>
 *   <li>{@link #HIGH} — 立即告警；存在 HIGH 时跳过 LLM 软扫，避免噪声覆盖硬规则。</li>
 *   <li>{@link #MEDIUM} — 需复核；触发 LLM 软扫以补强语义。</li>
 *   <li>{@link #LOW} — 仅记录趋势；不影响硬规则的 LLM 抑制逻辑。</li>
 * </ul>
 *
 * @author OAC
 */
public enum AnomalyLevel {

    HIGH,
    MEDIUM,
    LOW
}
