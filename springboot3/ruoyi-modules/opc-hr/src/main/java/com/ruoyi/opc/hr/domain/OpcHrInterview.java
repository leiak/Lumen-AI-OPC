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
public class OpcHrInterview {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("application_id")
    private Long applicationId;
    private Integer round;
    private String type;
    @JsonProperty("interviewer_id")
    private Long interviewerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("scheduled_at")
    private LocalDateTime scheduledAt;

    @JsonProperty("duration_min")
    private Integer durationMin;
    private String feedback;
    private String result;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("create_time")
    private LocalDateTime createTime;
}
