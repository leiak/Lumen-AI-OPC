package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmContract;

import java.util.List;

public interface ContractService {
    CrmContract create(CrmContract contract, Long operatorId);
    CrmContract update(Long id, CrmContract update, Long operatorId);
    CrmContract activate(Long id, Long operatorId);
    CrmContract expire(Long id, Long operatorId);
    CrmContract terminate(Long id, String reason, Long operatorId);
    CrmContract getById(Long id);
    List<CrmContract> listByCustomer(Long customerId);
}