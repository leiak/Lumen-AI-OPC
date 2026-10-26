package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.mapper.OpcFinanceBankFlowMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceTaxReportMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceTokenUsageMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceVoucherMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OpcFinanceTaxReportController} 集成测试（@WebMvcTest）— W6.4
 *
 * <p>覆盖维度：
 * <ul>
 *   <li><b>HTTP status</b>：200 正常 + 500 校验失败（业务校验）+ 500 service 异常 + 500 service 异常透传</li>
 *   <li><b>JSON 序列化</b>：{@code $.data.reportId} Long 字段</li>
 *   <li><b>401 鉴权</b>：匿名 → SecurityUtils.getUsername() null → service 抛 OpcException</li>
 *   <li><b>@RestControllerAdvice</b>：业务校验 error + OpcException 透传两种异常路径</li>
 * </ul>
 *
 * <p>本 controller 关键设计：{@code generate} 内部做参数校验（{@code period == null} → error），
 * 与 W6.1 不同的是「业务校验失败」是 HTTP 200 + JSON code=500（{@link HttpStatus#ERROR}），
 * 而非 HTTP 500。这是 OPC 业务约定。
 *
 * @author OAC
 */
@WebMvcTest(controllers = OpcFinanceTaxReportController.class,
        // W11.3 (M4 Task 1 I3 修复): 改用 REGEX 排除整个 mapper 包，避免每次新增 mapper
        // 都要在这里 hard-code 一行 FQN。@MapperScan("com.ruoyi.**.mapper")
        // 会把 mapper 作为 bean 注册 → @WebMvcTest 没有 sqlSessionFactory → 启动失败。
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.ruoyi\\.opc\\.finance\\.mapper\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
class OpcFinanceTaxReportControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IOpcFinanceTaxReportService taxReportService;

    // W11.1 (M4 Task 1): @EnableCustomConfig → @MapperScan("com.ruoyi.**.mapper")
    // 会把所有 mapper 注册为 bean，@WebMvcTest 不启 MyBatis → 启动失败。
    // 全部 @MockBean 屏蔽（service 层用不到 mapper）。
    @MockBean
    private OpcFinanceBankFlowMapper bankFlowMapper;

    @MockBean
    private OpcFinanceTaxReportMapper taxReportMapper;

    @MockBean
    private OpcFinanceTokenUsageMapper tokenUsageMapper;

    @MockBean
    private OpcFinanceVoucherMapper voucherMapper;

    private static final Long COMPANY_ID = 1001L;
    private static final String PERIOD = "2026-09";
    private static final Long REPORT_ID = 8888L;
    private static final String USERNAME = "zhangsan";

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.setUserName(USERNAME);
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.remove();
    }

    // ==================== POST /generate — 正常路径 ====================

    @Test
    @DisplayName("generate — 已登录 + service 返回 reportId → HTTP 200 + JSON data.reportId")
    void generate_returns200WithReportId() throws Exception {
        when(taxReportService.generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME))
                .thenReturn(REPORT_ID);

        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID))
                        .param("period", PERIOD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reportId").value(REPORT_ID));

        verify(taxReportService).generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME);
    }

    // ==================== POST /generate — 业务校验 ====================

    @Test
    @DisplayName("generate — period 缺省 → HTTP 200 + JSON code=500 (业务校验 error，不调 service)")
    void generate_missingPeriod_returns500WithValidationError() throws Exception {
        // 注意：缺省 @RequestParam 会抛 MissingServletRequestParameterException → advice
        // 而非 controller 内部 if (period == null) 校验
        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        verify(taxReportService, never()).generateMonthlyReport(any(), any(), any());
    }

    @Test
    @DisplayName("generate — period=\"\" 空字符串 → controller 直接拦（HTTP 200 + code=500），不透传给 service")
    void generate_emptyPeriod_rejectedByController() throws Exception {
        // W5.1 M5：controller 现在同时校验 period == null 与 isBlank(period) ——
        // 空 period 会生成一份"所属期为空"的垃圾报表，在 controller 层就挡住。
        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID))
                        .param("period", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("companyId 和 period 不能为空"));

        verify(taxReportService, never()).generateMonthlyReport(any(), any(), any());
    }

    // ==================== POST /generate — 异常路径 ====================

    @Test
    @DisplayName("generate — service 抛 OpcException（公司不存在）→ HTTP 200 + JSON code=500 + msg（业务错误约定）")
    void generate_serviceThrowsOpcException_returns200WithCode500() throws Exception {
        when(taxReportService.generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME))
                .thenThrow(new OpcException("公司不存在"));

        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID))
                        .param("period", PERIOD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("公司不存在"));
    }

    // ==================== POST /generate — 匿名场景 ====================

    @Test
    @DisplayName("generate — 匿名调用 → SecurityUtils.getUsername() null → service 抛 OpcException → HTTP 200 + code=500")
    void generate_anonymous_usernameNull_throwsOpcException() throws Exception {
        SecurityContextHolder.remove();
        when(taxReportService.generateMonthlyReport(any(), any(), any()))
                .thenThrow(new OpcException("username 不能为空"));

        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID))
                        .param("period", PERIOD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("username 不能为空"));
    }
}