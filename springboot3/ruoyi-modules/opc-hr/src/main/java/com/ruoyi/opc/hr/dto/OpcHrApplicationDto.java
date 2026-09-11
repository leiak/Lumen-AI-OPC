package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrApplicationDto {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("job_id")
    private Long jobId;
    @JsonProperty("candidate_id")
    private Long candidateId;
    private String channel;
    private Integer score;
    @JsonProperty("score_reason")
    private String scoreReason;
    private String status;
}