package com.ruoyi.opc.billing.service;

import com.ruoyi.opc.billing.domain.OpcWallet;

import java.math.BigDecimal;

public interface IOpcWalletService {

    OpcWallet getByCompanyUser(Long companyId, Long userId);

    OpcWallet getOrCreate(Long companyId, Long userId);

    /** 充值（模拟） */
    int recharge(Long companyId, Long userId, BigDecimal amount, String payMethod, String tradeNo);

    /** 扣费（雇佣/订阅/Token 消耗） */
    boolean deduct(Long companyId, Long userId, BigDecimal amount);

}
