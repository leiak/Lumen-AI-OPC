package com.ruoyi.opc.finance.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 财务凭证 opc_finance_voucher
 *
 * @author OAC
 */
@Data
public class OpcFinanceVoucher implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String voucherCode;
    private Long companyId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date voucherDate;
    private String period;
    private String summary;
    private String sourceType;
    private String sourceRefId;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String entriesJson;
    private String attachments;
    private Long agentTaskId;
    private String status;
    private String reviewedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewedTime;
    private String postedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date postedTime;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

}
