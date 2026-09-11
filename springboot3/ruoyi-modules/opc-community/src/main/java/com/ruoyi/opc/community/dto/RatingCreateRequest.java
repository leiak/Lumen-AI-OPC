package com.ruoyi.opc.community.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class RatingCreateRequest implements Serializable {
    @NotNull(message = "moduleId 必填")
    private Long moduleId;

    @NotNull(message = "score 必填")
    @DecimalMin("1.0")
    @DecimalMax("5.0")
    private BigDecimal score;

    private String review;
}
