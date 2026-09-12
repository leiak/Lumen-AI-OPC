package com.ruoyi.opc.erp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERP 库存每日快照（Task 7）
 *
 * <p>Quartz Job {@code OpcErpDailySnapshotJob} 在每日 23:55 为每个 SKU 生成一条：
 * <ul>
 *   <li>opening_stock = 前一天 closing_stock</li>
 *   <li>in_qty = 当日库存流水 change&gt;0 的合计</li>
 *   <li>out_qty = 当日库存流水 change&lt;0 的合计（取绝对值）</li>
 *   <li>closing_stock = SKU.stock 当前值（业务上即昨日期末）</li>
 * </ul>
 *
 * <p>供 {@link com.ruoyi.opc.erp.service.IOpcErpReportService} 日报/月报查询使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpDailySnapshot {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("snapshot_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate snapshotDate;
    @JsonProperty("sku_id")
    private Long skuId;
    @JsonProperty("opening_stock")
    private Integer openingStock;
    @JsonProperty("in_qty")
    private Integer inQty;
    @JsonProperty("out_qty")
    private Integer outQty;
    @JsonProperty("closing_stock")
    private Integer closingStock;
    @JsonProperty("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
