package com.ruoyi.opc.agent.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * Agent 编排工作流 opc_agent_workflow
 *
 * @author OAC
 */
@Data
public class OpcAgentWorkflow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String workflowCode;
    private String name;
    private Long companyId;
    private Long definitionId;
    /** MANUAL / CRON / EVENT */
    private String triggerType;
    private String triggerConfig;
    private String dagJson;
    private Integer enabled;
    /** Quartz 风格 6 段 cron，仅 triggerType=CRON 时有意义（V20260905 新增） */
    private String cronExpression;
    /** cron 求值时区，默认 Asia/Shanghai（V20260905 新增） */
    private String timezone;
    /** 下次预计执行时间，由 cronExpression 推算（V20260905 新增） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRunAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastRunTime;
    private Integer runCount;
    /** DRAFT / PUBLISHED / ARCHIVED */
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

}
