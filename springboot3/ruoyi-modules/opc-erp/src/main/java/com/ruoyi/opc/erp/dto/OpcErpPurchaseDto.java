package com.ruoyi.opc.erp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpPurchaseDto {
    private Long id;
    @JsonProperty("purchase_no")
    private String purchaseNo;
    @JsonProperty("supplier_id")
    private Long supplierId;
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
    private String status;
    @JsonProperty("operator_id")
    private Long operatorId;
    private String remark;
    private List<OpcErpPurchaseItemDto> items;
}
