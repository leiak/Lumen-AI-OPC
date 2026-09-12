package com.ruoyi.opc.erp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ERP 商品 SKU（动态笛卡尔积规格）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpProductSku {
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
    /** 乐观锁字段: service 层做手动 WHERE id=? AND version=? UPDATE  */
    private Long version;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("create_time")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}
