package com.ruoyi.opc.erp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ERP 商品（SKU 规格 root）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpProduct {
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
    /** 规格属性 JSON: [{"name":"颜色","values":["黑","白"]}] */
    @JsonProperty("spec_attrs")
    private String specAttrs;
    private String status;
    @JsonProperty("created_by")
    private Long createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("create_time")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}
