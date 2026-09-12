package com.ruoyi.opc.erp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ERP 销售明细（batch_id FIFO 指派）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpSaleItem {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("sale_id")
    private Long saleId;
    @JsonProperty("sku_id")
    private Long skuId;
    private Integer quantity;
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    @JsonProperty("batch_id")
    private Long batchId;
}
