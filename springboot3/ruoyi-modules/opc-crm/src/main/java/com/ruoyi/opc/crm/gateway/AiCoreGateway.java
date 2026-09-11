package com.ruoyi.opc.crm.gateway;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;

/**
 * Stub gateway for AI scoring — real @FeignClient will be added in Task 13
 * (opc-ai-core OpenFeign client). For now tests mock this interface.
 */
public interface AiCoreGateway {
    OpportunityScoreResponse scoreOpportunity(CrmOpportunity opportunity, CrmCustomer customer);
}
