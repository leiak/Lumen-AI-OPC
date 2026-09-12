package com.ruoyi.opc.erp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpProductDto {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("sku_root")
    private String skuRoot;
    private String name;
    private String category;
    private String brand;
    private String unit;
    private String description;
    @JsonProperty("spec_attrs")
    private String specAttrs;
    private String status;
}
