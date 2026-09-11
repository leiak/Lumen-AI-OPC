package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import java.util.List;

public interface CustomerService {
    CrmCustomer create(CrmCustomer customer, Long ownerId);
    CrmCustomer update(Long id, CrmCustomer customer, Long operatorId);
    void delete(Long id, Long operatorId);
    CrmCustomer getById(Long id);
    List<CrmCustomer> list(Long ownerId, String level, String source, String tag, String keyword);
    int countByOwner(Long ownerId);
}
