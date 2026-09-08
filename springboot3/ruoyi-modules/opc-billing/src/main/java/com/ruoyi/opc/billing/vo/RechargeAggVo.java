package com.ruoyi.opc.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 充值月度聚合结果（供 opc-insight 经 Feign 拉取）
 *
 * <p>字段来源：{@code opc_billing_order} 中 {@code order_type='RECHARGE' AND pay_status='PAID'}
 * 的订单按 {@code company_id + create_time} 的年月聚合。
 *
 * <p>渠道分桶：{@link #channels} 按 {@code pay_method} 分组（ALIPAY / WECHAT / WALLET 等），
 * 供 INSIGHT dashboard 展示「各渠道充值占比」。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RechargeAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 所属期 YYYY-MM */
    private String period;

    /** 已支付充值订单总数（pay_status='PAID'） */
    private Long count;

    /** 充值金额合计（scale=2） */
    private BigDecimal totalAmount;

    /** 各渠道充值分桶（按 pay_method 分组） */
    private List<ChannelAmount> channels;

    /**
     * 单个渠道的充值金额（{@code pay_method} → amount）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelAmount implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 渠道编码：ALIPAY / WECHAT / WALLET 等 */
        private String channel;

        /** 该渠道累计充值金额（scale=2） */
        private BigDecimal amount;
    }
}
