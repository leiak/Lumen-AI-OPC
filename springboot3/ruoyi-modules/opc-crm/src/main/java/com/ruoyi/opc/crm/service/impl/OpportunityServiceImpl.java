package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.enums.OpportunityStage;
import com.ruoyi.opc.crm.gateway.AiCoreGateway;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;
import com.ruoyi.opc.crm.service.CrmOwnership;
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
        // 商机挂在别人客户下 = 越权写入
        CrmOwnership.requireCustomer(customerMapper, opportunity.getCustomerId(), operatorId);
        if (opportunity.getStage() == null) {
            opportunity.setStage(OpportunityStage.LEAD.name());
        }
        if (opportunity.getScore() == null) opportunity.setScore(0);
        // 归属由服务端定 —— 客户端给的 ownerId 不作数
        // (否则可以把商机"挂"到别人名下, 或者把自己摘出去躲审计)
        opportunity.setOwnerId(operatorId);
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
        CrmOpportunity existing = mapper.selectByIdAndOwner(id, operatorId);
        if (existing == null) {
            throw CrmOwnership.denied("商机");
        }
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getAmount() != null) existing.setAmount(update.getAmount());
        if (update.getExpectedClose() != null) existing.setExpectedClose(update.getExpectedClose());
        // 归属**不能**由客户端改 —— 原来这行让 request 里的 ownerId 直接覆盖,
        // 等于可以把自己的商机转给别人、或把别人的商机抢过来。
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public CrmOpportunity getById(Long id, Long ownerId) {
        CrmOpportunity o = mapper.selectByIdAndOwner(id, ownerId);
        if (o == null) {
            throw CrmOwnership.denied("商机");
        }
        return o;
    }

    @Override
    @Transactional
    public CrmOpportunity changeStage(Long id, OpportunityStage target, String reason, Long operatorId) {
        CrmOpportunity opp = mapper.selectByIdAndOwner(id, operatorId);
        if (opp == null) {
            throw CrmOwnership.denied("商机");
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
        CrmOpportunity opp = mapper.selectByIdAndOwner(id, operatorId);
        if (opp == null) {
            throw CrmOwnership.denied("商机");
        }
        // 客户也要是本人的 —— 否则 AI 打分会把别人的客户资料带出去
        CrmCustomer customer = customerMapper.selectByIdAndOwner(opp.getCustomerId(), operatorId);
        if (customer == null) {
            throw CrmOwnership.denied("客户");
        }
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
    public List<CrmOpportunity> listByCustomer(Long customerId, Long ownerId) {
        return mapper.selectByCustomerIdAndOwner(customerId, ownerId);
    }

    @Override
    public List<CrmOpportunity> listByStage(OpportunityStage stage, Long ownerId) {
        return mapper.selectByStageAndOwner(stage.name(), ownerId);
    }

    @Override
    public List<CrmOpportunity> list(Long ownerId, Long customerId, OpportunityStage stage) {
        // ownerId 一定会带上, 所以 selectList 里的 owner 过滤永远生效 ——
        // 三个条件(customerId / stage / owner)是在同一个人名下筛, 不是三个独立维度。
        return mapper.selectList(ownerId, stage == null ? null : stage.name(), customerId);
    }
}
