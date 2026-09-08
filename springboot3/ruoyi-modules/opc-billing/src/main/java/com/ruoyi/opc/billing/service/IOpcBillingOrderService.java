package com.ruoyi.opc.billing.service;

import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;

/**
 * 计费订单 Service（M4 INSIGHT Day 2 / Task 2：含两个聚合方法）。
 *
 * <p>订单 CRUD / 列表 / 详情仍由 {@link com.ruoyi.opc.billing.mapper.OpcBillingOrderMapper}
 * 在 Controller 直调（保持原 W2 行为，避免改动过多）。本 service 只承载 INSIGHT Feign
 * 拉取用的两个聚合端点：
 * <ul>
 *   <li>{@link #aggregateByPeriod} — 订单月度聚合（含全部订单类型）</li>
 *   <li>{@link #aggregateRechargeByPeriod} — 充值月度聚合（含渠道分桶）</li>
 * </ul>
 *
 * @author OAC
 */
public interface IOpcBillingOrderService {

    /**
     * 订单月度聚合（M4 INSIGHT KPI）。
     *
     * @param companyId 公司 ID
     * @param period    期间 YYYY-MM（如 {@code "2026-09"}）
     * @return count / totalAmount / paidAmount / refundedAmount
     * @throws com.ruoyi.opc.common.exception.OpcException period 格式错
     */
    OrdersAggVo aggregateByPeriod(Long companyId, String period);

    /**
     * 充值月度聚合（M4 INSIGHT KPI，含渠道分桶）。
     *
     * <p>仅统计 {@code order_type='RECHARGE' AND pay_status='PAID'} 的订单。
     *
     * @param companyId 公司 ID
     * @param period    期间 YYYY-MM
     * @return count / totalAmount / channels（按 pay_method 分组）
     * @throws com.ruoyi.opc.common.exception.OpcException period 格式错
     */
    RechargeAggVo aggregateRechargeByPeriod(Long companyId, String period);
}
