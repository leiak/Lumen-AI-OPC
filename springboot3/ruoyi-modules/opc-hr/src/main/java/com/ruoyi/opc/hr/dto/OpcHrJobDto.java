package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrJobDto {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    private String title;
    private String category;
    private String description;
    @JsonProperty("full_jd")
    private String fullJd;
    @JsonProperty("skills_json")
    private String skillsJson;
    @JsonProperty("salary_min")
    private BigDecimal salaryMin;
    @JsonProperty("salary_max")
    private BigDecimal salaryMax;
    private String location;
    private String status;
}
