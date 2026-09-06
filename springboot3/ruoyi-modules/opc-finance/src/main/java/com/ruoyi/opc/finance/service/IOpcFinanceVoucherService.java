package com.ruoyi.opc.finance.service;

import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;

import java.util.List;

public interface IOpcFinanceVoucherService {

    OpcFinanceVoucher getById(Long id);

    List<OpcFinanceVoucher> listByCompany(Long companyId, String period, String status, Integer limit);

    Long create(OpcFinanceVoucher voucher);

    int update(OpcFinanceVoucher voucher);

    /** 审核通过 */
    int reviewPass(Long id, String reviewer);

    /** 审核拒绝 */
    int reviewReject(Long id, String reviewer, String opinion);

    /** 入账 */
    int post(Long id, String operator);

}
