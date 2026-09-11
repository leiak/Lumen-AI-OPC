package com.ruoyi.opc.crm.gateway;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.Map;

@FeignClient(name = "opc-ai-core", url = "${opc.crm.ai-core.base-url}")
public interface AiCoreGateway {

    @PostMapping("/opc/ai-core/opportunity/score")
    OpportunityScoreResponse scoreOpportunity(@RequestBody Map<String, Object> payload);

    /** Helper for callers that have the entities */
    default OpportunityScoreResponse scoreOpportunity(CrmOpportunity opp, CrmCustomer cust) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("opportunityId", opp.getId());
        payload.put("opportunityName", opp.getName());
        payload.put("amount", opp.getAmount());
        payload.put("stage", opp.getStage());
        payload.put("customerId", cust != null ? cust.getId() : null);
        payload.put("customerName", cust != null ? cust.getName() : null);
        return scoreOpportunity(payload);
    }
}