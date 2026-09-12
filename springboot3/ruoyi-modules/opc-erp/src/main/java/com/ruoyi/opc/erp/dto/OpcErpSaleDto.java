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
public class OpcErpSaleDto {
    private Long id;
    @JsonProperty("sale_no")
    private String saleNo;
    @JsonProperty("customer_name")
    private String customerName;
    @JsonProperty("customer_phone")
    private String customerPhone;
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
    private String status;
    @JsonProperty("operator_id")
    private Long operatorId;
    private String remark;
    private List<OpcErpSaleItemDto> items;
}
