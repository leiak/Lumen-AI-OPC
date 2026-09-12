package com.ruoyi.opc.erp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ERP 采购明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpPurchaseItem {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("purchase_id")
    private Long purchaseId;
    @JsonProperty("sku_id")
    private Long skuId;
    private Integer quantity;
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    @JsonProperty("batch_no")
    private String batchNo;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("production_date")
    private LocalDate productionDate;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("expiry_date")
    private LocalDate expiryDate;
}
