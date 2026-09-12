package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.dto.OpcErpReportDto;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP 报表服务（Task 7）。
 *
 * <p>基于 {@code opc_erp_daily_snapshot} 表：
 * <ul>
 *   <li>日报：单日所有 SKU 快照的明细 + skuCount/totalClosingStock 汇总</li>
 *   <li>月报：当月所有快照按日聚合（每日 in/out/closing 之和）</li>
 * </ul>
 */
public interface IOpcErpReportService {

    /**
     * 日报：返回指定日期所有 SKU 快照 + 汇总。
     */
    OpcErpReportDto.DailyReport getDailyReport(Long companyId, LocalDate date);

    /**
     * 月报：返回指定年月的按日聚合列表。
     */
    OpcErpReportDto.MonthlyReport getMonthlyReport(Long companyId, int year, int month);
}
