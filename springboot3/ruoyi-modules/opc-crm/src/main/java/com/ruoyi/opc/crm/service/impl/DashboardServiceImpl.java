package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmFollowUp;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.DashboardFunnelResponse;
import com.ruoyi.opc.crm.enums.CustomerLevel;
import com.ruoyi.opc.crm.enums.OpportunityStage;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmFollowUpMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;
import com.ruoyi.opc.crm.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final CrmCustomerMapper customerMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CrmFollowUpMapper followUpMapper;

    @Override
    public DashboardFunnelResponse getFunnel(Long ownerId) {
        DashboardFunnelResponse.DashboardFunnelResponseBuilder builder = DashboardFunnelResponse.builder();

        Map<String, Long> funnel = new LinkedHashMap<>();
        for (OpportunityStage stage : OpportunityStage.values()) {
            funnel.put(stage.name(), (long) opportunityMapper.countByStage(ownerId, stage.name()));
        }
        builder.funnel(funnel);

        BigDecimal activeAmount = opportunityMapper.sumAmountByOwnerAndStages(ownerId,
            List.of(OpportunityStage.LEAD.name(),
                OpportunityStage.QUALIFIED.name(),
                OpportunityStage.PROPOSAL.name(),
                OpportunityStage.NEGOTIATION.name()));
        builder.activeAmount(activeAmount != null ? activeAmount : BigDecimal.ZERO);

        BigDecimal wonAmount = opportunityMapper.sumAmountByOwnerAndStages(ownerId,
            List.of(OpportunityStage.WON.name()));
        builder.wonAmount(wonAmount != null ? wonAmount : BigDecimal.ZERO);

        List<CrmOpportunity> top = opportunityMapper.selectTopByOwner(ownerId, 10);
        builder.topOpportunities(top);

        return builder.build();
    }

    @Override
    public Map<String, Long> getCustomerSummary(Long ownerId) {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) customerMapper.countByOwner(ownerId));
        for (CustomerLevel level : CustomerLevel.values()) {
            summary.put(level.name(), (long) customerMapper.countByOwnerAndLevel(ownerId, level.name()));
        }
        return summary;
    }

    @Override
    public List<CrmFollowUp> getUpcomingFollowUps(Long ownerId) {
        return followUpMapper.selectUpcoming(ownerId, LocalDateTime.now());
    }

    @Override
    public List<CrmFollowUp> getRecentFollowUps(Long ownerId, int limit) {
        List<CrmFollowUp> all = followUpMapper.selectByOwnerId(ownerId);
        if (all == null) return List.of();
        return all.stream()
            .sorted(Comparator.comparing(CrmFollowUp::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .toList();
    }
}
