package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.dto.StageChangeRequest;
import com.ruoyi.opc.crm.enums.OpportunityStage;
import com.ruoyi.opc.crm.service.OpportunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 销售商机 Controller
 */
@RestController
@RequestMapping("/opc/crm/opportunity")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityService opportunityService;

    /**
     * 商机列表。
     *
     * <p>三种筛法(customerId / stage / 都不给)都在**当前用户**名下进行 ——
     * 以前「都不给」直接返回空数组, 看板因此永远是空的。</p>
     */
    @GetMapping
    public R<List<CrmOpportunity>> list(@RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) String stage) {
        OpportunityStage target = stage == null ? null : OpportunityStage.valueOf(stage);
        return R.ok(opportunityService.list(SecurityUtils.getUserId(), customerId, target));
    }

    @GetMapping("/{id}")
    public R<CrmOpportunity> getById(@PathVariable Long id) {
        return R.ok(opportunityService.getById(id, SecurityUtils.getUserId()));
    }

    @PostMapping
    public R<CrmOpportunity> create(@RequestBody CrmOpportunity opportunity) {
        return R.ok(opportunityService.create(opportunity, SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}")
    public R<CrmOpportunity> update(@PathVariable Long id, @RequestBody CrmOpportunity opportunity) {
        return R.ok(opportunityService.update(id, opportunity, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/stage")
    public R<CrmOpportunity> changeStage(@PathVariable Long id, @RequestBody StageChangeRequest req) {
        OpportunityStage target = OpportunityStage.valueOf(req.getStage());
        return R.ok(opportunityService.changeStage(id, target, req.getReason(), SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/score")
    public R<OpportunityScoreResponse> score(@PathVariable Long id) {
        return R.ok(opportunityService.score(id, SecurityUtils.getUserId()));
    }
}