package com.ruoyi.opc.erp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpProductSkuDto {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("product_id")
    private Long productId;
    @JsonProperty("sku_code")
    private String skuCode;
    @JsonProperty("spec_json")
    private String specJson;
    private BigDecimal price;
    private BigDecimal cost;
    private Integer stock;
    private Integer threshold;
    private String status;
    /** 计算字段: stock < threshold */
    @JsonProperty("low_stock")
    private Boolean lowStock;
}
