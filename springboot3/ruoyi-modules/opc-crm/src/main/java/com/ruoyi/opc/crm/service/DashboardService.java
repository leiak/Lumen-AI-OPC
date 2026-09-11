package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmFollowUp;
import com.ruoyi.opc.crm.dto.DashboardFunnelResponse;

import java.util.List;
import java.util.Map;

public interface DashboardService {
    DashboardFunnelResponse getFunnel(Long ownerId);
    Map<String, Long> getCustomerSummary(Long ownerId);
    List<CrmFollowUp> getUpcomingFollowUps(Long ownerId);
    List<CrmFollowUp> getRecentFollowUps(Long ownerId, int limit);
}
