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
 * ERP 批次（FIFO 按 production_date ASC 扣减）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpBatch {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("sku_id")
    private Long skuId;
    @JsonProperty("batch_no")
    private String batchNo;
    private Integer quantity;
    private Integer remaining;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("production_date")
    private LocalDate productionDate;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("expiry_date")
    private LocalDate expiryDate;
    @JsonProperty("supplier_id")
    private Long supplierId;
    @JsonProperty("purchase_id")
    private Long purchaseId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("create_time")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}
