package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.hamcrest.Matchers.nullValue;
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
 * <p><b>关于时区</b>：容器 JVM 是 UTC，生产上 {@code ApplicationConfig} 把 ObjectMapper
 * 的时区设成 {@code TimeZone.getDefault()}（= UTC）。本切片没引入 ApplicationConfig，
 * ObjectMapper 用 Jackson 默认的 UTC —— 两者一样，所以渲染口径与生产一致，
 * 而且不依赖开发机的时区（{@code @BeforeEach} 里把 JVM 默认时区钉成 UTC）。
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

    /** 进入测试前的 JVM 默认时区，{@code @AfterEach} 里还原 */
    private TimeZone originalTimeZone;

    @BeforeEach
    void pinDefaultTimeZoneToUtc() {
        // 容器 JVM 就是 UTC。把测试 JVM 的默认时区也钉成 UTC ——
        // 「tradeTime 渲染成 UTC 还是北京时间」这件事只有在 UTC 下才分得出来。
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreDefaultTimeZone() {
        // 全局状态必须还原 —— 否则会污染同 JVM 里的其他测试类
        TimeZone.setDefault(originalTimeZone);
    }

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

    @Test
    @DisplayName("toolFlows — service 抛 OpcException → HTTP 200 + body.code != 200 + 响应体仍是 R 形状")
    void toolFlows_serviceThrowsOpcException_returnsRShapedError() throws Exception {
        when(bankFlowService.queryRange(eq(COMPANY_ID), isNull(), isNull()))
                .thenThrow(new OpcException("fromDate 不能晚于 toDate"));

        mockMvc.perform(get("/opc/finance/tools/flows").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("fromDate 不能晚于 toDate"))
                // R 形状的判据：R 有 data 字段（这里是 null）；全局 advice 的 AjaxResult 压根没有 data 键
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("toolFlows — items[].tradeTime 按北京时间渲染（不是容器 JVM 的 UTC）")
    void toolFlows_tradeTimeRenderedInBeijingTime() throws Exception {
        // 库里存的是北京时间「2026-10-07 12:49:03」；驱动按 serverTimezone=Asia/Shanghai
        // 解读，得到的就是这个瞬间（= UTC 04:49:03）。渲染时必须还原成北京时间，
        // 否则接口吐出 04:49:03 —— 和库里对不上，凌晨的记录连日期都会差一天。
        OpcFinanceBankFlow flow = new OpcFinanceBankFlow();
        flow.setId(27L);
        flow.setCompanyId(COMPANY_ID);
        flow.setTradeTime(Date.from(Instant.parse("2026-10-07T04:49:03Z")));
        flow.setDirection("OUT");
        flow.setAmount(new BigDecimal("13200.00"));

        FlowRangeVo vo = FlowRangeVo.builder()
                .companyId(COMPANY_ID)
                .fromDate("2026-10-07")
                .toDate("2026-11-01")
                .count(1)
                .totalIn(BigDecimal.ZERO)
                .totalOut(new BigDecimal("13200.00"))
                .net(new BigDecimal("-13200.00"))
                .yesterdayCount(0)
                .yesterdayIn(BigDecimal.ZERO)
                .yesterdayOut(BigDecimal.ZERO)
                .items(List.of(flow))
                .build();
        when(bankFlowService.queryRange(eq(COMPANY_ID), isNull(), isNull())).thenReturn(vo);

        mockMvc.perform(get("/opc/finance/tools/flows").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].tradeTime").value("2026-10-07 12:49:03"));
    }

}
