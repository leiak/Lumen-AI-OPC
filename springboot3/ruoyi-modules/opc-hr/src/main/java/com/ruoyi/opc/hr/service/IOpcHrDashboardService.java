package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.dto.OpcHrDashboardDto;

/**
 * HR Dashboard 服务接口 — 聚合 4 项指标:
 * <ol>
 *   <li>漏斗(application 按 status 计数,5 段)</li>
 *   <li>转化率(相邻两段比值,4 段)</li>
 *   <li>平均招聘时长(ACCEPTED offer 响应距 applied_at 天数)</li>
 *   <li>JD 状态分布(job 按 status 计数,4 段)</li>
 * </ol>
 */
public interface IOpcHrDashboardService {

    /**
     * 计算 HR Dashboard 指标。
     *
     * @param companyId 公司 ID(必填)
     * @param sinceDays 时间窗口(天),{@code <= 0} 兜底 30 天
     * @return 4 项指标的 DTO
     */
    OpcHrDashboardDto getDashboard(Long companyId, int sinceDays);
}
