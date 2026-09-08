package com.ruoyi.opc.finance.service;

import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.vo.VoucherAggVo;

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

    /**
     * 按期间聚合凭证数据（M4 Task 1，供 opc-insight 经 Feign 拉取）。
     *
     * @param companyId 公司 ID
     * @param period    所属期 YYYY-MM
     * @throws com.ruoyi.opc.common.exception.OpcException 当 companyId 为空 / period 格式非法
     */
    VoucherAggVo aggregateByPeriod(Long companyId, String period);

}
