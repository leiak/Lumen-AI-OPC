package com.ruoyi.opc.finance.mapper;

import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface OpcFinanceVoucherMapper {

    OpcFinanceVoucher selectById(Long id);

    OpcFinanceVoucher selectByCode(String code);

    List<OpcFinanceVoucher> selectByCompany(Long companyId, String period, String status, Integer limit);

    Map<String, Object> aggregateDaily(Long companyId, String bizDate);

    /**
     * 聚合某公司在指定期间 (YYYY-MM) 的凭证数据,供月度报表 Service 使用
     *
     * <p>返回 key 集合:
     * <ul>
     *   <li>taxable_amount — 应税销售额 (所有贷方合计)</li>
     *   <li>input_tax — 可抵扣进项税额 (借方合计)</li>
     *   <li>voucher_count — 凭证总张数</li>
     *   <li>posted_count — 已入账 (status='POSTED') 张数</li>
     *   <li>rejected_count — 被拒绝 (status='REJECTED') 张数</li>
     *   <li>pending_count — 待审核 (status IN ('DRAFT','REVIEW')) 张数</li>
     * </ul>
     */
    Map<String, Object> aggregateByPeriod(@Param("companyId") Long companyId,
                                          @Param("period") String period);

    int insert(OpcFinanceVoucher record);

    int update(OpcFinanceVoucher record);

}
