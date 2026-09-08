package com.ruoyi.opc.insight.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.service.IAdviceService;
import com.ruoyi.opc.insight.service.IAnomalyService;
import com.ruoyi.opc.insight.service.IKpiService;
import com.ruoyi.opc.insight.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/opc/insight/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final IKpiService kpiService;
    private final IAnomalyService anomalyService;
    private final IAdviceService adviceService;

    @GetMapping
    public R<DashboardVo> dashboard() {
        Long companyId = resolveCompanyId();
        return R.ok(DashboardVo.builder()
                .kpi(kpiService.snapshot(companyId, null))
                .alerts(anomalyService.listOpen(companyId, 5))
                .advice(adviceService.listByCompany(companyId, 3))
                .build());
    }

    private Long resolveCompanyId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) throw new OpcException("请先登录");
        return userId;
    }
}
