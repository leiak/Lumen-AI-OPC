package com.ruoyi.opc.finance.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 税务报表 opc_finance_tax_report
 *
 * <p>每月 1 号 0 点由 opc-finance 模块的月度报表生成 Service 写入,
 * 状态机:DRAFT → SUBMITTED → PAID。
 *
 * @author OAC
 */
@Data
public class OpcFinanceTaxReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 申报编号(系统生成,UK) */
    private String reportCode;

    /** 公司 ID(对应 opc_company_profile.id) */
    private Long companyId;

    /** 税种(VAT/CIT/SD/...) */
    private String taxType;

    /** 所属期 YYYY-MM */
    private String period;

    /** 国税/地税申报流水号 */
    private String declarationNo;

    /** 应税金额 */
    private BigDecimal taxableAmount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 应缴 */
    private BigDecimal payAmount;

    /** 已缴 */
    private BigDecimal paidAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dueDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    /** 触发生成的 Agent 任务 ID */
    private Long agentTaskId;

    /** DRAFT / SUBMITTED / PAID */
    private String status;

    /** 附件(JSON 数组或逗号分隔) */
    private String attachments;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
