package com.ruoyi.opc.billing.mapper;

import com.ruoyi.opc.billing.domain.OpcWallet;

import java.util.Map;

public interface OpcWalletMapper {

    OpcWallet selectByCompanyUser(Long companyId, Long userId);

    OpcWallet selectById(Long id);

    int insert(OpcWallet record);

    int update(OpcWallet record);

    /** 余额扣减（乐观锁） */
    int deductBalance(Long id, java.math.BigDecimal amount);

    /** 余额充值 */
    int recharge(Long id, java.math.BigDecimal amount);

    /**
     * 公司级钱包余额合计（M4 INSIGHT KPI）— SUM(balance) where company_id = ?
     * 返回 Map，key="balance"，value 是 BigDecimal / Long / null（driver 决定）。
     */
    Map<String, Object> selectBalanceSumByCompany(Long companyId);

}
