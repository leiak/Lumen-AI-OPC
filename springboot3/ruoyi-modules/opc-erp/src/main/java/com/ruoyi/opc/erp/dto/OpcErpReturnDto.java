package com.ruoyi.opc.erp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退货单 DTO（含 items 与单据反向引用 sale/purchase）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpReturnDto {
    private Long id;
    @JsonProperty("return_no")
    private String returnNo;
    @JsonProperty("return_type")
    private String returnType;
    @JsonProperty("ref_id")
    private Long refId;
    @JsonProperty("refund_amount")
    private BigDecimal refundAmount;
    private String status;
    @JsonProperty("operator_id")
    private Long operatorId;
    private String reason;
    private String remark;
    /** 退货明细映射: SALES_RETURN 来自 sale_items / SUPPLIER_RETURN 来自 purchase_items */
    private List<OpcErpReturnItemDto> items;
}
