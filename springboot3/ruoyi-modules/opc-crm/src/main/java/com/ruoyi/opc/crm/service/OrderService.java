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

    /** 按 id 取订单，但**必须**挂在 {@code ownerId} 名下的客户下。 */
    CrmOrder getById(Long id, Long ownerId);

    /** 按客户列订单，且该客户必须属于 {@code ownerId}。 */
    List<CrmOrder> listByCustomer(Long customerId, Long ownerId);

    /** 按合同列订单，且**合同本身**必须属于 {@code ownerId}。 */
    List<CrmOrder> listByContract(Long contractId, Long ownerId);
}
