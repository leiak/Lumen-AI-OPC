package com.ruoyi.opc.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 钱包公司级聚合结果（供 opc-insight 经 Feign 拉取）
 *
 * <p>字段来源：{@code opc_wallet} 按 {@code company_id} 聚合（{@code SUM(balance)}）。
 * 金额一律 {@link BigDecimal} 且 scale=2，避免不同 JDBC driver 返回的 SUM() scale
 * 不一致（见 W1.4.2 经验）。
 *
 * <p>当前架构：opc_wallet 是 1 个 user 1 个钱包，所以公司级余额 = 公司下所有用户钱包余额之和。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 公司下所有钱包余额合计（scale=2） */
    private BigDecimal balance;

    /** 币种：当前固定 CNY（保留扩展位） */
    private String currency;
}
