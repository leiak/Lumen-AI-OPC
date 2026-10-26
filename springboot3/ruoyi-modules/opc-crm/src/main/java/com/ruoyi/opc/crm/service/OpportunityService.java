package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.enums.OpportunityStage;

import java.util.List;

public interface OpportunityService {
    CrmOpportunity create(CrmOpportunity opportunity, Long operatorId);
    CrmOpportunity update(Long id, CrmOpportunity update, Long operatorId);

    /** 按 id 取商机，但**必须**属于 {@code ownerId}。 */
    CrmOpportunity getById(Long id, Long ownerId);

    CrmOpportunity changeStage(Long id, OpportunityStage target, String reason, Long operatorId);
    OpportunityScoreResponse score(Long id, Long operatorId);

    /** 按客户列商机，且该客户必须属于 {@code ownerId}。 */
    List<CrmOpportunity> listByCustomer(Long customerId, Long ownerId);

    /** 按阶段列商机，且只限 {@code ownerId} —— 阶段是全局枚举，不带 owner 就是全员数据。 */
    List<CrmOpportunity> listByStage(OpportunityStage stage, Long ownerId);

    /**
     * 列表入口：只列当前用户的商机。
     *
     * <p>{@code customerId} / {@code stage} 有值就按值筛，没有就列全部（本人全部）。
     * 三个条件都作用在 {@code ownerId} 之内。</p>
     */
    List<CrmOpportunity> list(Long ownerId, Long customerId, OpportunityStage stage);
}
