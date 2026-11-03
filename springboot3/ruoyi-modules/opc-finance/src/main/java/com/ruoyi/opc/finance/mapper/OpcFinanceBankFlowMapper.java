package com.ruoyi.opc.finance.mapper;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface OpcFinanceBankFlowMapper {

    OpcFinanceBankFlow selectById(Long id);

    List<OpcFinanceBankFlow> selectByCompany(Long companyId, Integer extracted, Integer limit);

    int insert(OpcFinanceBankFlow record);

    int update(OpcFinanceBankFlow record);

    int insertBatch(List<OpcFinanceBankFlow> records);

    /**
     * 聚合某公司在指定期间 (YYYY-MM) 的银行流水，供 INSIGHT 聚合端点使用。
     *
     * <p>返回 key 集合：
     * <ul>
     *   <li>in_total — 收入合计 (direction='IN')</li>
     *   <li>out_total — 支出合计 (direction='OUT')</li>
     *   <li>flow_count — 流水条数</li>
     *   <li>extracted_count — 已 AI 抽取条数 (extracted=1)</li>
     * </ul>
     */
    Map<String, Object> aggregateByPeriod(@Param("companyId") Long companyId,
                                          @Param("period") String period);

    /**
     * 查某公司在闭区间 {@code [fromDate, toDate]} 内的全部流水明细（按 trade_time 升序）。
     *
     * <p>汇总在 Java 侧算（明细最多几十条）——避免 SQL 口径与 Java 口径两处漂移。</p>
     *
     * <p><b>这条 SQL 没有 LIMIT，是故意的</b>：截断会变成「静默少给数据」——
     * 调用方拿到一个看起来完整、其实少了几条的区间，数字对不上却没有任何提示，
     * 比慢一点难查得多。前提是「单公司单区间」的明细量有界（演示库里是 6 条）；
     * 真的长起来以后，必须改成显式分页（page/pageSize + total），
     * 并且把「被截断了」写进响应，而不是偷偷砍掉。</p>
     *
     * @param fromDate 起始日（含），yyyy-MM-dd
     * @param toDate   截止日（含），yyyy-MM-dd
     */
    List<OpcFinanceBankFlow> selectByCompanyAndDateRange(@Param("companyId") Long companyId,
                                                          @Param("fromDate") LocalDate fromDate,
                                                          @Param("toDate") LocalDate toDate);

    /**
     * 查某公司最早一条流水的日期（yyyy-MM-dd）；该公司没有流水时返回 null。
     *
     * <p>用作区间查询的 fromDate 缺省值 —— 「自有数据起算」，而不是「30 天前」。</p>
     */
    String selectMinTradeDate(@Param("companyId") Long companyId);

}
