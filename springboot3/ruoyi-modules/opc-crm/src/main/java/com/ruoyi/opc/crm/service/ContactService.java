package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmContact;

import java.util.List;

public interface ContactService {

    CrmContact create(CrmContact contact, Long operatorId);

    CrmContact update(Long id, CrmContact contact, Long operatorId);

    void delete(Long id, Long operatorId);

    CrmContact getById(Long id);

    List<CrmContact> listByCustomer(Long customerId);

    void setPrimary(Long customerId, Long contactId);
}
