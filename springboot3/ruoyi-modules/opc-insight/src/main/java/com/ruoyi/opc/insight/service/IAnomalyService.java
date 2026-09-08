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
     * 按主键查询单条异常，状态无关（OPEN / ACK / RESOLVED 都能查到）。
     *
     * <p>供 {@code AlertController.detail(...)} 使用 —— 之前的实现用
     * {@code listOpen(...).stream().filter(...).findFirst()}，ACK 状态的异常
     * 永远返回 500。现改为按主键直查 + domain→VO 转换（与 {@code listOpen}
     * 共享 {@code toVo(...)} 路径，保证字段语义一致）。</p>
     *
     * @param id 异常 ID（必填）
     * @return 异常 VO（{@code id} 不存在时抛 {@code OpcException}）
     * @throws com.ruoyi.opc.common.exception.OpcException {@code id} 为空或异常不存在时
     */
    AnomalyVo getById(Long id);

    /**
     * 确认异常（status: OPEN → ACK）。
     */
    void acknowledge(Long id);
}
