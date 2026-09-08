package com.ruoyi.opc.insight.service;

/**
 * 日报生成服务。
 *
 * <p>日报具体聚合逻辑由后续实现提供；定时任务只依赖该接口，便于独立测试和替换实现。</p>
 */
public interface IDailyReportService {

    /**
     * 为指定公司生成指定日期的日报。
     *
     * @param companyId 公司 ID
     * @param date      报告日期，格式为 {@code yyyy-MM-dd}
     */
    void generate(Long companyId, String date);
}
