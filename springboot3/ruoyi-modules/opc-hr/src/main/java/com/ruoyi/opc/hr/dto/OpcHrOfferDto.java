package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrOfferDto {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("application_id")
    private Long applicationId;
    private BigDecimal salary;
    @JsonProperty("start_date")
    private LocalDate startDate;
    @JsonProperty("expire_at")
    private LocalDateTime expireAt;
    private String status;
}
