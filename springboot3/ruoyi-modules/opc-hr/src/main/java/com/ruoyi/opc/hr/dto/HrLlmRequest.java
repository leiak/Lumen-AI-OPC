package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HrLlmRequest {
    private String promptName;
    @JsonProperty("job_id")
    private Long jobId;
    @JsonProperty("candidate_id")
    private Long candidateId;
    private String input;
}
