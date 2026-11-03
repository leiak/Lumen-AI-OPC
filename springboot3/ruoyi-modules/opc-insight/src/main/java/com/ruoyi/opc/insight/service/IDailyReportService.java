package com.ruoyi.opc.insight.service;

import com.ruoyi.opc.insight.vo.DailyReportUpsertRequest;
import com.ruoyi.opc.insight.vo.DailyReportVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 日报生成服务（M4 Task 8）。
 *
 * <p>从 KPI 快照 + LLM 生成自然语言日报，落库到 {@code opc_insight_daily_report}。
 * 提供按日期范围查询与按 ID 查询两个读侧方法。</p>
 *
 * <p><b>签名约束（M4 Task 7 沿用）</b>：{@link #generate(Long, String)} 保持
 * {@code void} 返回值 —— {@code InsightDailyReportJob} 调用时忽略返回值，改为
 * {@code Long} 会破坏既有 job 测试。若未来需返回 reportId，建议新增
 * {@code generateAndReturnId} 重载而非修改本方法。</p>
 */
public interface IDailyReportService {

    /**
     * 为指定公司生成指定日期的日报。
     *
     * <p>流程：参数校验 → {@link IKpiService#snapshot} → LLM 生成
     * （失败时使用固定模板回退）→ 落库。</p>
     *
     * @param companyId 公司 ID
     * @param date      报告日期，格式为 {@code yyyy-MM-dd}，不能晚于今天
     */
    void generate(Long companyId, String date);

    /**
     * 按日期范围列出某公司的日报，按 period DESC 排序。
     *
     * @param companyId 公司 ID（必填）
     * @param from      起始日期（含）
     * @param to        截止日期（含）
     * @param limit     可选条数限制；{@code null}/0 = 不限
     */
    List<DailyReportVo> listByDateRange(Long companyId, LocalDate from, LocalDate to, Integer limit);

    /**
     * 按主键查询单条日报。{@code id} 不存在时抛 {@code OpcException}。
     */
    DailyReportVo getById(Long id);

    /**
     * 工作流写入路径 —— 把工作流产出的日报 upsert 进 {@code opc_insight_daily_report}。
     *
     * <p><b>与 {@link #generate(Long, String)} 的区别</b>：{@code generate} 是手工路径，
     * 自己去取 KPI、调 LLM、落库，来源写死 {@code "MANUAL"}；本方法是工作流路径，
     * 内容由调用方（引擎）算好传进来，来源写死 {@code "AGENT_WORKFLOW"}。
     * 两条路径落到同一张表的同一行上 —— 所以才有 {@code report_source} 这一列，
     * 让事后能分清是谁写的。</p>
     *
     * <p>校验失败抛 {@code OpcException}（400）；已有同 {@code (company_id, period)}
     * 的行则先删旧再插新（snapshot 语义，与 {@code generate} 一致）。</p>
     *
     * @param req 工作流产出的日报内容
     * @return 落库后的行 id
     */
    Long upsertFromWorkflow(DailyReportUpsertRequest req);
}
