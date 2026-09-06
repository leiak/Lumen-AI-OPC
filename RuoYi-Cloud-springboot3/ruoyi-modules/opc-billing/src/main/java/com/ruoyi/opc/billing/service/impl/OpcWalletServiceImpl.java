package com.ruoyi.opc.billing.service.impl;

import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.mapper.OpcWalletMapper;
import com.ruoyi.opc.billing.service.IOpcWalletService;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OpcWalletServiceImpl implements IOpcWalletService {

    private final OpcWalletMapper walletMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public int recharge(Long companyId, Long userId, BigDecimal amount, String payMethod, String tradeNo) {
        OpcWallet wallet = getOrCreate(companyId, userId);
        walletMapper.recharge(wallet.getId(), amount);
        return 1;
    }

    @Override
    public boolean deduct(Long companyId, Long userId, BigDecimal amount) {
        OpcWallet wallet = getOrCreate(companyId, userId);
        int rows = walletMapper.deductBalance(wallet.getId(), amount);
        if (rows == 0) throw new OpcException("余额不足");
        return true;
    }

}
