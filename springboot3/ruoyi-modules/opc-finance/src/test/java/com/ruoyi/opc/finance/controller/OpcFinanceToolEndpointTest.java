package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.finance.mapper.OpcFinanceBankFlowMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceTaxReportMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceTokenUsageMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceVoucherMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
import com.ruoyi.opc.finance.service.IOpcFinanceTokenUsageService;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
import com.ruoyi.opc.finance.vo.FlowRangeVo;
import com.ruoyi.opc.finance.vo.VoucherRangeVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OpcFinanceController} 的两个「工具端点」集成测试（@WebMvcTest）
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code GET /opc/finance/tools/flows}    → {@code R<FlowRangeVo>}（数字员工取真流水）</li>
 *   <li>{@code GET /opc/finance/tools/vouchers} → {@code R<VoucherRangeVo>}（数字员工取真凭证）</li>
 * </ul>
 *
 * <p><b>为什么断言 body.code 而不是 HTTP 状态码</b>：RuoYi 的业务错误一律
 * HTTP 200 + {@code body.code != 200}（见 CLAUDE.md 坑 #8）——缺参数、
 * 业务异常都长这样。所以「不传 companyId」必须断言 HTTP 仍是 200、
 * 但 {@code body.code != 200}。
 *
 * <p>测试用的区间数据是演示库实测值（company 2）：6 条流水 / 5 张凭证。
 *
 * @author OAC
 */
@WebMvcTest(controllers = OpcFinanceController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.ruoyi\\.opc\\.finance\\.mapper\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
class OpcFinanceToolEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IOpcFinanceVoucherService voucherService;

    @MockBean
    private IOpcFinanceBankFlowService bankFlowService;

    @MockBean
    private IOpcFinanceTaxReportService taxReportService;

    @MockBean
    private IOpcFinanceTokenUsageService tokenUsageService;

    // 切片里没有 DataSource/SqlSessionFactory，mapper 一律占位（不会被本测试调到）
    @MockBean
    private OpcFinanceBankFlowMapper bankFlowMapper;

    @MockBean
    private OpcFinanceTaxReportMapper taxReportMapper;

    @MockBean
    private OpcFinanceTokenUsageMapper tokenUsageMapper;

    @MockBean
    private OpcFinanceVoucherMapper voucherMapper;

    private static final Long COMPANY_ID = 2L;

    @Test
    @DisplayName("toolFlows — 返回 R<FlowRangeVo>：count=6、totalIn=51892.00")
    void toolFlows_returnsRangeVo() throws Exception {
        FlowRangeVo vo = FlowRangeVo.builder()
                .companyId(COMPANY_ID)
                .fromDate("2026-10-07")
                .toDate("2026-11-01")
                .count(6)
                .totalIn(new BigDecimal("51892.00"))
                .totalOut(new BigDecimal("32430.00"))
                .net(new BigDecimal("19462.00"))
                .yesterdayCount(0)
                .yesterdayIn(BigDecimal.ZERO)
                .yesterdayOut(BigDecimal.ZERO)
                .items(List.of())
                .build();
        when(bankFlowService.queryRange(COMPANY_ID, null, null)).thenReturn(vo);

        mockMvc.perform(get("/opc/finance/tools/flows").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").value(6))
                .andExpect(jsonPath("$.data.totalIn").value(51892.00))
                .andExpect(jsonPath("$.data.totalOut").value(32430.00))
                .andExpect(jsonPath("$.data.net").value(19462.00))
                .andExpect(jsonPath("$.data.fromDate").value("2026-10-07"))
                .andExpect(jsonPath("$.data.toDate").value("2026-11-01"));

        verify(bankFlowService).queryRange(COMPANY_ID, null, null);
    }

    @Test
    @DisplayName("toolFlows — 不传 companyId → HTTP 200 + body.code != 200（不是 HTTP 500）")
    void toolFlows_requiresCompanyId() throws Exception {
        mockMvc.perform(get("/opc/finance/tools/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        // 请求根本没进 controller —— service 一次都不能被调
        verify(bankFlowService, never()).queryRange(any(), any(), any());
    }

    @Test
    @DisplayName("toolVouchers — 返回 R<VoucherRangeVo>：totalIn=35400.00（贷方口径）")
    void toolVouchers_returnsRangeVo() throws Exception {
        VoucherRangeVo vo = VoucherRangeVo.builder()
                .companyId(COMPANY_ID)
                .fromDate("2026-10-07")
                .toDate("2026-11-01")
                .count(5)
                .totalIn(new BigDecimal("35400.00"))
                .totalOut(new BigDecimal("29750.00"))
                .net(new BigDecimal("5650.00"))
                .yesterdayCount(0)
                .yesterdayIn(BigDecimal.ZERO)
                .yesterdayOut(BigDecimal.ZERO)
                .items(List.of())
                .build();
        when(voucherService.queryRange(eq(COMPANY_ID), isNull(), isNull())).thenReturn(vo);

        mockMvc.perform(get("/opc/finance/tools/vouchers").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").value(5))
                .andExpect(jsonPath("$.data.totalIn").value(35400.00))
                .andExpect(jsonPath("$.data.totalOut").value(29750.00));

        verify(voucherService).queryRange(COMPANY_ID, null, null);
    }

}
