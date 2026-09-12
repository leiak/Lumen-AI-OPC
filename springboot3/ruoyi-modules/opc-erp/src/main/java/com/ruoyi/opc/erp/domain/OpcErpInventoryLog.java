package com.ruoyi.opc.erp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ERP 库存流水
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpInventoryLog {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("sku_id")
    private Long skuId;
    @JsonProperty("batch_id")
    private Long batchId;
    /** 变化数量(正入库/负出库) */
    private Integer change;
    private String type;
    @JsonProperty("ref_type")
    private String refType;
    @JsonProperty("ref_id")
    private Long refId;
    private String remark;
    @JsonProperty("created_by")
    private Long createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("create_time")
    private LocalDateTime createTime;
}
