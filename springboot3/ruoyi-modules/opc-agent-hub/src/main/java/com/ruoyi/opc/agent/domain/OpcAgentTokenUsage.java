package com.ruoyi.opc.agent.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Token 消耗 opc_agent_token_usage
 *
 * @author OAC
 */
@Data
public class OpcAgentTokenUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String usageCode;
    private Long companyId;
    private Long userId;
    private Long instanceId;
    private Long taskId;
    private String model;
    private String modelType;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Integer tokenTotal;
    private BigDecimal unitPriceInput;
    private BigDecimal unitPriceOutput;
    private BigDecimal cost;
    private Integer latencyMs;
    private Integer success;
    private String errorMessage;
    private String requestId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date bizDate;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

}
