package com.ruoyi.opc.billing.service.impl;

import com.ruoyi.opc.billing.mapper.OpcBillingOrderMapper;
import com.ruoyi.opc.billing.service.IOpcBillingOrderService;
import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * {@link IOpcBillingOrderService} 实现（M4 INSIGHT Day 2 / Task 2）。
 *
 * <p>复用 {@link AggSupport} 做入参校验 + Map → BigDecimal/Long 归一化（W1.4.2 经验）。
 *
 * @author OAC
 */
@Service
@RequiredArgsConstructor
public class OpcBillingOrderServiceImpl implements IOpcBillingOrderService {

    private final OpcBillingOrderMapper orderMapper;

    @Override
    public OrdersAggVo aggregateByPeriod(Long companyId, String period) {
        AggSupport.validate(companyId, period);

        Map<String, Object> agg = AggSupport.orEmpty(orderMapper.aggregateByPeriod(companyId, period));

        return OrdersAggVo.builder()
                .companyId(companyId)
                .period(period)
                .count(AggSupport.count(agg.get("order_count")))
                .totalAmount(AggSupport.amount(agg.get("total_amount")))
                .paidAmount(AggSupport.amount(agg.get("paid_amount")))
                .refundedAmount(AggSupport.amount(agg.get("refunded_amount")))
                .build();
    }

    @Override
    public RechargeAggVo aggregateRechargeByPeriod(Long companyId, String period) {
        AggSupport.validate(companyId, period);

        List<Map<String, Object>> rows = orderMapper.aggregateRechargeChannelsByPeriod(companyId, period);

        // 累加总数 + 构建 channels 列表（一次 SQL 完成，避免重复查询）
        long count = 0L;
        java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;
        List<RechargeAggVo.ChannelAmount> channels = new java.util.ArrayList<>(rows == null ? 0 : rows.size());

        if (rows != null) {
            for (Map<String, Object> row : rows) {
                long cnt = AggSupport.count(row.get("cnt"));
                java.math.BigDecimal amt = AggSupport.amount(row.get("amount"));
                count += cnt;
                totalAmount = totalAmount.add(amt);
                channels.add(new RechargeAggVo.ChannelAmount(
                        row.get("channel") == null ? null : row.get("channel").toString(),
                        amt));
            }
        }

        return RechargeAggVo.builder()
                .companyId(companyId)
                .period(period)
                .count(count)
                .totalAmount(totalAmount.setScale(2, java.math.RoundingMode.HALF_UP))
                .channels(channels)
                .build();
    }
}
