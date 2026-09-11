package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.enums.OpportunityStage;

import java.util.List;

public interface OpportunityService {
    CrmOpportunity create(CrmOpportunity opportunity, Long operatorId);
    CrmOpportunity update(Long id, CrmOpportunity update, Long operatorId);
    CrmOpportunity getById(Long id);
    CrmOpportunity changeStage(Long id, OpportunityStage target, String reason, Long operatorId);
    OpportunityScoreResponse score(Long id, Long operatorId);
    List<CrmOpportunity> listByCustomer(Long customerId);
    List<CrmOpportunity> listByStage(OpportunityStage stage);
}
