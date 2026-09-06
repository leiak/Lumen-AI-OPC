package com.ruoyi.opc.billing.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 钱包交易流水。
 *
 * <p>由 {@code OpcWalletService} 在每次余额变动时原子写入，配合 {@code tx_code} 唯一索引
 * 保证重试幂等。报表 / 对账 / 审计都依赖这张表。
 *
 * @author OAC
 */
@Data
public class OpcTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String txCode;
    private Long walletId;
    private Long companyId;

    /** RECHARGE / CONSUME / REFUND / FREEZE / UNFREEZE / REWARD */
    private String txType;

    /** 变动金额（始终为正数，方向由 txType 决定） */
    private BigDecimal amount;

    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;

    /** 业务类型：RECHARGE_ORDER / VOUCHER / INVITE_REWARD / AGENT_RUN */
    private String bizType;
    private Long bizId;
    private String description;
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}