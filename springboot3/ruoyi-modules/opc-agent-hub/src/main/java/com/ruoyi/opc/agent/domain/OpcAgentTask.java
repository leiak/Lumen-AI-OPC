package com.ruoyi.opc.agent.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Agent 任务 opc_agent_task
 *
 * @author OAC
 */
@Data
public class OpcAgentTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String taskCode;
    private Long companyId;
    private Long instanceId;
    private Long definitionId;
    private String taskType;
    private String sessionId;
    private String input;
    private String output;
    private String toolCalls;
    private String steps;
    private String status;
    private String errorMessage;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Integer tokenTotal;
    private BigDecimal cost;
    private Integer reviewRequired;
    private String reviewedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewedTime;
    private String reviewOpinion;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
    private Integer durationMs;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
