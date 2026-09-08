package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
import com.ruoyi.opc.finance.service.IOpcFinanceTokenUsageService;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.TokenUsageVo;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpcFinanceController} 聚合端点单测 — M4 Task 1
 *
 * <p>覆盖 4 个供 opc-insight Feign 调用的端点：
 * <ul>
 *   <li>{@code GET /opc/finance/agg/voucher}      → {@link VoucherAggVo}</li>
 *   <li>{@code GET /opc/finance/agg/flow}         → {@link FlowAggVo}</li>
 *   <li>{@code GET /opc/finance/agg/tax-report}   → {@link OpcFinanceTaxReport}（可能为 null）</li>
 *   <li>{@code GET /opc/finance/agg/token-usage}  → {@link TokenUsageVo}</li>
 * </ul>
 *
 * <p>与 W2.5 的 {@code OpcFinanceControllerTest} 不同，这 4 个端点不读
 * {@code SecurityUtils}（companyId 由调用方显式传入），所以不需要 mockStatic。
 * 返回类型是 {@link R} 而非 {@code AjaxResult}——Feign 客户端声明的是
 * {@code R<VoucherAggVo>} 等强类型，便于 opc-insight 侧直接反序列化。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcFinanceAggControllerTest {

    private static final Long COMPANY_ID = 1L;
    private static final String PERIOD = "2026-09";

    @Mock
    private IOpcFinanceVoucherService voucherService;

    @Mock
    private IOpcFinanceBankFlowService bankFlowService;

    @Mock
    private IOpcFinanceTaxReportService taxReportService;

    @Mock
    private IOpcFinanceTokenUsageService tokenUsageService;

    @InjectMocks
    private OpcFinanceController controller;

    @Test
    @DisplayName("voucherAgg — 透传 service 聚合结果（借贷合计 + 张数 + 待审数）")
    void voucherAgg_returnsAggregatedTotals() {
        VoucherAggVo vo = VoucherAggVo.builder()
                .companyId(COMPANY_ID)
                .period(PERIOD)
                .debitTotal(new BigDecimal("800.00"))
                .creditTotal(new BigDecimal("1000.00"))
                .voucherCount(50L)
                .pendingCount(5L)
                .build();
        when(voucherService.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(vo);

        R<VoucherAggVo> r = controller.voucherAgg(COMPANY_ID, PERIOD);

        assertEquals(R.SUCCESS, r.getCode());
        assertNotNull(r.getData());
        assertEquals(new BigDecimal("800.00"), r.getData().getDebitTotal());
        assertEquals(new BigDecimal("1000.00"), r.getData().getCreditTotal());
        assertEquals(50L, r.getData().getVoucherCount());
        assertEquals(5L, r.getData().getPendingCount());
        verify(voucherService).aggregateByPeriod(COMPANY_ID, PERIOD);
    }

    @Test
    @DisplayName("flowAgg — 透传 service 聚合结果（收/支合计 + 条数 + 已抽取数）")
    void flowAgg_returnsFlowTotals() {
        FlowAggVo vo = FlowAggVo.builder()
                .companyId(COMPANY_ID)
                .period(PERIOD)
                .inTotal(new BigDecimal("12000.00"))
                .outTotal(new BigDecimal("3500.50"))
                .flowCount(30L)
                .extractedCount(28L)
                .build();
        when(bankFlowService.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(vo);

        R<FlowAggVo> r = controller.flowAgg(COMPANY_ID, PERIOD);

        assertEquals(R.SUCCESS, r.getCode());
        assertNotNull(r.getData());
        assertEquals(new BigDecimal("12000.00"), r.getData().getInTotal());
        assertEquals(new BigDecimal("3500.50"), r.getData().getOutTotal());
        assertEquals(30L, r.getData().getFlowCount());
        assertEquals(28L, r.getData().getExtractedCount());
        verify(bankFlowService).aggregateByPeriod(COMPANY_ID, PERIOD);
    }

    @Test
    @DisplayName("taxReport — 返回当期最新报表；无报表时 data 为 null（不报错）")
    void taxReport_returnsLatestReport() {
        OpcFinanceTaxReport report = new OpcFinanceTaxReport();
        report.setId(9L);
        report.setCompanyId(COMPANY_ID);
        report.setPeriod(PERIOD);
        report.setTaxType("VAT");
        report.setPayAmount(new BigDecimal("130.00"));
        when(taxReportService.getByCompanyAndPeriod(COMPANY_ID, PERIOD)).thenReturn(report);

        R<OpcFinanceTaxReport> r = controller.taxReport(COMPANY_ID, PERIOD);

        assertEquals(R.SUCCESS, r.getCode());
        assertNotNull(r.getData());
        assertEquals(9L, r.getData().getId());
        assertEquals(new BigDecimal("130.00"), r.getData().getPayAmount());
        verify(taxReportService).getByCompanyAndPeriod(COMPANY_ID, PERIOD);

        // 当期没有报表 → data=null，Feign 侧按「降级为空」处理，不能抛异常
        when(taxReportService.getByCompanyAndPeriod(COMPANY_ID, "2026-01")).thenReturn(null);
        R<OpcFinanceTaxReport> empty = controller.taxReport(COMPANY_ID, "2026-01");
        assertEquals(R.SUCCESS, empty.getCode());
        assertNull(empty.getData());
    }

    @Test
    @DisplayName("tokenUsage — 透传 service 聚合结果（token 数 + 花费 + 调用次数）")
    void tokenUsage_returnsUsage() {
        TokenUsageVo vo = TokenUsageVo.builder()
                .companyId(COMPANY_ID)
                .period(PERIOD)
                .inputTokens(120000L)
                .outputTokens(30000L)
                .totalTokens(150000L)
                .totalCost(new BigDecimal("180.5000"))
                .callCount(420L)
                .build();
        when(tokenUsageService.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(vo);

        R<TokenUsageVo> r = controller.tokenUsage(COMPANY_ID, PERIOD);

        assertEquals(R.SUCCESS, r.getCode());
        assertNotNull(r.getData());
        assertEquals(150000L, r.getData().getTotalTokens());
        assertEquals(new BigDecimal("180.5000"), r.getData().getTotalCost());
        assertEquals(420L, r.getData().getCallCount());
        verify(tokenUsageService).aggregateByPeriod(COMPANY_ID, PERIOD);
    }

    // ==================== W11.1 C1 修复验证：OpcException 走 controller 级 handler → R.fail ====================

    @Test
    @DisplayName("invalid period → controller 级 @ExceptionHandler 把 OpcException 转 R.fail(code,msg)（非 AjaxResult）")
    void invalidPeriod_returnsRFailWithCodeAndMsgNotAjaxResult() {
        // W11.1 C1：service 内部 AggSupport.validate(period="2026-13") 抛 OpcException，
        // controller 级 @ExceptionHandler(OpcException.class) 把异常转成 R.fail(code, msg)。
        // 验证点：
        //   1. handler 真的存在，且返回 R<Void>（不是 AjaxResult — 否则 Feign 强类型 R<T> 解析失败）
        //   2. R.code 来自 OpcException.getCode()（默认 500），不能硬编码成 HTTP 500
        //   3. R.msg 来自 OpcException.getMessage()，data=null
        R<Void> r = controller.handleOpcException(new OpcException("period 格式错误，应为 YYYY-MM"));

        assertEquals(500, r.getCode(),
                "OpcException 默认 code=500，必须出现在 R.code 而非 HTTP 500");
        assertEquals("period 格式错误，应为 YYYY-MM", r.getMsg());
        assertNull(r.getData(),
                "失败响应 data 必须为 null（不是空 VO，避免下游误读 0 值）");
    }

}
