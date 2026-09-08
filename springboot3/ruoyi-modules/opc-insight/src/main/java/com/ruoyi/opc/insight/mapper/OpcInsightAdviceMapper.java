package com.ruoyi.opc.insight.mapper;

import com.ruoyi.opc.insight.domain.OpcInsightAdvice;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * INSIGHT 决策建议表 Mapper（M4 Task 9）。
 *
 * <p>命名约定（与 opc-finance / opc-insight 已有的 {@code OpcInsightDailyReportMapper}
 * / {@code OpcInsightAnomalyMapper} 一致）：
 * <ul>
 *   <li>{@link #insert(OpcInsightAdvice)} — useGeneratedKeys 回填 id</li>
 *   <li>{@link #selectById(Long)} — 按主键查询</li>
 *   <li>{@link #selectRecent(Long, String, Integer)} — 7 天缓存查重（核心）</li>
 *   <li>{@link #selectByCompany(Long, Integer)} — 按公司列出建议历史</li>
 *   <li>{@link #updateById(OpcInsightAdvice)} — 重生成时覆盖 advice_md / llm_used / confidence</li>
 * </ul>
 *
 * <p>表 {@code opc_insight_advice} 设计为 append-only；{@code updateById} 仅在
 * {@code AdviceServiceImpl.regenerate()} 中被调用，由 service 层控制入口，
 * mapper 层不暴露"无条件更新"接口。</p>
 */
public interface OpcInsightAdviceMapper {

    /**
     * 插入建议记录，自动回填主键到 {@code advice.id}。
     */
    int insert(OpcInsightAdvice advice);

    /**
     * 按主键查询。
     */
    OpcInsightAdvice selectById(@Param("id") Long id);

    /**
     * 按公司 + 主题 + 时间窗口查重（用于 generate() 的 7 天缓存）。
     *
     * <p>SQL 形如 {@code WHERE company_id = ? AND topic = ?
     * AND create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
     * ORDER BY create_time DESC LIMIT 1}，返回最近一条匹配项。</p>
     *
     * @param companyId 公司 ID
     * @param topic     建议主题
     * @param sinceDays 时间窗口（天数），如 7 表示 7 天内
     * @return 最近一条建议；无匹配返回 null
     */
    OpcInsightAdvice selectRecent(@Param("companyId") Long companyId,
                                   @Param("topic") String topic,
                                   @Param("sinceDays") Integer sinceDays);

    /**
     * 按公司列出建议历史，按 create_time DESC 排序。
     *
     * @param companyId 公司 ID
     * @param limit     条数限制（{@code null}/0 = 默认 20，由 service 层处理；mapper 不做兜底）
     */
    List<OpcInsightAdvice> selectByCompany(@Param("companyId") Long companyId,
                                            @Param("limit") Integer limit);

    /**
     * 重生成时覆盖建议正文（仅更新 advice_md / llm_used / confidence 三列，
     * {@code update_time} 由 MySQL ON UPDATE CURRENT_TIMESTAMP 自动维护）。
     *
     * <p>表本身 append-only，此方法只被 {@code AdviceServiceImpl.regenerate()}
     * 调用，调用方需确保合法业务路径。</p>
     */
    int updateById(@Param("advice") OpcInsightAdvice advice);
}