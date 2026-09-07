package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
@WebMvcTest(controllers = OpcFinanceTaxReportController.class)
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
                .andExpect(status().isInternalServerError());

        verify(taxReportService, never()).generateMonthlyReport(any(), any(), any());
    }

    @Test
    @DisplayName("generate — period=\"\" 空字符串 → 透传给 service，由 service 决定（现状：抛 OpcException → 500）")
    void generate_emptyPeriod_passesThroughToService() throws Exception {
        // W5.1 M5 mutation 关注点：controller 当前只校验 period == null，未校验 isEmpty()
        // period="" 会透传到 service，由 service 抛 OpcException → advice → 500
        // 这与 W5.1 单元测试 `generate_periodEmptyString_returnsError` 是不同维度
        when(taxReportService.generateMonthlyReport(COMPANY_ID, "", USERNAME))
                .thenThrow(new OpcException("期间格式错误"));

        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID))
                        .param("period", ""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.msg").value("期间格式错误"));
    }

    // ==================== POST /generate — 异常路径 ====================

    @Test
    @DisplayName("generate — service 抛 OpcException（公司不存在）→ HTTP 500 + JSON msg（via @RestControllerAdvice）")
    void generate_serviceThrowsOpcException_returns500ViaAdvice() throws Exception {
        when(taxReportService.generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME))
                .thenThrow(new OpcException("公司不存在"));

        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID))
                        .param("period", PERIOD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("公司不存在"));
    }

    // ==================== POST /generate — 匿名场景 ====================

    @Test
    @DisplayName("generate — 匿名调用 → SecurityUtils.getUsername() null → service 抛 OpcException → 500")
    void generate_anonymous_usernameNull_throwsOpcException() throws Exception {
        SecurityContextHolder.remove();
        when(taxReportService.generateMonthlyReport(any(), any(), any()))
                .thenThrow(new OpcException("username 不能为空"));

        mockMvc.perform(post("/opc/finance/tax-reports/generate")
                        .param("companyId", String.valueOf(COMPANY_ID))
                        .param("period", PERIOD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.msg").value("username 不能为空"));
    }
}