package com.ruoyi.opc.insight.service;

import com.ruoyi.opc.insight.vo.AnomalyVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;

import java.util.List;

/**
 * INSIGHT 异常检测服务接口（M4 Task 6）。
 *
 * @author OAC
 */
public interface IAnomalyService {

    /**
     * 对 KPI 快照执行异常扫描：先跑 8 条硬规则；若未命中 HIGH，再调 LLM 软扫。
     *
     * <p>命中规则全部写入 {@code opc_insight_anomaly} 表，并回填 id。
     *
     * @param snapshot KPI 快照（{@code null} 时返回空列表）
     * @return 命中的异常列表
     */
    List<AnomalyVo> scan(KpiSnapshot snapshot);

    /**
     * 列出某公司 OPEN 状态的异常。
     *
     * @param companyId 公司 ID
     * @param limit     可选条数限制（null 表示不限）
     */
    List<AnomalyVo> listOpen(Long companyId, Integer limit);

    /**
     * 确认异常（status: OPEN → ACK）。
     */
    void acknowledge(Long id);
}
