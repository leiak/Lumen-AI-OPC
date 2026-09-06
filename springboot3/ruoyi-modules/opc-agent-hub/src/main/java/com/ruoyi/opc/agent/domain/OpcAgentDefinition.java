package com.ruoyi.opc.agent.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Agent 定义 opc_agent_definition
 *
 * @author OAC
 */
@Data
public class OpcAgentDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String agentCode;
    private String name;
    private String version;
    private String category;
    private String description;
    private String capabilities;
    private String systemPrompt;
    private String toolsConfig;
    private String primaryModel;
    private String fallbackModel;
    private String promptVersion;
    private String memoryType;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private BigDecimal tokenPriceInput;
    private BigDecimal tokenPriceOutput;
    private Integer freeQuota;
    private String iconUrl;
    private String tags;
    private Integer published;
    private Integer sortOrder;
    private Integer downloads;
    private BigDecimal rating;
    private String status;

    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

}
