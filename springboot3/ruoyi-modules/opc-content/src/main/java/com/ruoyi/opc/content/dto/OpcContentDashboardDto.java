package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard 聚合 DTO — 4 个统计卡 + 趋势 + 最近脚本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcContentDashboardDto {

    /** 今日生成脚本数 */
    @JsonProperty("today_generate")
    private Integer todayGenerate;

    /** 待发布 (publish.status=PENDING) */
    @JsonProperty("pending_publish")
    private Integer pendingPublish;

    /** 已发布 (publish.status=SUCCESS) */
    @JsonProperty("published")
    private Integer published;

    /** 失败率 0.00-100.00 (%) */
    @JsonProperty("failed_rate")
    private BigDecimal failedRate;

    /** 近 7 天每日生成数 (length=7, index 0=今天) */
    @JsonProperty("seven_day_trend")
    private List<Integer> sevenDayTrend;

    /** 最近 5 条脚本 */
    @JsonProperty("recent_scripts")
    private List<OpcContentScriptDto> recentScripts;
}