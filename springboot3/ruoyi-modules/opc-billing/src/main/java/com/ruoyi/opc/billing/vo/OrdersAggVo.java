package com.ruoyi.opc.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单月度聚合结果（供 opc-insight 经 Feign 拉取）
 *
 * <p>字段来源：{@code opc_billing_order} 按 {@code company_id + create_time} 的
 * 年月聚合，包含全部订单类型（RECHARGE/SUBSCRIPTION/CONSUME/REFUND）。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdersAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 所属期 YYYY-MM */
    private String period;

    /** 订单总数 */
    private Long count;

    /** 订单金额合计（含未付款） */
    private BigDecimal totalAmount;

    /** 已支付金额合计（pay_status='PAID'） */
    private BigDecimal paidAmount;

    /** 已退款金额合计 */
    private BigDecimal refundedAmount;
}
