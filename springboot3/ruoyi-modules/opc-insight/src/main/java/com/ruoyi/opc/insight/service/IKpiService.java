package com.ruoyi.opc.insight.service;

import com.ruoyi.opc.insight.vo.KpiSnapshot;

/**
 * INSIGHT KPI 聚合服务接口（M4 Task 5）。
 *
 * <p>从 6 个数据源（凭证 / 流水 / 税务报表 / Token 消耗 / 钱包 / 公司档案）聚合为
 * 单一不可变快照。任何一个数据源失败不影响整体 —— 仅在 {@link KpiSnapshot#isPartial()}
 * 标记为 true。
 *
 * @author OAC
 */
public interface IKpiService {

    /**
     * 聚合指定公司 + 期的 KPI 快照。
     *
     * @param companyId 公司 ID（必填，{@code null} 抛 OpcException）
     * @param period    所属期 YYYY-MM（{@code null}/blank 时默认本月）
     * @return 不可变 KPI 快照（任何降级时 partial=true）
     */
    KpiSnapshot snapshot(Long companyId, String period);

}
