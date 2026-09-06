package com.ruoyi.opc.billing.mapper;

import com.ruoyi.opc.billing.domain.OpcTransaction;

import java.util.List;

public interface OpcTransactionMapper {

    int insert(OpcTransaction record);

    OpcTransaction selectById(Long id);

    OpcTransaction selectByTxCode(String txCode);

    List<OpcTransaction> selectByWallet(Long walletId, Integer limit);

    List<OpcTransaction> selectByCompany(Long companyId, String txType, Integer limit);
}