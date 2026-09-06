package com.ruoyi.opc.billing.mapper;

import com.ruoyi.opc.billing.domain.OpcBillingOrder;

import java.util.List;

public interface OpcBillingOrderMapper {

    OpcBillingOrder selectById(Long id);

    OpcBillingOrder selectByOrderNo(String orderNo);

    List<OpcBillingOrder> selectByCompany(Long companyId, String payStatus, Integer limit);

    int insert(OpcBillingOrder record);

    int update(OpcBillingOrder record);

    int markPaid(String orderNo, String payMethod, String payTradeNo);

}
