package com.ruoyi.opc.finance.vo;

import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 凭证区间查询结果（闭区间 {@code [fromDate, toDate]}）
 *
 * <p>区间语义与 {@link FlowRangeVo} 完全一致（fromDate 缺省 = 最早一条凭证的日期，
 * toDate 缺省 = 昨天/北京时间，闭区间），响应回写实际使用的区间。
 *
 * <p><b>口径</b>：凭证没有 amount 列 —— 收入 = {@code SUM(total_credit)}（贷方），
 * 支出 = {@code SUM(total_debit)}（借方）。这样凭证与流水是同一套 in/out 口径，
 * 下游对账不必分两套算法。
 *
 * <p><b>金额的 scale 不统一（下游请用 compareTo，不要用 equals）</b>：区间为空时
 * 各合计是 {@link java.math.BigDecimal#ZERO}，JSON 里渲染成 {@code 0}；
 * 区间非空时是库里 total_credit / total_debit 的 scale（2），
 * JSON 里渲染成 {@code 35400.00}。{@code BigDecimal.equals} 连 scale 一起比，
 * {@code 0} 和 {@code 0.00} 不相等；要比数值请用 {@code compareTo(...)}。</p>
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherRangeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 实际使用的起始日 yyyy-MM-dd（缺省时 = 该公司最早一张凭证的日期） */
    private String fromDate;

    /** 实际使用的截止日 yyyy-MM-dd（缺省时 = 昨天，北京时间） */
    private String toDate;

    /** 区间内凭证张数（不分状态 —— 含 DRAFT/REVIEW/REJECTED） */
    private int count;

    /** 收入合计 = SUM(total_credit)（贷方） */
    private BigDecimal totalIn;

    /** 支出合计 = SUM(total_debit)（借方） */
    private BigDecimal totalOut;

    /** 净额 = totalIn - totalOut */
    private BigDecimal net;

    /** 昨天（北京时间今天的前一天）那一天的凭证张数 */
    private int yesterdayCount;

    /** 昨天那天的贷方合计 */
    private BigDecimal yesterdayIn;

    /** 昨天那天的借方合计 */
    private BigDecimal yesterdayOut;

    /** 区间内的凭证明细（按 voucher_date 升序、同日按 id 升序） */
    private List<OpcFinanceVoucher> items;

}
