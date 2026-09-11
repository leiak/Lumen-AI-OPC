package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OpcHrOfferDto {
    private Long id;
    @JsonProperty("application_id")
    private Long applicationId;
    private BigDecimal salary;
    @JsonProperty("start_date")
    private LocalDate startDate;
    @JsonProperty("expire_at")
    private LocalDateTime expireAt;
    private String status;
}
