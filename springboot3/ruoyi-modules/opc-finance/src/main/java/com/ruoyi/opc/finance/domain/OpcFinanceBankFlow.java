package com.ruoyi.opc.finance.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 银行流水 opc_finance_bank_flow
 *
 * @author OAC
 */
@Data
public class OpcFinanceBankFlow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String flowCode;
    private Long companyId;
    private String bankAccount;
    private String bankName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date tradeTime;
    private String direction;
    private BigDecimal amount;
    private String currency;
    private String counterParty;
    private String memo;
    private String rawText;
    private Integer extracted;
    private Long voucherId;
    private Long agentTaskId;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
