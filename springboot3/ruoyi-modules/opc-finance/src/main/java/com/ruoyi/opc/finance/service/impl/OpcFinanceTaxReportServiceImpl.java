package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.mapper.OpcFinanceTaxReportMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceVoucherMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 月度税务报表 Service 实现（W1 Sub-task 4.2）
 *
 * <p>核心流程：
 * <ol>
 *   <li>校验 period 格式（YYYY-MM）</li>
 *   <li>从 {@code opc_finance_voucher} 聚合当月数据 → 写入应税销售额 / 税额 / 凭证张数</li>
 *   <li>尝试调用 LLM 生成"报税建议"（system prompt + 数字上下文），
 *       LLM 调用失败（{@link OpcException} 或其他异常）时回退到固定模板，不影响落库</li>
 *   <li>写入 {@code opc_finance_tax_report}（status=DRAFT, taxType=VAT）</li>
 *   <li>返回新报表的主键 ID</li>
 * </ol>
 *
 * <p>设计取舍：
 * <ul>
 *   <li>不调用 opc_company_member 解析默认公司 — Service 只依赖 companyId 与 voucher 数据</li>
 *   <li>LLM 失败不重试 — 受 LlmGateway 自身的 fallback 链保护（DeepSeek → OpenAI → 文心）</li>
 *   <li>不重复生成本期已存在 DRAFT 状态的报表 — 简单幂等，避免对账混淆
 *       （重复生成会在调用方用幂等键控制，此处不强制）</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcFinanceTaxReportServiceImpl implements IOpcFinanceTaxReportService {

    /** 默认税种：增值税 */
    private static final String DEFAULT_TAX_TYPE = "VAT";

    /** 增值税征收率 13%（中国一般纳税人） */
    private static final BigDecimal VAT_RATE = new BigDecimal("0.13");

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OpcFinanceTaxReportMapper taxReportMapper;
    private final OpcFinanceVoucherMapper voucherMapper;
    private final LlmGateway llmGateway;

    @Override
    public Long generateMonthlyReport(Long companyId, String period, String createBy) {
        if (companyId == null) throw new OpcException("公司 ID 不能为空");
        validatePeriod(period);

        // 1) 聚合当月凭证数据
        Map<String, Object> agg = voucherMapper.aggregateByPeriod(companyId, period);
        // 归一化到 scale=2 — 不同 DB driver 返回的 SUM() scale 不一致，
        // 统一存 2 位便于报表展示 & 避免 equals() 比较失配
        BigDecimal taxableAmount = toBigDecimal(agg.get("taxable_amount")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal inputTax      = toBigDecimal(agg.get("input_tax")).setScale(2, RoundingMode.HALF_UP);
        long voucherCount        = toLong(agg.get("voucher_count"));
        long rejectedCount       = toLong(agg.get("rejected_count"));

        // 2) 应缴 = (销项 - 进项) * 13% 估值；这里用进项已经存于借方，做简化计算：
        //    pay_amount ≈ max(taxable_amount * 13% - input_tax, 0)
        BigDecimal outputTax   = taxableAmount.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payAmount   = outputTax.subtract(inputTax).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount   = payAmount; // 净额应纳税额

        // 3) 调 LLM 生成报税建议（失败 → 回退）
        String advice = generateTaxAdvice(period, taxableAmount, taxAmount, voucherCount, rejectedCount);

        // 4) 构造 + 落库
        OpcFinanceTaxReport report = new OpcFinanceTaxReport();
        report.setReportCode(OpcCodeGenerator.taxReportCode());
        report.setCompanyId(companyId);
        report.setTaxType(DEFAULT_TAX_TYPE);
        report.setPeriod(period);
        report.setTaxableAmount(taxableAmount);
        report.setTaxAmount(taxAmount);
        report.setPayAmount(payAmount);
        report.setPaidAmount(new BigDecimal("0.00"));
        report.setDueDate(nextMonth15th(period));
        report.setStatus("DRAFT");
        report.setAttachments(advice); // 报税建议暂存 attachments，后续 Sub-task 4.3 可拆 JSON
        report.setCreateBy(createBy);

        taxReportMapper.insertTaxReport(report);
        log.info("[TaxReport] 生成报表完成: reportCode={}, company={}, period={}, payable={}",
                report.getReportCode(), companyId, period, payAmount);
        return report.getId();
    }

    @Override
    public List<OpcFinanceTaxReport> listByCompany(Long companyId, String period, String status, Integer limit) {
        return taxReportMapper.listByCompany(companyId, period, status, limit == null ? 20 : limit);
    }

    @Override
    public OpcFinanceTaxReport getById(Long id) {
        return taxReportMapper.selectById(id);
    }

    @Override
    public OpcFinanceTaxReport getByCode(String reportCode) {
        return taxReportMapper.selectByCode(reportCode);
    }

    @Override
    public OpcFinanceTaxReport getByCompanyAndPeriod(Long companyId, String period) {
        AggSupport.validate(companyId, period);
        return taxReportMapper.selectByCompanyAndPeriod(companyId, period, DEFAULT_TAX_TYPE);
    }

    // ============= 私有辅助 =============

    /** 校验期间格式：YYYY-MM 且月份在 1..12 */
    private void validatePeriod(String period) {
        if (period == null || !period.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new OpcException("period 格式错误，应为 YYYY-MM");
        }
        // 顺便解析一次，避免 regex 通过但非法日期（如 "2026-13"）
        YearMonth.parse(period, PERIOD_FMT);
    }

    /**
     * 调 LLM 生成报税建议。所有异常被吞掉 —— LLM 失败不影响落库。
     */
    private String generateTaxAdvice(String period,
                                      BigDecimal taxableAmount,
                                      BigDecimal taxAmount,
                                      long voucherCount,
                                      long rejectedCount) {
        String fallback = buildFallbackAdvice(period, taxableAmount, taxAmount, voucherCount, rejectedCount);

        try {
            String prompt = String.format(
                    "你是资深中国注册会计师。请基于以下月度数据给出简明报税建议（≤200 字，纯文本）：\n" +
                            "- 所属期：%s\n" +
                            "- 当月凭证张数：%d\n" +
                            "- 应税销售额（贷方合计）：¥%s\n" +
                            "- 本月应纳税额：¥%s\n" +
                            "- 被拒绝凭证：%d 张\n\n" +
                            "要求：按风险高低排序提示，并在末尾给出 1 条具体动作建议（例如'完善凭证摘要'或'补充进项发票'）。",
                    period, voucherCount, taxableAmount.toPlainString(),
                    taxAmount.toPlainString(), rejectedCount);

            ChatResponse resp = llmGateway.chat(
                    List.of(ChatMessage.system("你是 OPC 数字员工——财务税务顾问。"),
                            ChatMessage.user(prompt)),
                    ChatModelProvider.ChatOptions.builder()
                            .temperature(0.2)
                            .maxTokens(500)
                            .timeoutSeconds(30)
                            .build(),
                    LlmGateway.ChatContext.builder()
                            .companyId(null) // companyId 由调用方解析
                            .scene("tax-report-monthly")
                            .build()
            );

            if (resp != null && Boolean.TRUE.equals(resp.getSuccess())
                    && resp.getContent() != null && !resp.getContent().isBlank()) {
                return resp.getContent().trim();
            }
            log.warn("[TaxReport] LLM 返回失败/空，回退固定建议: period={}", period);
            return fallback;
        } catch (Exception e) {
            log.warn("[TaxReport] LLM 异常 ({})，回退固定建议: period={}",
                    e.getClass().getSimpleName() + ":" + e.getMessage(), period);
            return fallback;
        }
    }

    /** LLM 失败时的固定模板，保证报表仍写一条值 */
    private String buildFallbackAdvice(String period, BigDecimal taxable, BigDecimal tax,
                                       long voucherCount, long rejectedCount) {
        return String.format(
                "[自动聚合·未走 LLM] %s 共 %d 张凭证，应税销售额 ¥%s，应纳税额 ¥%s，被拒 %d 张。请会计复核后申报。",
                period, voucherCount, taxable.toPlainString(), tax.toPlainString(), rejectedCount);
    }

    /** 解析 "2026-09" → 2026-10-15（Date 类型，不带时间） */
    private Date nextMonth15th(String period) {
        YearMonth ym = YearMonth.parse(period, PERIOD_FMT);
        LocalDate due = ym.plusMonths(1).atDay(15);
        return Date.from(due.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}
