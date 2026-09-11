package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OpcHrDashboardDto {
    /** 漏斗 5 段: NEW / SCREENING / INTERVIEW / OFFER / HIRED */
    @JsonProperty("funnel")
    private List<Map<String, Object>> funnel;
    /** 转化率:NEW→SCREENING 等 4 段百分比 */
    @JsonProperty("conversion_rates")
    private Map<String, Double> conversionRates;
    /** 平均招聘时长(天),仅统计 ACCEPTED offers */
    @JsonProperty("avg_hire_days")
    private Double avgHireDays;
    /** JD 状态分布:status → count */
    @JsonProperty("job_status_distribution")
    private Map<String, Integer> jobStatusDistribution;
}
