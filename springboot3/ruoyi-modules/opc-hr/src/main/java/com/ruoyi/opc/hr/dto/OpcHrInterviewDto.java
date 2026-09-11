package com.ruoyi.opc.hr.dto;

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
public class OpcHrInterviewDto {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("application_id")
    private Long applicationId;
    private Integer round;
    private String type;
    @JsonProperty("interviewer_id")
    private Long interviewerId;
    @JsonProperty("scheduled_at")
    private LocalDateTime scheduledAt;
    @JsonProperty("duration_min")
    private Integer durationMin;
    private String feedback;
    private String result;
}
