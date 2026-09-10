package com.ruoyi.opc.insight.mapper;

import com.ruoyi.opc.insight.domain.OpcInsightDailyReport;
import com.ruoyi.opc.insight.vo.DailyReportVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * INSIGHT 日报表 Mapper（M4 Task 8）。
 *
 * <p>命名约定（与 opc-finance / opc-insight 已有的 {@code OpcInsightAnomalyMapper} 一致）：
 * <ul>
 *   <li>{@link #insert(OpcInsightDailyReport)} — useGeneratedKeys 回填 id</li>
 *   <li>{@link #selectById(Long)} — 按主键查询</li>
 *   <li>{@link #selectByCompanyAndDateRange(Long, LocalDate, LocalDate, Integer)} — 日期范围</li>
 *   <li>{@link #selectByCompanyAndDate(Long, LocalDate)} — 单日查重（InsightDailyReportJob 备用）</li>
 * </ul>
 */
public interface OpcInsightDailyReportMapper {

    /**
     * 插入日报，自动回填主键到 {@code report.id}。
     */
    int insert(OpcInsightDailyReport report);

    /**
     * 按主键查询。
     */
    DailyReportVo selectById(@Param("id") Long id);

    /**
     * 按公司 + 日期范围查询，按 period DESC 排序。
     *
     * @param companyId 公司 ID
     * @param from      起始日期（含）
     * @param to        截止日期（含）
     * @param limit     可选条数限制（{@code null}/0 = 不限）
     */
    List<DailyReportVo> selectByCompanyAndDateRange(@Param("companyId") Long companyId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to,
                                                     @Param("limit") Integer limit);

    /**
     * 按公司 + 单日查重（生成时可在插入前先调用以提前拦截重复，避免依赖
     * UNIQUE KEY 抛 {@code DataIntegrityViolationException}）。
     */
    DailyReportVo selectByCompanyAndDate(@Param("companyId") Long companyId,
                                          @Param("period") LocalDate period);

    /**
     * W48.5: 按主键删除，用于 generate 时的 UPSERT 流程（先删旧再插新）。
     */
    int deleteByPrimaryKey(@Param("id") Long id);
}
