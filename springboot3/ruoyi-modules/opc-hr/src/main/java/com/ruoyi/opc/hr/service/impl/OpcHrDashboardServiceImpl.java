package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.dto.OpcHrDashboardDto;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrJobMapper;
import com.ruoyi.opc.hr.service.IOpcHrDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 聚合实现 — 4 步组合:
 * <ol>
 *   <li>{@code applicationMapper.countByStatusForDashboard} → funnel</li>
 *   <li>内存计算 conversion_rates</li>
 *   <li>{@code applicationMapper.avgHireDaysForAcceptedOffers} → avg_hire_days</li>
 *   <li>{@code jobMapper.countList} × 4 状态 → job_status_distribution</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcHrDashboardServiceImpl implements IOpcHrDashboardService {

    /** 漏斗固定顺序:5 段 application 状态 */
    private static final List<String> FUNNEL_STAGES =
            List.of("NEW", "SCREENING", "INTERVIEW", "OFFER", "HIRED");

    /** JD 4 段状态 */
    private static final List<String> JOB_STAGES =
            List.of("DRAFT", "OPEN", "PAUSED", "CLOSED");

    /** 默认时间窗口(天) */
    private static final int DEFAULT_SINCE_DAYS = 30;

    private final OpcHrApplicationMapper applicationMapper;
    private final OpcHrJobMapper jobMapper;

    @Override
    public OpcHrDashboardDto getDashboard(Long companyId, int sinceDays) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (sinceDays <= 0) {
            sinceDays = DEFAULT_SINCE_DAYS;
        }

        OpcHrDashboardDto dto = new OpcHrDashboardDto();

        // 1. funnel:5 段 application 状态计数(即使某段为 0 也要出现)
        Map<String, Integer> funnelMap = new LinkedHashMap<>();
        for (String stage : FUNNEL_STAGES) {
            funnelMap.put(stage, 0);
        }
        List<Map<String, Object>> statusCounts =
                applicationMapper.countByStatusForDashboard(companyId, sinceDays);
        if (statusCounts != null) {
            for (Map<String, Object> row : statusCounts) {
                Object rawStatus = row.get("status");
                if (!(rawStatus instanceof String status)) {
                    continue;
                }
                Object rawCount = row.get("count");
                if (rawCount == null) {
                    continue;
                }
                if (funnelMap.containsKey(status)) {
                    funnelMap.put(status, ((Number) rawCount).intValue());
                }
            }
        }
        dto.setFunnel(toFunnelList(funnelMap));

        // 2. conversion_rates:相邻两段比值,from=0 时返回 0.0(避免除零)
        Map<String, Double> conversion = new LinkedHashMap<>();
        conversion.put("NEW_TO_SCREENING",
                rate(funnelMap.get("NEW"), funnelMap.get("SCREENING")));
        conversion.put("SCREENING_TO_INTERVIEW",
                rate(funnelMap.get("SCREENING"), funnelMap.get("INTERVIEW")));
        conversion.put("INTERVIEW_TO_OFFER",
                rate(funnelMap.get("INTERVIEW"), funnelMap.get("OFFER")));
        conversion.put("OFFER_TO_HIRED",
                rate(funnelMap.get("OFFER"), funnelMap.get("HIRED")));
        dto.setConversionRates(conversion);

        // 3. avg_hire_days:null 表示无 ACCEPTED offer,直接透传(前端显示 "-")
        Double avgDays = applicationMapper.avgHireDaysForAcceptedOffers(companyId, sinceDays);
        dto.setAvgHireDays(avgDays);

        // 4. job_status_distribution:4 段计数
        Map<String, Integer> jobDist = new LinkedHashMap<>();
        for (String stage : JOB_STAGES) {
            jobDist.put(stage, jobMapper.countList(companyId, stage));
        }
        dto.setJobStatusDistribution(jobDist);

        log.debug("HR dashboard companyId={} sinceDays={} funnel={} conversion={} avgDays={} jobs={}",
                companyId, sinceDays, funnelMap, conversion, avgDays, jobDist);
        return dto;
    }

    /**
     * funnel LinkedHashMap → List[{status, count}](保序)
     */
    private static List<Map<String, Object>> toFunnelList(Map<String, Integer> funnelMap) {
        List<Map<String, Object>> list = new ArrayList<>(funnelMap.size());
        for (Map.Entry<String, Integer> e : funnelMap.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", e.getKey());
            row.put("count", e.getValue());
            list.add(row);
        }
        return list;
    }

    /**
     * 计算转化率:to / from,保留 4 位小数;from=0 时返回 0.0 避免除零。
     */
    private static double rate(int from, int to) {
        if (from == 0) {
            return 0.0;
        }
        return Math.round((double) to / from * 10000.0) / 10000.0;
    }
}
