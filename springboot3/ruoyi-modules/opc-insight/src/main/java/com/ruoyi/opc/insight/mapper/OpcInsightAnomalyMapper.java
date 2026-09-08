package com.ruoyi.opc.insight.mapper;

import com.ruoyi.opc.insight.domain.OpcInsightAnomaly;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * INSIGHT 异常表 Mapper（M4 Task 6）。
 *
 * <p>命名约定（与 opc-finance 一致）：
 * <ul>
 *   <li>{@link #insert(OpcInsightAnomaly)} — useGeneratedKeys 回填 id</li>
 *   <li>{@link #selectById(Long)} — 按主键查询</li>
 *   <li>{@link #selectOpenByCompany(Long, Integer)} — 列 OPEN 状态异常，按时间倒序</li>
 *   <li>{@link #updateStatus(Long, String)} — 通用 status 更新（ACK / RESOLVED）</li>
 * </ul>
 *
 * @author OAC
 */
public interface OpcInsightAnomalyMapper {

    /**
     * 插入异常记录，自动回填主键到 {@code anomaly.id}。
     */
    int insert(OpcInsightAnomaly anomaly);

    /**
     * 按主键查询。
     */
    OpcInsightAnomaly selectById(@Param("id") Long id);

    /**
     * 列出某公司 OPEN 状态异常，按 create_time DESC 排序。
     *
     * @param companyId 公司 ID
     * @param limit     可选条数限制（null 或 0 = 不限）
     */
    List<OpcInsightAnomaly> selectOpenByCompany(@Param("companyId") Long companyId,
                                                 @Param("limit") Integer limit);

    /**
     * 更新异常处置状态。{@code update_time} 由 SQL ON UPDATE CURRENT_TIMESTAMP 自动维护。
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status);
}
