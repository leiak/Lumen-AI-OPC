package com.ruoyi.opc.agent.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * Agent 实例 opc_agent_instance
 *
 * @author OAC
 */
@Data
public class OpcAgentInstance implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String instanceCode;
    private Long companyId;
    private Long definitionId;
    private String nickname;
    private String hireType;
    private Integer hireDuration;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;
    private Integer autoRenew;
    private String configOverride;
    private String memoryNamespace;
    private Long tokenUsed;
    private Long tokenQuota;
    private Integer taskCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastActiveTime;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

}
