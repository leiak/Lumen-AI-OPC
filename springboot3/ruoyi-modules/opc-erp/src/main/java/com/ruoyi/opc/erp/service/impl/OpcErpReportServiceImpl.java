package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpDailySnapshot;
import com.ruoyi.opc.erp.dto.OpcErpReportDto;
import com.ruoyi.opc.erp.mapper.OpcErpDailySnapshotMapper;
import com.ruoyi.opc.erp.service.IOpcErpReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERP 报表服务实现（Task 7）。
 *
 * <p>W72 Task 7 设计:
 * <ul>
 *   <li>日报: selectByCompanyAndDate → wrap 成 DailyReport(date, skuCount, totalClosingStock, details)</li>
 *   <li>月报: selectByCompanyAndDateRange [monthStart, monthEnd] → 按日聚合(sum in_qty, sum out_qty)
 *       → LinkedHashMap&lt;LocalDate, DailySnapshot&gt; 保持日期升序</li>
 *   <li>无快照时返回空明细,totalClosingStock=0</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcErpReportServiceImpl implements IOpcErpReportService {

    private final OpcErpDailySnapshotMapper snapshotMapper;

    @Override
    public OpcErpReportDto.DailyReport getDailyReport(Long companyId, LocalDate date) {
        if (companyId == null || date == null) {
            throw new ServiceException("companyId/date 不能为空");
        }

        List<OpcErpDailySnapshot> snapshots = snapshotMapper.selectByCompanyAndDate(companyId, date);

        List<OpcErpReportDto.DailySnapshot> details = new ArrayList<>(snapshots.size());
        int totalClosing = 0;
        for (OpcErpDailySnapshot snap : snapshots) {
            details.add(OpcErpReportDto.DailySnapshot.builder()
                    .date(snap.getSnapshotDate())
                    .skuId(snap.getSkuId())
                    .opening(snap.getOpeningStock())
                    .inQty(snap.getInQty())
                    .outQty(snap.getOutQty())
                    .closing(snap.getClosingStock())
                    .build());
            totalClosing += (snap.getClosingStock() == null ? 0 : snap.getClosingStock());
        }

        return OpcErpReportDto.DailyReport.builder()
                .date(date)
                .skuCount(details.size())
                .totalClosingStock(totalClosing)
                .details(details)
                .build();
    }

    @Override
    public OpcErpReportDto.MonthlyReport getMonthlyReport(Long companyId, int year, int month) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (month < 1 || month > 12) {
            throw new ServiceException("月份必须 1-12");
        }
        if (year < 1900 || year > 9999) {
            throw new ServiceException("年份非法: " + year);
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<OpcErpDailySnapshot> snapshots =
                snapshotMapper.selectByCompanyAndDateRange(companyId, start, end);

        // 按日聚合,保持日期升序
        Map<LocalDate, DailyAccumulator> acc = new LinkedHashMap<>();
        for (OpcErpDailySnapshot snap : snapshots) {
            LocalDate d = snap.getSnapshotDate();
            DailyAccumulator a = acc.computeIfAbsent(d, k -> new DailyAccumulator(k));
            a.inSum += (snap.getInQty() == null ? 0 : snap.getInQty());
            a.outSum += (snap.getOutQty() == null ? 0 : snap.getOutQty());
            a.closingSum += (snap.getClosingStock() == null ? 0 : snap.getClosingStock());
        }

        List<OpcErpReportDto.DailySnapshot> daily = new ArrayList<>(acc.size());
        for (DailyAccumulator a : acc.values()) {
            daily.add(OpcErpReportDto.DailySnapshot.builder()
                    .date(a.date)
                    .skuId(null)  // 月报聚合不指单 SKU
                    .opening(null)
                    .inQty(a.inSum)
                    .outQty(a.outSum)
                    .closing(a.closingSum)
                    .build());
        }

        log.debug("月报聚合 companyId={} year={} month={} days={}", companyId, year, month, daily.size());

        return OpcErpReportDto.MonthlyReport.builder()
                .year(year)
                .month(month)
                .daily(daily)
                .build();
    }

    /**
     * 月报按日聚合的临时累加器。
     */
    private static final class DailyAccumulator {
        final LocalDate date;
        int inSum = 0;
        int outSum = 0;
        int closingSum = 0;

        DailyAccumulator(LocalDate date) {
            this.date = date;
        }
    }
}
