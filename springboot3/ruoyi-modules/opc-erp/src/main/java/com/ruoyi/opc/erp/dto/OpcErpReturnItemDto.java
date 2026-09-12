package com.ruoyi.opc.erp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 退货明细 DTO（batch_id + 退款金额 + 退货原因）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpReturnItemDto {
    private Long id;
    @JsonProperty("sku_id")
    private Long skuId;
    private Integer quantity;
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    @JsonProperty("batch_id")
    private Long batchId;
    private String reason;
}
