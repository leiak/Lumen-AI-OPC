package com.ruoyi.opc.agent.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DeterministicAggregator} 单测 —— Task 3
 *
 * <p><b>它是干什么的</b>：工作流里的 LLM 节点是会失败的（超时、PromptGuard 403、provider 挂）。
 * 失败时整条 run 不该直接死掉 —— 已经取到的真实数据不该白取。
 * 这时候就用这里的确定性摘要顶上：数字全部来自工具返回值，<b>一个字都不多</b>。
 *
 * @author OAC
 */
@DisplayName("DeterministicAggregator 单测")
class DeterministicAggregatorTest {

    private static final String YESTERDAY = "2026-11-02";

    /** finance 端 {@code query_bank_flow} 的真实返回形状（金额是 scale 2）。 */
    private static Map<String, Object> flowWithoutYesterday() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", "opc_finance_bank_flow");
        m.put("companyId", 2);
        m.put("fromDate", "2026-10-07");
        m.put("toDate", "2026-11-01");
        m.put("count", 6);
        m.put("totalIn", new BigDecimal("51892.00"));
        m.put("totalOut", new BigDecimal("32430.00"));
        m.put("yesterdayCount", 0);
        m.put("yesterdayIn", new BigDecimal("0.00"));
        m.put("yesterdayOut", new BigDecimal("0.00"));
        return m;
    }

    @Test
    @DisplayName("昨天没有流水 → 明说没有，不编数字")
    void bankFlowSummary_statesNoFlowWhenYesterdayIsEmpty() {
        String out = DeterministicAggregator.bankFlowSummary(flowWithoutYesterday(), YESTERDAY);

        assertThat(out).contains("昨日无资金流水");
        assertThat(out).contains(YESTERDAY);
    }

    @Test
    @DisplayName("昨天有流水 → 说清几笔，收支多少")
    void bankFlowSummary_statesFlowWhenYesterdayHasRows() {
        Map<String, Object> flow = flowWithoutYesterday();
        flow.put("yesterdayCount", 2);
        flow.put("yesterdayIn", new BigDecimal("100.00"));
        flow.put("yesterdayOut", new BigDecimal("50.00"));

        String out = DeterministicAggregator.bankFlowSummary(flow, YESTERDAY);

        assertThat(out).contains("昨日 2 笔");
        assertThat(out).doesNotContain("无资金流水");
        assertThat(out).contains("100.00").contains("50.00");
    }

    @Test
    @DisplayName("摘要里的每个数字都来自输入 —— 不多、不少、不 null")
    void bankFlowSummary_containsOnlyRealNumbers() {
        String out = DeterministicAggregator.bankFlowSummary(flowWithoutYesterday(), YESTERDAY);

        assertThat(out).contains("6 笔");
        assertThat(out).contains("51892.00");
        assertThat(out).contains("32430.00");
        assertThat(out).doesNotContain("null");
        assertThat(out).doesNotContain("NaN");
        // 区间也是输入里给的
        assertThat(out).contains("2026-10-07").contains("2026-11-01");
    }

    @Test
    @DisplayName("输出以「[自动聚合·未走 LLM]」开头 —— 界面能认出这段没走 LLM")
    void bankFlowSummary_marksFallbackPrefix() {
        String out = DeterministicAggregator.bankFlowSummary(flowWithoutYesterday(), YESTERDAY);

        assertThat(out).startsWith("[自动聚合·未走 LLM]");
        assertThat(DeterministicAggregator.PREFIX).isEqualTo("[自动聚合·未走 LLM]");
    }

    @Test
    @DisplayName("缺字段 → 0（不是 null、不是「null」）")
    void bankFlowSummary_missingFields_useZero() {
        String out = DeterministicAggregator.bankFlowSummary(Map.of(), YESTERDAY);

        assertThat(out).startsWith("[自动聚合·未走 LLM]");
        assertThat(out).contains("共 0 笔");
        assertThat(out).contains("收 0");
        assertThat(out).contains("支 0");
        assertThat(out).doesNotContain("null");
    }

    @Test
    @DisplayName("金额用普通小数串，不用科学计数法")
    void bankFlowSummary_usesPlainStringForAmounts() {
        Map<String, Object> flow = flowWithoutYesterday();
        flow.put("totalIn", new BigDecimal("1E+3"));

        String out = DeterministicAggregator.bankFlowSummary(flow, YESTERDAY);

        assertThat(out).contains("收 1000");
        assertThat(out).doesNotContain("E+");
    }

    @Test
    @DisplayName("凭证摘要：昨天没凭证就说没凭证")
    void voucherSummary_statesNoVoucherWhenYesterdayIsEmpty() {
        Map<String, Object> voucher = flowWithoutYesterday();
        voucher.put("source", "opc_finance_voucher");

        String out = DeterministicAggregator.voucherSummary(voucher, YESTERDAY);

        assertThat(out).startsWith("[自动聚合·未走 LLM]");
        assertThat(out).contains("昨日无凭证");
        assertThat(out).contains("51892.00");
    }

    @Test
    @DisplayName("凭证摘要：昨天有凭证就说几笔")
    void voucherSummary_statesVoucherWhenYesterdayHasRows() {
        Map<String, Object> voucher = flowWithoutYesterday();
        voucher.put("yesterdayCount", 3);
        voucher.put("yesterdayIn", new BigDecimal("300.00"));
        voucher.put("yesterdayOut", new BigDecimal("200.00"));

        String out = DeterministicAggregator.voucherSummary(voucher, YESTERDAY);

        assertThat(out).contains("昨日 3 笔");
        assertThat(out).doesNotContain("昨日无凭证");
    }

}
