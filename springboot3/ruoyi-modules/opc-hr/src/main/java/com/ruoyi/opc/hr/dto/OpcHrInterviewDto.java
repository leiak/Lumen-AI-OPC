package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
public class OpcHrInterviewDto {
    private Long id;
    @JsonProperty("application_id")
    private Long applicationId;
    private Integer round;
    private String type;
    @JsonProperty("interviewer_id")
    private Long interviewerId;
    @JsonProperty("scheduled_at")
    private Date scheduledAt;
    @JsonProperty("duration_min")
    private Integer durationMin;
    private String feedback;
    private String result;
}
