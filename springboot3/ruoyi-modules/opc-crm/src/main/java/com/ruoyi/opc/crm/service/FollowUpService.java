package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmFollowUp;

import java.time.LocalDateTime;
import java.util.List;

public interface FollowUpService {

    CrmFollowUp create(CrmFollowUp followUp, Long operatorId);

    CrmFollowUp markCompleted(Long id, Long operatorId);

    /** 按 id 取跟进记录，但**必须**属于 {@code ownerId}。 */
    CrmFollowUp getById(Long id, Long ownerId);

    /** 按客户列跟进记录，且该客户必须属于 {@code ownerId}。 */
    List<CrmFollowUp> listByCustomer(Long customerId, Long ownerId);

    /** 列某个 owner 名下的跟进记录 —— ownerId 由服务端给（当前登录用户）。 */
    List<CrmFollowUp> listByOwner(Long ownerId);

    List<CrmFollowUp> listUpcoming(Long ownerId, LocalDateTime from);
}
