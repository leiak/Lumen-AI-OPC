package com.ruoyi.opc.finance.mapper;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import org.apache.ibatis.annotations.Param;

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

}
