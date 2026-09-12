package com.ruoyi.opc.erp.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.dto.OpcErpReportDto;
import com.ruoyi.opc.erp.service.IOpcErpReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * ERP 报表 Controller（Task 7）。
 *
 * <p>2 endpoints:
 * <ul>
 *   <li>GET /daily?companyId=&amp;date=YYYY-MM-DD  日报</li>
 *   <li>GET /monthly?companyId=&amp;year=YYYY&amp;month=MM  月报</li>
 * </ul>
 */
@RestController
@RequestMapping("/opc/erp/report")
@RequiredArgsConstructor
public class OpcErpReportController {

    private final IOpcErpReportService reportService;

    @GetMapping("/daily")
    public R<OpcErpReportDto.DailyReport> daily(
            @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(reportService.getDailyReport(companyId, date));
    }

    @GetMapping("/monthly")
    public R<OpcErpReportDto.MonthlyReport> monthly(@RequestParam Long companyId,
                                                    @RequestParam int year,
                                                    @RequestParam int month) {
        if (month < 1 || month > 12) {
            throw new ServiceException("月份必须 1-12");
        }
        return R.ok(reportService.getMonthlyReport(companyId, year, month));
    }
}
