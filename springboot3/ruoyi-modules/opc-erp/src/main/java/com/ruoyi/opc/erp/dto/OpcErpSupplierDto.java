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
public class OpcErpSupplierDto {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    private String name;
    private String contact;
    private String phone;
    private String email;
    private String address;
    private String level;
    private String status;
}
