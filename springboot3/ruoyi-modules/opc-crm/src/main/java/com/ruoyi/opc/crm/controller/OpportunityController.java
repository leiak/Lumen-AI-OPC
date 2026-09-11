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

    @GetMapping
    public R<List<CrmOpportunity>> list(@RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) String stage) {
        if (customerId != null) return R.ok(opportunityService.listByCustomer(customerId));
        if (stage != null) return R.ok(opportunityService.listByStage(OpportunityStage.valueOf(stage)));
        return R.ok(List.of());
    }

    @GetMapping("/{id}")
    public R<CrmOpportunity> getById(@PathVariable Long id) {
        return R.ok(opportunityService.getById(id));
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