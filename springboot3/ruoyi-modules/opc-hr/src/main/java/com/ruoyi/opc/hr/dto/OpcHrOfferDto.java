package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class OpcHrOfferDto {
    private Long id;
    @JsonProperty("application_id")
    private Long applicationId;
    private BigDecimal salary;
    @JsonProperty("start_date")
    private Date startDate;
    @JsonProperty("expire_at")
    private Date expireAt;
    private String status;
}
