package com.ruoyi.opc.hr.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrJob {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("created_by")
    private Long createdBy;

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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("publish_at")
    private Date publishAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("close_at")
    private Date closeAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("create_time")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("update_time")
    private Date updateTime;
}
