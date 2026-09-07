package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.constant.HttpStatus;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcFinanceTaxReportController} 单元测试 — W3.2
 *
 * <p>覆盖 3 个 endpoint：
 * <ul>
 *   <li>POST /generate — 参数校验 + SecurityUtils.getUsername() + service.generateMonthlyReport</li>
 *   <li>GET / — 参数校验 + service.listByCompany (period/status/limit)</li>
 *   <li>GET /{id} — service.getById + 不存在返回 error</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@link SecurityUtils#getUsername} 是 static（与 W2.5 FinanceCtrl 同模式，区别于 W2.6/W2.7 的 getUserId）</li>
 *   <li>Controller 在参数校验失败时返回 {@code error("msg")} → code=500（{@link HttpStatus#ERROR}）</li>
 *   <li>{@code @RequestParam(defaultValue = "20")} → Java 调用处必须显式传 20</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcFinanceTaxReportControllerTest {

    @Mock
    private IOpcFinanceTaxReportService taxReportService;

    @InjectMocks
    private OpcFinanceTaxReportController controller;

    private MockedStatic<SecurityUtils> securityMock;

    private static final Long COMPANY_ID = 1001L;
    private static final Long REPORT_ID = 8888L;
    private static final Long REPORT_PK_ID = 77L;
    private static final String PERIOD = "2026-09";
    private static final String USERNAME = "zhangsan";

    @BeforeEach
    void setupSecurityMock() {
        securityMock = mockStatic(SecurityUtils.class);
        securityMock.when(SecurityUtils::getUsername).thenReturn(USERNAME);
    }

    @AfterEach
    void teardownSecurityMock() {
        if (securityMock != null) securityMock.close();
    }

    private OpcFinanceTaxReport sampleReport() {
        OpcFinanceTaxReport r = new OpcFinanceTaxReport();
        r.setId(REPORT_PK_ID);
        r.setReportCode("TR20260907000001");
        r.setCompanyId(COMPANY_ID);
        r.setPeriod(PERIOD);
        r.setStatus("DRAFT");
        r.setTaxableAmount(new BigDecimal("12000.00"));
        r.setTaxAmount(new BigDecimal("780.00"));
        r.setPayAmount(new BigDecimal("780.00"));
        r.setPaidAmount(BigDecimal.ZERO);
        return r;
    }

    // ==================== POST /generate ====================

    @Test
    @DisplayName("generate — SecurityUtils.getUsername + service.generateMonthlyReport，返回 success({reportId})")
    void generate_returnsSuccessWithReportId() {
        when(taxReportService.generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME))
                .thenReturn(REPORT_ID);

        AjaxResult result = controller.generate(COMPANY_ID, PERIOD);

        assertEquals(HttpStatus.SUCCESS, result.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNotNull(data, "data map 不应为空");
        assertEquals(REPORT_ID, data.get("reportId"));
        verify(taxReportService).generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME);
        securityMock.verify(() -> SecurityUtils.getUsername(), atLeastOnce());
    }

    @Test
    @DisplayName("generate — companyId=null → error(\"companyId 和 period 不能为空\")，不调 service")
    void generate_companyIdNull_returnsError() {
        AjaxResult result = controller.generate(null, PERIOD);

        assertEquals(HttpStatus.ERROR, result.get("code"),
                "companyId=null 应返回 error（HTTP 500）");
        assertTrue(result.get("msg").toString().contains("companyId"),
                "错误信息应提及 companyId");
        verify(taxReportService, never()).generateMonthlyReport(any(), any(), any());
    }

    @Test
    @DisplayName("generate — period=null → error(\"companyId 和 period 不能为空\")，不调 service")
    void generate_periodNull_returnsError() {
        AjaxResult result = controller.generate(COMPANY_ID, null);

        assertEquals(HttpStatus.ERROR, result.get("code"),
                "period=null 应返回 error（HTTP 500）");
        assertTrue(result.get("msg").toString().contains("period"),
                "错误信息应提及 period");
        verify(taxReportService, never()).generateMonthlyReport(any(), any(), any());
    }

    @Test
    @DisplayName("generate — period=\"\"（空字符串）→ error，不调 service（W5.1 mutation fix M5）")
    void generate_periodEmptyString_returnsError() {
        AjaxResult result = controller.generate(COMPANY_ID, "");

        assertEquals(HttpStatus.ERROR, result.get("code"),
                "period=\"\" 应被 controller 拦截，不能透传到 service");
        assertTrue(result.get("msg").toString().contains("period"),
                "错误信息应提及 period");
        verify(taxReportService, never()).generateMonthlyReport(any(), any(), any());
    }

    @Test
    @DisplayName("generate — 完整 URL 透传：companyId + period + SecurityUtils.getUsername() 都传给 service")
    void generate_passesAllArgs() {
        when(taxReportService.generateMonthlyReport(any(), any(), any()))
                .thenReturn(REPORT_ID);

        controller.generate(COMPANY_ID, PERIOD);

        verify(taxReportService).generateMonthlyReport(eq(COMPANY_ID), eq(PERIOD), eq(USERNAME));
    }

    @Test
    @DisplayName("generate — service 抛 OpcException（公司不存在/期间格式错误）→ 透传不包装")
    void generate_serviceThrowsPropagates() {
        when(taxReportService.generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME))
                .thenThrow(new OpcException("公司不存在"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.generate(COMPANY_ID, PERIOD));
        assertTrue(ex.getMessage().contains("公司不存在"));
    }

    // ==================== GET / ====================

    @Test
    @DisplayName("list — 透传 companyId/period/status/limit 到 service.listByCompany，返回 success(List)")
    void list_returnsSuccessWithList() {
        List<OpcFinanceTaxReport> mockList = Arrays.asList(sampleReport(), sampleReport());
        when(taxReportService.listByCompany(COMPANY_ID, PERIOD, "DRAFT", 20))
                .thenReturn(mockList);

        AjaxResult result = controller.list(COMPANY_ID, PERIOD, "DRAFT", 20);

        assertEquals(HttpStatus.SUCCESS, result.get("code"));
        assertSame(mockList, result.get("data"));
        verify(taxReportService).listByCompany(COMPANY_ID, PERIOD, "DRAFT", 20);
    }

    @Test
    @DisplayName("list — period 和 status 为 null 也能透传（不过滤）")
    void list_nullFiltersPassThrough() {
        when(taxReportService.listByCompany(COMPANY_ID, null, null, 20))
                .thenReturn(new ArrayList<>());

        AjaxResult result = controller.list(COMPANY_ID, null, null, 20);

        assertEquals(HttpStatus.SUCCESS, result.get("code"));
        verify(taxReportService).listByCompany(COMPANY_ID, null, null, 20);
    }

    @Test
    @DisplayName("list — 默认 limit=20（@RequestParam defaultValue=\"20\"）")
    void list_defaultLimitIs20() {
        when(taxReportService.listByCompany(COMPANY_ID, null, null, 20))
                .thenReturn(new ArrayList<>());

        AjaxResult result = controller.list(COMPANY_ID, null, null, 20);

        assertEquals(HttpStatus.SUCCESS, result.get("code"));
        verify(taxReportService).listByCompany(COMPANY_ID, null, null, 20);
    }

    @Test
    @DisplayName("list — 自定义 limit=50 也透传")
    void list_customLimitPassesThrough() {
        when(taxReportService.listByCompany(COMPANY_ID, "2026-08", "SUBMITTED", 50))
                .thenReturn(new ArrayList<>());

        controller.list(COMPANY_ID, "2026-08", "SUBMITTED", 50);

        verify(taxReportService).listByCompany(COMPANY_ID, "2026-08", "SUBMITTED", 50);
    }

    @Test
    @DisplayName("list — companyId=null → error(\"companyId 不能为空\")，不调 service")
    void list_companyIdNull_returnsError() {
        AjaxResult result = controller.list(null, null, null, 20);

        assertEquals(HttpStatus.ERROR, result.get("code"),
                "companyId=null 应返回 error（HTTP 500）");
        assertTrue(result.get("msg").toString().contains("companyId"));
        verify(taxReportService, never()).listByCompany(any(), any(), any(), any());
    }

    @Test
    @DisplayName("list — 空列表也能正常返回 success([])")
    void list_emptyList() {
        when(taxReportService.listByCompany(COMPANY_ID, null, null, 20))
                .thenReturn(new ArrayList<>());

        AjaxResult result = controller.list(COMPANY_ID, null, null, 20);

        assertEquals(HttpStatus.SUCCESS, result.get("code"));
        assertNotNull(result.get("data"));
        assertEquals(0, ((List<?>) result.get("data")).size());
    }

    // ==================== GET /{id} ====================

    @Test
    @DisplayName("detail — 透传 id 到 service.getById，返回 success(Report)")
    void detail_returnsReport() {
        OpcFinanceTaxReport r = sampleReport();
        when(taxReportService.getById(REPORT_PK_ID)).thenReturn(r);

        AjaxResult result = controller.detail(REPORT_PK_ID);

        assertEquals(HttpStatus.SUCCESS, result.get("code"));
        assertSame(r, result.get("data"));
        verify(taxReportService).getById(REPORT_PK_ID);
    }

    @Test
    @DisplayName("detail — service 返回 null → error(\"报表不存在\")，HTTP 500")
    void detail_notFound_returnsError() {
        when(taxReportService.getById(999L)).thenReturn(null);

        AjaxResult result = controller.detail(999L);

        assertEquals(HttpStatus.ERROR, result.get("code"),
                "找不到报表应返回 error（HTTP 500）");
        assertEquals("报表不存在", result.get("msg"));
        verify(taxReportService).getById(999L);
    }
}