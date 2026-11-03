package com.ruoyi.opc.finance.mapper;

import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

    /**
     * 查某公司在闭区间 {@code [fromDate, toDate]} 内的全部凭证明细（按 voucher_date 升序）。
     *
     * <p>汇总在 Java 侧算（明细最多几十条）——避免 SQL 口径与 Java 口径两处漂移。</p>
     *
     * <p><b>这条 SQL 没有 LIMIT，是故意的</b>：截断会变成「静默少给数据」——
     * 调用方拿到一个看起来完整、其实少了几条的区间，数字对不上却没有任何提示，
     * 比慢一点难查得多。前提是「单公司单区间」的明细量有界（演示库里是 5 条）；
     * 真的长起来以后，必须改成显式分页（page/pageSize + total），
     * 并且把「被截断了」写进响应，而不是偷偷砍掉。</p>
     *
     * @param fromDate 起始日（含），yyyy-MM-dd
     * @param toDate   截止日（含），yyyy-MM-dd
     */
    List<OpcFinanceVoucher> selectByCompanyAndDateRange(@Param("companyId") Long companyId,
                                                         @Param("fromDate") LocalDate fromDate,
                                                         @Param("toDate") LocalDate toDate);

    /**
     * 查某公司最早一张凭证的日期（yyyy-MM-dd）；该公司没有凭证时返回 null。
     *
     * <p>用作区间查询的 fromDate 缺省值 —— 「自有数据起算」，而不是「30 天前」。</p>
     */
    String selectMinVoucherDate(@Param("companyId") Long companyId);

}
