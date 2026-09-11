package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmOrder;

import java.util.List;

public interface OrderService {
    CrmOrder create(CrmOrder order, Long operatorId);
    CrmOrder update(Long id, CrmOrder update, Long operatorId);
    CrmOrder pay(Long id, Long operatorId);
    CrmOrder ship(Long id, Long operatorId);
    CrmOrder complete(Long id, Long operatorId);
    CrmOrder cancel(Long id, String reason, Long operatorId);
    CrmOrder getById(Long id);
    List<CrmOrder> listByCustomer(Long customerId);
    List<CrmOrder> listByContract(Long contractId);
}
