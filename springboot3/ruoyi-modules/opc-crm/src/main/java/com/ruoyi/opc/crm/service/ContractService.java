package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmContract;

import java.util.List;

public interface ContractService {
    CrmContract create(CrmContract contract, Long operatorId);
    CrmContract update(Long id, CrmContract update, Long operatorId);
    CrmContract activate(Long id, Long operatorId);
    CrmContract expire(Long id, Long operatorId);
    CrmContract terminate(Long id, String reason, Long operatorId);

    /** 按 id 取合同，但**必须**挂在 {@code ownerId} 名下的客户下。 */
    CrmContract getById(Long id, Long ownerId);

    /** 按客户列合同，且该客户必须属于 {@code ownerId}。 */
    List<CrmContract> listByCustomer(Long customerId, Long ownerId);
}