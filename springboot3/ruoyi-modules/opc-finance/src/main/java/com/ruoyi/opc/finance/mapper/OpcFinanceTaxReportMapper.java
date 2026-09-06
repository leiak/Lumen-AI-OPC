package com.ruoyi.opc.finance.mapper;

import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 税务报表 Mapper
 *
 * <p>命名约定:
 * <ul>
 *   <li>按主键 / 编号查: selectById / selectByCode</li>
 *   <li>按公司 + 期间查: selectByCompanyAndPeriod</li>
 *   <li>按公司列表: listByCompany(可选 period/status 过滤)</li>
 *   <li>插入: insertTaxReport</li>
 *   <li>更新: update</li>
 * </ul>
 *
 * <p>说明:任务拆分(OPC-W1-TASK-BREAKDOWN §4.1)中曾提到
 * {@code selectByUserAndPeriod} 与 {@code listByUser},但
 * {@code opc_finance_tax_report} 表结构只有 {@code company_id},
 * 不持有 {@code user_id}(多租户隔离靠公司)。这里按实际 schema
 * 实现为 {@code selectByCompanyAndPeriod} 与 {@code listByCompany},
 * 后续 Sub-task 4.2 Service 层会基于 opc_company_member 关联用户。
 *
 * @author OAC
 */
public interface OpcFinanceTaxReportMapper {

    OpcFinanceTaxReport selectById(@Param("id") Long id);

    OpcFinanceTaxReport selectByCode(@Param("reportCode") String reportCode);

    /**
     * 按公司 + 期间取最新一条(同一公司同期间一般多条,各对应一个税种,
     * 此方法用于服务层拿到某个税种的最新记录)
     */
    OpcFinanceTaxReport selectByCompanyAndPeriod(@Param("companyId") Long companyId,
                                                 @Param("period") String period,
                                                 @Param("taxType") String taxType);

    /**
     * 列出某公司的报表,支持 period / status 过滤与 limit
     */
    List<OpcFinanceTaxReport> listByCompany(@Param("companyId") Long companyId,
                                            @Param("period") String period,
                                            @Param("status") String status,
                                            @Param("limit") Integer limit);

    int insertTaxReport(OpcFinanceTaxReport report);

    int update(OpcFinanceTaxReport report);

}
