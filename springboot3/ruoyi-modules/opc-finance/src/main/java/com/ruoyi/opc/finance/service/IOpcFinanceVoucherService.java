package com.ruoyi.opc.finance.service;

import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
import com.ruoyi.opc.finance.vo.VoucherRangeVo;

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

    /**
     * 区间查询凭证（供数字员工经工具端点调用）。
     *
     * <p>闭区间 {@code [fromDate, toDate]}，日期格式 yyyy-MM-dd：
     * <ul>
     *   <li>{@code fromDate} 缺省 = 该公司最早一张凭证的日期</li>
     *   <li>{@code toDate} 缺省 = 昨天（北京时间）</li>
     * </ul>
     *
     * <p>口径：收入 = {@code SUM(total_credit)}（贷方），支出 = {@code SUM(total_debit)}（借方）。
     *
     * @return 明细 + 汇总 + 昨天子集，并回写实际使用的 fromDate / toDate
     * @throws com.ruoyi.opc.common.exception.OpcException 当 companyId 为空 /
     *         日期格式非法 / fromDate 晚于 toDate
     */
    VoucherRangeVo queryRange(Long companyId, String fromDate, String toDate);

}
