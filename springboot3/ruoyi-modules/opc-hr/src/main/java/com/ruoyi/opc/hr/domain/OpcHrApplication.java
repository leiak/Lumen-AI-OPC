package com.ruoyi.opc.hr.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrApplication {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("job_id")
    private Long jobId;
    @JsonProperty("candidate_id")
    private Long candidateId;
    private String channel;

    /** LLM 评分 0-100;create 时默认 0(避免 NOT NULL DEFAULT 0 写入问题) */
    private Integer score;
    @JsonProperty("score_reason")
    private String scoreReason;

    private String status;
    @JsonProperty("current_stage")
    private String currentStage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("applied_at")
    private Date appliedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("update_time")
    private Date updateTime;
}
