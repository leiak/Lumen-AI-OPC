package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmFollowUp;

import java.time.LocalDateTime;
import java.util.List;

public interface FollowUpService {

    CrmFollowUp create(CrmFollowUp followUp, Long operatorId);

    CrmFollowUp markCompleted(Long id, Long operatorId);

    CrmFollowUp getById(Long id);

    List<CrmFollowUp> listByCustomer(Long customerId);

    List<CrmFollowUp> listByOwner(Long ownerId);

    List<CrmFollowUp> listUpcoming(Long ownerId, LocalDateTime from);
}
