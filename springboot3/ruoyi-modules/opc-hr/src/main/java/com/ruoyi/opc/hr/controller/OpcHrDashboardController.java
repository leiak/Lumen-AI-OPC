package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.dto.OpcHrDashboardDto;
import com.ruoyi.opc.hr.service.IOpcHrDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HR Dashboard Controller — 1 endpoint:
 * {@code GET /opc/hr/dashboard?companyId=&sinceDays=}
 */
@RestController
@RequestMapping("/opc/hr/dashboard")
@RequiredArgsConstructor
public class OpcHrDashboardController {

    private final IOpcHrDashboardService dashboardService;

    @GetMapping
    public R<OpcHrDashboardDto> get(@RequestParam Long companyId,
                                    @RequestParam(defaultValue = "30") int sinceDays) {
        return R.ok(dashboardService.getDashboard(companyId, sinceDays));
    }
}
