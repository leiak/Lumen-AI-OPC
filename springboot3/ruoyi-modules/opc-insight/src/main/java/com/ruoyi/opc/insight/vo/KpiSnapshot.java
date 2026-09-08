package com.ruoyi.opc.insight.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * INSIGHT KPI 快照（M4 Task 5）。
 *
 * <p>聚合 6 个数据源（voucher / flow / tax-report / token-usage / wallet / profile）
 * 为单一不可变快照，供 dashboard / 异常检测 / 日报 / 建议 共用。
 *
 * <p>{@link #partial} = true 表示至少一个数据源降级 —— 调用方应展示「数据不完整」徽标，
 * 不可用此快照触发自动决策（详见 spec §1.1）。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公司 ID */
    private Long companyId;

    /** 所属期 YYYY-MM */
    private String period;

    /** 收入合计（贷方合计，来自凭证聚合） */
    private BigDecimal totalRevenue;

    /** 支出合计（借方合计，来自凭证聚合） */
    private BigDecimal totalExpense;

    /** 凭证总张数 */
    private Long voucherCount;

    /** 待审凭证张数 */
    private Long pendingVoucherCount;

    /** 钱包余额（公司级合计） */
    private BigDecimal walletBalance;

    /** 本期 token 消耗 */
    private Long tokenUsage;

    /** 公司活跃天数（自 createTime 起到今天） */
    private Long companyActiveDays;

    /** true = 至少一个数据源降级，调用方需提示「数据不完整」 */
    private boolean partial;

}
