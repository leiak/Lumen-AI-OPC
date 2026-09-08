package com.ruoyi.opc.finance.service;

import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;

import java.util.List;

/**
 * 税务报表 Service（W1 Sub-task 4.2）
 *
 * <p>核心能力：为某公司在指定月份聚合一篮子财务凭证数据，
 * 调用 LLM 生成报税建议（或在 LLM 不可用时回退到纯聚合数据），
 * 将报表落库到 {@code opc_finance_tax_report}。
 *
 * <p>关于方法签名的设计取舍：W1-TASK-BREAKDOWN §4.2 中曾写
 * {@code generateMonthlyReport(userId, yearMonth)}，但表结构
 * {@code opc_finance_tax_report.company_id} 没有对应的
 * {@code user_id}（多租户隔离靠公司），故当前实现按
 * {@code companyId} 主键聚合。如果未来需要"按当前登录用户解析其
 * 默认公司"，可在 Controller 层用 {@code opc_company_member} 解析后再调用本方法。
 *
 * @author OAC
 */
public interface IOpcFinanceTaxReportService {

    /**
     * 生成月度税务报表
     *
     * @param companyId 公司 ID
     * @param period    期间 YYYY-MM（如 "2026-09"）
     * @param createBy  操作者用户名（写审计字段 create_by）
     * @return 新报表的主键 ID
     * @throws com.ruoyi.opc.common.exception.OpcException 当公司不存在 / 期间格式错误
     */
    Long generateMonthlyReport(Long companyId, String period, String createBy);

    /** 按公司 + 可选 period / status + limit 列表 */
    List<OpcFinanceTaxReport> listByCompany(Long companyId, String period, String status, Integer limit);

    /** 按主键查询 */
    OpcFinanceTaxReport getById(Long id);

    /** 按业务编号查询 */
    OpcFinanceTaxReport getByCode(String reportCode);

    /**
     * 按公司 + 期间取当期最新报表（M4 Task 1，供 opc-insight 经 Feign 拉取）。
     *
     * <p>税种固定为默认税种（增值税 VAT）——INSIGHT 的 KPI 只关心主税种应缴额。
     *
     * @return 当期无报表时返回 {@code null}（调用方按「降级为空」处理，不抛异常）
     * @throws com.ruoyi.opc.common.exception.OpcException 当 companyId 为空 / period 格式非法
     */
    OpcFinanceTaxReport getByCompanyAndPeriod(Long companyId, String period);

}
