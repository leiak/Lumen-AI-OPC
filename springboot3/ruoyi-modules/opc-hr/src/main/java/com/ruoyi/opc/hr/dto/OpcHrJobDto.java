package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
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
