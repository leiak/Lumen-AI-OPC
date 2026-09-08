package com.ruoyi.opc.billing.service.impl;

import com.ruoyi.opc.billing.domain.OpcTransaction;
import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.mapper.OpcTransactionMapper;
import com.ruoyi.opc.billing.mapper.OpcWalletMapper;
import com.ruoyi.opc.billing.service.IOpcWalletService;
import com.ruoyi.opc.billing.vo.WalletAggVo;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 钱包服务：余额 + 流水双写，原子一致。
 *
 * <h3>不变量</h3>
 * <ul>
 *   <li>每个余额变动必写 {@link OpcTransaction}（流水不可丢）</li>
 *   <li>txCode 全局唯一，由调用方传入（订单号 / 业务号），保证幂等</li>
 *   <li>所有方法走 {@link Propagation#REQUIRED} 事务，余额 + 流水要么都成要么都回滚</li>
 *   <li>amount 始终为正数；余额变动方向由 txType 决定</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcWalletServiceImpl implements IOpcWalletService {

    private final OpcWalletMapper walletMapper;
    private final OpcTransactionMapper txMapper;

    /** txType 常量 — 与 DB enum / 报表口径一致 */
    public static final String TX_RECHARGE = "RECHARGE";
    public static final String TX_CONSUME  = "CONSUME";
    public static final String TX_REFUND   = "REFUND";
    public static final String TX_FREEZE   = "FREEZE";
    public static final String TX_UNFREEZE = "UNFREEZE";
    public static final String TX_REWARD   = "REWARD";

    @Override
    public OpcWallet getByCompanyUser(Long companyId, Long userId) {
        return walletMapper.selectByCompanyUser(companyId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpcWallet getOrCreate(Long companyId, Long userId) {
        OpcWallet wallet = walletMapper.selectByCompanyUser(companyId, userId);
        if (wallet == null) {
            wallet = new OpcWallet();
            wallet.setCompanyId(companyId);
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozen(BigDecimal.ZERO);
            wallet.setTotalRecharge(BigDecimal.ZERO);
            wallet.setTotalConsume(BigDecimal.ZERO);
            wallet.setStatus("ACTIVE");
            wallet.setCreateBy(String.valueOf(userId));
            walletMapper.insert(wallet);
        }
        return wallet;
    }

    @Override
    public OpcWallet getById(Long walletId) {
        return walletMapper.selectById(walletId);
    }

    @Override
    public WalletAggVo aggregateWalletByCompany(Long companyId) {
        // wallet agg 无 period 入参，仅校验 companyId（保持与 AggSupport 一致的入参契约）
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        Map<String, Object> agg = AggSupport.orEmpty(walletMapper.selectBalanceSumByCompany(companyId));
        return WalletAggVo.builder()
                .companyId(companyId)
                .balance(AggSupport.amount(agg.get("balance")))
                .currency("CNY")
                .build();
    }

    // ===================== 余额变动 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpcTransaction recharge(Long companyId, Long userId, BigDecimal amount,
                                   String bizType, String bizId, String description) {
        validateAmount(amount);
        OpcWallet wallet = getOrCreate(companyId, userId);

        BigDecimal before = wallet.getBalance();
        int rows = walletMapper.recharge(wallet.getId(), amount);
        if (rows == 0) throw new OpcException("余额更新失败");

        BigDecimal after = before.add(amount);
        return writeTx(wallet, TX_RECHARGE, amount, before, after,
                bizType, bizId, description, String.valueOf(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpcTransaction consume(Long companyId, Long userId, BigDecimal amount,
                                  String bizType, Long bizId, String description) {
        validateAmount(amount);
        OpcWallet wallet = getOrCreate(companyId, userId);

        BigDecimal before = wallet.getBalance();
        if (before.compareTo(amount) < 0) {
            throw new OpcException("余额不足：当前 ¥" + before + "，需扣 ¥" + amount);
        }

        int rows = walletMapper.deductBalance(wallet.getId(), amount);
        if (rows == 0) throw new OpcException("余额不足（并发扣款竞争失败）");

        BigDecimal after = before.subtract(amount);
        OpcTransaction tx = writeTx(wallet, TX_CONSUME, amount, before, after,
                bizType, bizId, description, String.valueOf(userId));
        log.info("[Wallet] consume walletId={} amount={} biz={}:{}", wallet.getId(), amount, bizType, bizId);
        return tx;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpcTransaction refund(Long companyId, Long userId, BigDecimal amount,
                                 String bizType, Long bizId, String description) {
        validateAmount(amount);
        OpcWallet wallet = getOrCreate(companyId, userId);

        BigDecimal before = wallet.getBalance();
        walletMapper.recharge(wallet.getId(), amount);
        BigDecimal after = before.add(amount);

        return writeTx(wallet, TX_REFUND, amount, before, after,
                bizType, bizId, description, String.valueOf(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpcTransaction grant(Long companyId, Long userId, BigDecimal amount,
                                String bizType, Long bizId, String description) {
        validateAmount(amount);
        OpcWallet wallet = getOrCreate(companyId, userId);

        BigDecimal before = wallet.getBalance();
        walletMapper.recharge(wallet.getId(), amount);
        BigDecimal after = before.add(amount);

        return writeTx(wallet, TX_REWARD, amount, before, after,
                bizType, bizId, description, String.valueOf(userId));
    }

    @Override
    public List<OpcTransaction> listTransactions(Long companyId, String txType, Integer limit) {
        return txMapper.selectByCompany(companyId, txType, limit);
    }

    // ===================== 兼容旧接口 =====================

    @Override
    @Deprecated
    public int recharge(Long companyId, Long userId, BigDecimal amount, String payMethod, String tradeNo) {
        // 旧路径：模拟支付立即到账（Controller 在用）
        recharge(companyId, userId, amount, "RECHARGE_ORDER", tradeNo, "钱包充值");
        return 1;
    }

    @Override
    @Deprecated
    public boolean deduct(Long companyId, Long userId, BigDecimal amount) {
        consume(companyId, userId, amount, "LEGACY_DEDUCT", null, "兼容旧扣款");
        return true;
    }

    // ===================== 内部工具 =====================

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OpcException("金额必须大于 0");
        }
    }

    /**
     * 写一条交易流水。txCode 由调用方传入作为幂等键；冲突说明重复调用，吞掉并返回旧记录。
     */
    private OpcTransaction writeTx(OpcWallet wallet, String txType, BigDecimal amount,
                                   BigDecimal before, BigDecimal after,
                                   String bizType, Object bizId, String description, String createBy) {
        OpcTransaction tx = new OpcTransaction();
        tx.setTxCode(bizId == null ? null : bizId.toString());
        // bizId 为空时也要有 txCode —— 用 txType + walletId + nanos 兜底，但调用方应优先传 bizId
        if (tx.getTxCode() == null || tx.getTxCode().isEmpty()) {
            tx.setTxCode(txType + "-" + wallet.getId() + "-" + System.nanoTime());
        }
        tx.setWalletId(wallet.getId());
        tx.setCompanyId(wallet.getCompanyId());
        tx.setTxType(txType);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setBizType(bizType);
        if (bizId instanceof Long l) tx.setBizId(l);
        tx.setDescription(description);
        tx.setCreateBy(createBy);

        try {
            txMapper.insert(tx);
        } catch (Exception e) {
            // 唯一键冲突 = 重复请求，返回旧值（让 Controller 不抛 500）
            OpcTransaction exist = txMapper.selectByTxCode(tx.getTxCode());
            if (exist != null) {
                log.warn("[Wallet] txCode={} 已存在，幂等返回旧记录", tx.getTxCode());
                return exist;
            }
            throw e;
        }
        return tx;
    }
}