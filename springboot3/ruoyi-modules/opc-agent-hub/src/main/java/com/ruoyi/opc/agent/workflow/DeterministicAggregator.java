package com.ruoyi.opc.agent.workflow;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 确定性聚合器 —— <b>不调 LLM</b>，只用工具返回里的真实数字拼一段可读的摘要。
 *
 * <p><b>为什么需要它</b>：工作流里的 LLM 节点是会失败的（超时、PromptGuard 403、provider 挂）。
 * 失败时整条 run 不该直接死掉 —— 已经取到的真实数据不该白取。
 * 这时候就用这里的确定性摘要顶上：数字全部来自工具返回值，<b>一个字都不多</b>。
 *
 * <p><b>口径</b>：
 * <ul>
 *   <li>数字一律用 {@link BigDecimal#toPlainString()}（不带科学计数法）；</li>
 *   <li>缺字段 → {@code 0}（不是 null、不是 "null"）；</li>
 *   <li>输出以 {@link #PREFIX} 开头 —— 和 insight / finance 现有的 fallback 口径一致，
 *       界面能认出「这段是没走 LLM 的」。</li>
 * </ul>
 *
 * @author OAC
 */
public final class DeterministicAggregator {

    /**
     * fallback 摘要前缀 —— 与 {@code DailyReportServiceImpl.FALLBACK_SUMMARY_PREFIX}、
     * {@code OpcFinanceTaxReportServiceImpl} 的口径一致。
     */
    public static final String PREFIX = "[自动聚合·未走 LLM]";

    private DeterministicAggregator() {
    }

    /**
     * 银行流水确定性摘要。
     *
     * <p>输出格式（固定，健康检查可按它断言）：
     * <pre>
     * [自动聚合·未走 LLM] 昨日无资金流水（2026-11-02）；2026-10-07~2026-11-01 共 6 笔，收 51892.00，支 32430.00。
     * [自动聚合·未走 LLM] 昨日 2 笔（2026-11-02），收 100.00，支 50.00；2026-10-07~2026-11-01 共 6 笔，收 51892.00，支 32430.00。
     * </pre>
     *
     * @param flowResult 工具 {@code query_bank_flow} 的返回值（已解析成 Map）
     * @param yesterday 「昨天」的日期（yyyy-MM-dd，北京时间口径）
     */
    public static String bankFlowSummary(Map<String, Object> flowResult, String yesterday) {
        Map<String, Object> flow = flowResult == null ? Map.of() : flowResult;
        StringBuilder sb = new StringBuilder(PREFIX).append(' ');
        int yesterdayCount = intOf(flow.get("yesterdayCount"));
        if (yesterdayCount > 0) {
            sb.append("昨日 ").append(yesterdayCount).append(" 笔")
                    .append(dateSuffix(yesterday))
                    .append("，收 ").append(decimalOf(flow.get("yesterdayIn")))
                    .append("，支 ").append(decimalOf(flow.get("yesterdayOut")));
        } else {
            sb.append("昨日无资金流水").append(dateSuffix(yesterday));
        }
        sb.append('；').append(range(flow))
                .append(" 共 ").append(intOf(flow.get("count"))).append(" 笔")
                .append("，收 ").append(decimalOf(flow.get("totalIn")))
                .append("，支 ").append(decimalOf(flow.get("totalOut")))
                .append('。');
        return sb.toString();
    }

    /**
     * 会计凭证确定性摘要 —— 结构与 {@link #bankFlowSummary} 一致，
     * 区别是「昨天没有流水」换成「昨天没有凭证」。
     *
     * @param voucherResult 工具 {@code query_voucher} 的返回值（已解析成 Map）
     * @param yesterday 「昨天」的日期（yyyy-MM-dd，北京时间口径）
     */
    public static String voucherSummary(Map<String, Object> voucherResult, String yesterday) {
        Map<String, Object> voucher = voucherResult == null ? Map.of() : voucherResult;
        StringBuilder sb = new StringBuilder(PREFIX).append(' ');
        int yesterdayCount = intOf(voucher.get("yesterdayCount"));
        if (yesterdayCount > 0) {
            sb.append("昨日 ").append(yesterdayCount).append(" 笔")
                    .append(dateSuffix(yesterday))
                    .append("，收 ").append(decimalOf(voucher.get("yesterdayIn")))
                    .append("，支 ").append(decimalOf(voucher.get("yesterdayOut")));
        } else {
            sb.append("昨日无凭证").append(dateSuffix(yesterday));
        }
        sb.append('；').append(range(voucher))
                .append(" 共 ").append(intOf(voucher.get("count"))).append(" 笔")
                .append("，收 ").append(decimalOf(voucher.get("totalIn")))
                .append("，支 ").append(decimalOf(voucher.get("totalOut")))
                .append('。');
        return sb.toString();
    }

    // ==================== 内部工具 ====================

    /** 区间描述：{@code fromDate~toDate}（缺字段写「未知」，不编日期）。 */
    private static String range(Map<String, Object> data) {
        return textOf(data.get("fromDate")) + "~" + textOf(data.get("toDate"));
    }

    /** 「昨天」那一句后面的日期括注 —— 没有日期就不加括号。 */
    private static String dateSuffix(String yesterday) {
        return (yesterday == null || yesterday.isBlank()) ? "" : "（" + yesterday + "）";
    }

    /** 数字 → 纯小数串（不带科学计数法）。缺字段 → {@code 0}。 */
    private static String decimalOf(Object value) {
        if (value == null) {
            return "0";
        }
        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString()).toPlainString();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return "0";
        }
        try {
            return new BigDecimal(text).toPlainString();
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    /** 计数 → int。缺字段 / 不是数字 → {@code 0}。 */
    private static int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 文本 → 原样；缺字段 → 「未知」（不编内容）。 */
    private static String textOf(Object value) {
        if (value == null) {
            return "未知";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "未知" : text;
    }

}
