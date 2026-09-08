package com.ruoyi.opc.billing.service;

import com.ruoyi.opc.billing.domain.OpcTransaction;
import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.vo.WalletAggVo;

import java.math.BigDecimal;
import java.util.List;

public interface IOpcWalletService {

    OpcWallet getByCompanyUser(Long companyId, Long userId);

    OpcWallet getOrCreate(Long companyId, Long userId);

    OpcWallet getById(Long walletId);

    /**
     * 公司级钱包余额合计（M4 INSIGHT KPI）。
     *
     * <p>当前架构：opc_wallet 是 1 个 user 1 个钱包，公司级余额 = 公司下所有用户钱包余额之和。
     *
     * @param companyId 公司 ID
     * @return WalletAggVo（companyId, balance, currency）
     * @throws com.ruoyi.opc.common.exception.OpcException companyId 为空
     */
    WalletAggVo aggregateWalletByCompany(Long companyId);

    /**
     * 充值（外部支付成功回调时调用）。
     *
     * @param bizType     业务类型，如 RECHARGE_ORDER
     * @param bizId       业务ID（订单号），用作幂等键
     * @return 写好的交易流水
     */
    OpcTransaction recharge(Long companyId, Long userId, BigDecimal amount,
                           String bizType, String bizId, String description);

    /**
     * 消费扣款（AI 调用、订阅、Token 计量等场景）。
     *
     * @return 写好的交易流水
     * @throws com.ruoyi.opc.common.exception.OpcException 余额不足或 amount<=0
     */
    OpcTransaction consume(Long companyId, Long userId, BigDecimal amount,
                           String bizType, Long bizId, String description);

    /**
     * 退款（原路退回）。
     */
    OpcTransaction refund(Long companyId, Long userId, BigDecimal amount,
                          String bizType, Long bizId, String description);

    /**
     * 邀请奖励 / 代金券发放（资金来源方 ≠ 真实充值，记 REWARD 类型）。
     */
    OpcTransaction grant(Long companyId, Long userId, BigDecimal amount,
                         String bizType, Long bizId, String description);

    /**
     * 钱包交易流水查询。
     */
    List<OpcTransaction> listTransactions(Long companyId, String txType, Integer limit);

    /**
     * 兼容旧 Controller 调用（模拟支付用）。
     * @deprecated 直接调用 {@link #recharge(Long, Long, BigDecimal, String, String, String)}
     */
    @Deprecated
    int recharge(Long companyId, Long userId, BigDecimal amount, String payMethod, String tradeNo);

    /**
     * 兼容旧 Controller 调用。
     * @deprecated 直接调用 {@link #consume(Long, Long, BigDecimal, String, Long, String)}
     */
    @Deprecated
    boolean deduct(Long companyId, Long userId, BigDecimal amount);
}