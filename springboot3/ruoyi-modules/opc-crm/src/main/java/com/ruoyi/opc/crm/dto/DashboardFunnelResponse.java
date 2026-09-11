package com.ruoyi.opc.crm.dto;

import com.ruoyi.opc.crm.domain.CrmOpportunity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardFunnelResponse {
    private Map<String, Long> funnel;
    private Map<String, Long> customersByLevel;
    private List<CrmOpportunity> topOpportunities;
    private BigDecimal activeAmount;
    private BigDecimal wonAmount;
}
