package com.ruoyi.opc.billing.mapper;

import com.ruoyi.opc.billing.domain.OpcWallet;

public interface OpcWalletMapper {

    OpcWallet selectByCompanyUser(Long companyId, Long userId);

    OpcWallet selectById(Long id);

    int insert(OpcWallet record);

    int update(OpcWallet record);

    /** 余额扣减（乐观锁） */
    int deductBalance(Long id, java.math.BigDecimal amount);

    /** 余额充值 */
    int recharge(Long id, java.math.BigDecimal amount);

}
