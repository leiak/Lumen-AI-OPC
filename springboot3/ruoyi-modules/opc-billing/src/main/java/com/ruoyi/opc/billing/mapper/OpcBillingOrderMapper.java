package com.ruoyi.opc.billing.mapper;

import com.ruoyi.opc.billing.domain.OpcBillingOrder;

import java.util.List;
import java.util.Map;

public interface OpcBillingOrderMapper {

    OpcBillingOrder selectById(Long id);

    OpcBillingOrder selectByOrderNo(String orderNo);

    List<OpcBillingOrder> selectByCompany(Long companyId, String payStatus, Integer limit);

    int insert(OpcBillingOrder record);

    int update(OpcBillingOrder record);

    int markPaid(String orderNo, String payMethod, String payTradeNo);

    /**
     * 订单月度聚合（M4 INSIGHT KPI）— 按 company_id + create_time 的年月聚合全部订单类型。
     * 返回 Map，key 见 SQL 别名。
     */
    Map<String, Object> aggregateByPeriod(Long companyId, String period);

    /**
     * 充值渠道分桶（M4 INSIGHT KPI）— order_type='RECHARGE' AND pay_status='PAID'，
     * 按 pay_method GROUP BY。返回 List<Map>，每个 Map 含 channel / amount。
     */
    List<Map<String, Object>> aggregateRechargeChannelsByPeriod(Long companyId, String period);

}
