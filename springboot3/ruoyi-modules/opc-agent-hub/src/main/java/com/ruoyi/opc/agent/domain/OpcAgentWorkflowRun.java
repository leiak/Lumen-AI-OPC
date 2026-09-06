package com.ruoyi.opc.agent.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 工作流执行历史 opc_agent_workflow_run
 *
 * @author OAC
 */
@Data
public class OpcAgentWorkflowRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String runCode;
    private Long workflowId;
    private Long companyId;
    /** MANUAL / CRON / EVENT */
    private String triggerType;
    private String triggerSource;
    /** PENDING / RUNNING / SUCCESS / FAILED / CANCELLED */
    private String status;
    private String inputParams;
    private String outputResult;
    private String errorMessage;
    private String stepLogs;
    private Integer tokenUsed;
    private BigDecimal cost;
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
