package com.ruoyi.opc.erp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * ERP 报表 DTO（静态内部类 + 顶层包装）
 */
public class OpcErpReportDto {

    /**
     * 日库存快照
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySnapshot {
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        private LocalDate date;
        @JsonProperty("sku_id")
        private Long skuId;
        /** 期初 */
        private Integer opening;
        @JsonProperty("in_qty")
        private Integer inQty;
        @JsonProperty("out_qty")
        private Integer outQty;
        /** 期末 */
        private Integer closing;
    }

    /**
     * 月度统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyReport {
        private Integer year;
        private Integer month;
        @JsonProperty("daily")
        private java.util.List<DailySnapshot> daily;
    }

    /**
     * 顶层报表响应：低库存计数 + 月度数组
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportResponse {
        @JsonProperty("low_stock_count")
        private Integer lowStockCount;
        @JsonProperty("monthly")
        private java.util.List<MonthlyReport> monthly;
    }
}
