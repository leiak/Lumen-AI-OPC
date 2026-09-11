package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmFollowUp;
import com.ruoyi.opc.crm.dto.DashboardFunnelResponse;
import com.ruoyi.opc.crm.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * CRM 仪表盘 Controller
 */
@RestController
@RequestMapping("/opc/crm/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public R<DashboardFunnelResponse> funnel() {
        return R.ok(dashboardService.getFunnel(SecurityUtils.getUserId()));
    }

    @GetMapping("/customers")
    public R<Map<String, Long>> customers() {
        return R.ok(dashboardService.getCustomerSummary(SecurityUtils.getUserId()));
    }

    @GetMapping("/follow-ups/upcoming")
    public R<List<CrmFollowUp>> upcoming() {
        return R.ok(dashboardService.getUpcomingFollowUps(SecurityUtils.getUserId()));
    }

    @GetMapping("/follow-ups/recent")
    public R<List<CrmFollowUp>> recent(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(dashboardService.getRecentFollowUps(SecurityUtils.getUserId(), limit));
    }
}