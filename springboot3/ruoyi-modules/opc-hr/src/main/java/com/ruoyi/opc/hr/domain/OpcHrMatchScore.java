package com.ruoyi.opc.hr.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrMatchScore {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("job_id")
    private Long jobId;
    @JsonProperty("candidate_id")
    private Long candidateId;
    private Integer score;
    private String reason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("computed_at")
    private LocalDateTime computedAt;
}
