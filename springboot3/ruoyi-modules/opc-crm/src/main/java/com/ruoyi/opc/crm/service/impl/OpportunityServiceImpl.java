package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.enums.OpportunityStage;
import com.ruoyi.opc.crm.gateway.AiCoreGateway;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;
import com.ruoyi.opc.crm.service.OpportunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityServiceImpl implements OpportunityService {

    private final CrmOpportunityMapper mapper;
    private final CrmCustomerMapper customerMapper;
    private final AiCoreGateway aiCoreGateway;

    @Override
    @Transactional
    public CrmOpportunity create(CrmOpportunity opportunity, Long operatorId) {
        if (opportunity.getCustomerId() == null) {
            throw new IllegalArgumentException("customerId required");
        }
        if (opportunity.getName() == null || opportunity.getName().isBlank()) {
            throw new IllegalArgumentException("Opportunity name cannot be empty (商机名称不能为空)");
        }
        if (opportunity.getStage() == null) {
            opportunity.setStage(OpportunityStage.LEAD.name());
        }
        if (opportunity.getScore() == null) opportunity.setScore(0);
        opportunity.setCreateBy(String.valueOf(operatorId));
        opportunity.setUpdateBy(String.valueOf(operatorId));
        if (opportunity.getDeleted() == null) opportunity.setDeleted(0);
        mapper.insert(opportunity);
        log.info("Created opportunity id={} name={} stage={}",
            opportunity.getId(), opportunity.getName(), opportunity.getStage());
        return opportunity;
    }

    @Override
    @Transactional
    public CrmOpportunity update(Long id, CrmOpportunity update, Long operatorId) {
        CrmOpportunity existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Opportunity not found: " + id);
        }
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getAmount() != null) existing.setAmount(update.getAmount());
        if (update.getExpectedClose() != null) existing.setExpectedClose(update.getExpectedClose());
        if (update.getOwnerId() != null) existing.setOwnerId(update.getOwnerId());
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public CrmOpportunity getById(Long id) {
        CrmOpportunity o = mapper.selectById(id);
        if (o == null) {
            throw new IllegalArgumentException("Opportunity not found: " + id);
        }
        return o;
    }

    @Override
    @Transactional
    public CrmOpportunity changeStage(Long id, OpportunityStage target, String reason, Long operatorId) {
        CrmOpportunity opp = mapper.selectById(id);
        if (opp == null) {
            throw new IllegalArgumentException("Opportunity not found: " + id);
        }
        OpportunityStage current = OpportunityStage.valueOf(opp.getStage());
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("Invalid stage transition: %s → %s (商机阶段不能从 %s 跳到 %s)",
                    current, target, current, target));
        }
        opp.setStage(target.name());
        if (target == OpportunityStage.LOST) {
            opp.setLostReason(reason);
        }
        opp.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(opp);
        log.info("Opportunity id={} stage changed {} → {} by operator {}",
            id, current, target, operatorId);
        return opp;
    }

    @Override
    @Transactional
    public OpportunityScoreResponse score(Long id, Long operatorId) {
        CrmOpportunity opp = mapper.selectById(id);
        if (opp == null) {
            throw new IllegalArgumentException("Opportunity not found: " + id);
        }
        CrmCustomer customer = customerMapper.selectById(opp.getCustomerId());
        OpportunityScoreResponse resp = aiCoreGateway.scoreOpportunity(opp, customer);
        if (resp != null) {
            opp.setScore(resp.getScore());
            opp.setScoreReason(resp.getReason());
            opp.setUpdateBy(String.valueOf(operatorId));
            mapper.updateById(opp);
        }
        return resp;
    }

    @Override
    public List<CrmOpportunity> listByCustomer(Long customerId) {
        return mapper.selectByCustomerId(customerId);
    }

    @Override
    public List<CrmOpportunity> listByStage(OpportunityStage stage) {
        return mapper.selectByStage(stage.name());
    }
}
