package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcFinanceController} 单元测试 — W2.5
 *
 * <p>覆盖 11 个 endpoint（8 voucher + 3 flow/agent）：
 * <ul>
 *   <li>GET /vouchers — 列表查询（4 参数透传）</li>
 *   <li>GET /voucher/{id} — 详情</li>
 *   <li>POST /voucher — 创建（返回 {voucherId}）</li>
 *   <li>PUT /voucher — 更新（rows>0 → true / rows==0 → false）</li>
 *   <li>POST /voucher/{id}/review-pass — 审核通过（SecurityUtils.getUsername）</li>
 *   <li>POST /voucher/{id}/review-reject — 审核拒绝（opinion 可选）</li>
 *   <li>POST /voucher/{id}/post — 入账（service 异常透传）</li>
 *   <li>POST /flows/upload — 上传银行流水（SecurityUtils.getUsername）</li>
 *   <li>GET /flows/pending — 待处理流水</li>
 *   <li>POST /flows/extract — AI 提取异步入口（不调 service）</li>
 *   <li>POST /daily-report — 日报异步入口（不调 service）</li>
 * </ul>
 *
 * <p>关键模式：{@link SecurityUtils} 是静态方法，需要 {@link Mockito#mockStatic(Class)}。
 * 每个 @BeforeEach 都启动 mockStatic 默认返回 "alice"，@AfterEach 关闭，确保不影响其他测试。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcFinanceControllerTest {

    @Mock
    private IOpcFinanceVoucherService voucherService;

    @Mock
    private IOpcFinanceBankFlowService bankFlowService;

    @InjectMocks
    private OpcFinanceController controller;

    private MockedStatic<SecurityUtils> securityMock;

    private static final Long COMPANY_ID = 1001L;
    private static final Long VOUCHER_ID = 7L;
    private static final String USERNAME = "alice";

    @BeforeEach
    void setupSecurityMock() {
        securityMock = mockStatic(SecurityUtils.class);
        securityMock.when(SecurityUtils::getUsername).thenReturn(USERNAME);
    }

    @AfterEach
    void teardownSecurityMock() {
        if (securityMock != null) securityMock.close();
    }

    private OpcFinanceVoucher sampleVoucher() {
        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(VOUCHER_ID);
        v.setCompanyId(COMPANY_ID);
        v.setPeriod("2026-09");
        v.setSummary("支付供应商货款");
        v.setTotalDebit(new BigDecimal("1000.00"));
        v.setTotalCredit(new BigDecimal("1000.00"));
        v.setStatus("DRAFT");
        v.setCreateBy("spoofed-user");  // W8: 客户端伪造值，验证 controller 必须 override
        return v;
    }

    private OpcFinanceBankFlow sampleFlow() {
        OpcFinanceBankFlow f = new OpcFinanceBankFlow();
        f.setId(99L);
        f.setCompanyId(COMPANY_ID);
        f.setBankAccount("6225880101234567");
        f.setAmount(new BigDecimal("1000.00"));
        f.setDirection("IN");
        f.setStatus("IMPORTED");
        return f;
    }

    // ==================== GET /vouchers ====================

    @Test
    @DisplayName("listVouchers — 4 参数透传给 service + 返回 success(List)")
    void listVouchers_passesAllArgs() {
        List<OpcFinanceVoucher> mockList = Arrays.asList(sampleVoucher(), sampleVoucher());
        when(voucherService.listByCompany(COMPANY_ID, "2026-09", "POSTED", 50))
                .thenReturn(mockList);

        AjaxResult result = controller.listVouchers(COMPANY_ID, "2026-09", "POSTED", 50);

        assertEquals(200, result.get("code"), "应返回 200 成功码");
        assertSame(mockList, result.get("data"));
        verify(voucherService).listByCompany(COMPANY_ID, "2026-09", "POSTED", 50);
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("listVouchers — period/status=null 也能透传")
    void listVouchers_nullFiltersPassThrough() {
        when(voucherService.listByCompany(COMPANY_ID, null, null, 20))
                .thenReturn(new ArrayList<>());

        AjaxResult result = controller.listVouchers(COMPANY_ID, null, null, 20);

        assertEquals(200, result.get("code"));
        verify(voucherService).listByCompany(COMPANY_ID, null, null, 20);
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== GET /voucher/{id} ====================

    @Test
    @DisplayName("voucherDetail — 透传 id，返回 success(voucher)")
    void voucherDetail_returnsVoucher() {
        OpcFinanceVoucher v = sampleVoucher();
        when(voucherService.getById(VOUCHER_ID)).thenReturn(v);

        AjaxResult result = controller.voucherDetail(VOUCHER_ID);

        assertEquals(200, result.get("code"));
        assertSame(v, result.get("data"));
        verify(voucherService).getById(VOUCHER_ID);
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== POST /voucher ====================

    @Test
    @DisplayName("createVoucher — service.create 返回 id → 包装为 {voucherId: id}")
    void createVoucher_returnsIdInMap() {
        OpcFinanceVoucher v = sampleVoucher();
        when(voucherService.create(v)).thenReturn(123L);

        AjaxResult result = controller.createVoucher(v);

        assertEquals(200, result.get("code"));
        assertEquals(123L, result.get("voucherId"),
                "data.voucherId 应等于 service.create 返回值");
        assertEquals(USERNAME, v.getCreateBy(),
                "W8 修复：controller 必须 override createBy = SecurityUtils.getUsername()");
        verify(voucherService).create(v);
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("createVoucher — service.create 返回 null（如 mapper 异常）→ data.voucherId=null 不抛")
    void createVoucher_nullIdReturned() {
        OpcFinanceVoucher v = sampleVoucher();
        when(voucherService.create(v)).thenReturn(null);

        AjaxResult result = controller.createVoucher(v);

        assertEquals(200, result.get("code"));
        assertNull(result.get("voucherId"));
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== PUT /voucher ====================

    @Test
    @DisplayName("updateVoucher — rows > 0 → 返回 success(true)")
    void updateVoucher_rowsGreaterThanZero() {
        OpcFinanceVoucher v = sampleVoucher();
        when(voucherService.update(v)).thenReturn(1);

        AjaxResult result = controller.updateVoucher(v);

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.TRUE, result.get("data"),
                "rows > 0 → data 应为 true");
        assertEquals(USERNAME, v.getUpdateBy(),
                "W10.3 修复：controller 必须 override updateBy = SecurityUtils.getUsername()");
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("updateVoucher — rows == 0 → 返回 success(false)（不是 error）")
    void updateVoucher_rowsZero() {
        OpcFinanceVoucher v = sampleVoucher();
        when(voucherService.update(v)).thenReturn(0);

        AjaxResult result = controller.updateVoucher(v);

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.FALSE, result.get("data"),
                "rows == 0 → data 应为 false（controller 不主动 error）");
        assertEquals(USERNAME, v.getUpdateBy(),
                "即使 update 失败（rows==0），controller 仍必须 override updateBy（防止伪造身份）");
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("updateVoucher — W10.3 修复：override updateBy（对称 W8.1 createBy 修复），防客户端伪造身份")
    void updateVoucher_overridesUpdateByFromSecurityContext() {
        OpcFinanceVoucher v = sampleVoucher();
        v.setUpdateBy("spoofed-user");  // 客户端伪造值
        when(voucherService.update(v)).thenReturn(1);

        controller.updateVoucher(v);

        assertEquals(USERNAME, v.getUpdateBy(),
                "W10.3 修复：updateBy 必须被 override 为 SecurityUtils.getUsername()（对称 W8.1 createBy 修复）");
        securityMock.verify(() -> SecurityUtils.getUsername(), atLeastOnce());
    }

    // ==================== POST /voucher/{id}/review-pass ====================

    @Test
    @DisplayName("reviewPass — rows > 0 → 返回 true，且 SecurityUtils.getUsername 被调")
    void reviewPass_success() {
        when(voucherService.reviewPass(eq(VOUCHER_ID), eq(USERNAME))).thenReturn(1);

        AjaxResult result = controller.reviewPass(VOUCHER_ID);

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.TRUE, result.get("data"));
        verify(voucherService).reviewPass(VOUCHER_ID, USERNAME);
        securityMock.verify(() -> SecurityUtils.getUsername(), atLeastOnce());
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("reviewPass — rows == 0 → 返回 false（service 端 reject 后业务失败）")
    void reviewPass_rowsZero() {
        when(voucherService.reviewPass(VOUCHER_ID, USERNAME)).thenReturn(0);

        AjaxResult result = controller.reviewPass(VOUCHER_ID);

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.FALSE, result.get("data"));
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== POST /voucher/{id}/review-reject ====================

    @Test
    @DisplayName("reviewReject — opinion 有值 → 透传给 service")
    void reviewReject_withOpinion() {
        when(voucherService.reviewReject(VOUCHER_ID, USERNAME, "凭证借贷不平衡"))
                .thenReturn(1);

        AjaxResult result = controller.reviewReject(VOUCHER_ID, "凭证借贷不平衡");

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.TRUE, result.get("data"));
        verify(voucherService).reviewReject(VOUCHER_ID, USERNAME, "凭证借贷不平衡");
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("reviewReject — opinion 为 null 也能透传")
    void reviewReject_nullOpinion() {
        when(voucherService.reviewReject(VOUCHER_ID, USERNAME, null)).thenReturn(1);

        AjaxResult result = controller.reviewReject(VOUCHER_ID, null);

        assertEquals(200, result.get("code"));
        verify(voucherService).reviewReject(VOUCHER_ID, USERNAME, null);
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("reviewReject — rows == 0 → 返回 false")
    void reviewReject_rowsZero() {
        when(voucherService.reviewReject(anyLong(), anyString(), any())).thenReturn(0);

        AjaxResult result = controller.reviewReject(VOUCHER_ID, "opinion");

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.FALSE, result.get("data"));
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== POST /voucher/{id}/post ====================

    @Test
    @DisplayName("post — rows > 0 → 返回 true")
    void post_success() {
        when(voucherService.post(VOUCHER_ID, USERNAME)).thenReturn(1);

        AjaxResult result = controller.post(VOUCHER_ID);

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.TRUE, result.get("data"));
        verify(voucherService).post(VOUCHER_ID, USERNAME);
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("post — rows == 0（如 voucher 不存在或状态非法）→ 返回 false")
    void post_rowsZero() {
        when(voucherService.post(VOUCHER_ID, USERNAME)).thenReturn(0);

        AjaxResult result = controller.post(VOUCHER_ID);

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.FALSE, result.get("data"));
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("post — service 抛 OpcException（状态机非法） → 异常透传，controller 不吞")
    void post_serviceThrowsPropagates() {
        when(voucherService.post(VOUCHER_ID, USERNAME))
                .thenThrow(new OpcException("凭证未通过审核，不能入账"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.post(VOUCHER_ID));
        assertTrue(ex.getMessage().contains("未通过审核"));
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== POST /flows/upload ====================

    @Test
    @DisplayName("uploadFlows — 正常：service.uploadBatch 写入 N 条 → 返回 success(N)")
    void uploadFlows_returnsCount() {
        List<OpcFinanceBankFlow> flows = Arrays.asList(sampleFlow(), sampleFlow(), sampleFlow());
        when(bankFlowService.uploadBatch(flows, USERNAME)).thenReturn(3);

        AjaxResult result = controller.uploadFlows(flows);

        assertEquals(200, result.get("code"));
        assertEquals(3, result.get("data"));
        verify(bankFlowService).uploadBatch(flows, USERNAME);
        securityMock.verify(() -> SecurityUtils.getUsername(), atLeastOnce());
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    @Test
    @DisplayName("uploadFlows — service 返回 0（空集合） → 返回 success(0)")
    void uploadFlows_returnsZero() {
        when(bankFlowService.uploadBatch(anyList(), anyString())).thenReturn(0);

        AjaxResult result = controller.uploadFlows(new ArrayList<>());

        assertEquals(200, result.get("code"));
        assertEquals(0, result.get("data"));
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== GET /flows/pending ====================

    @Test
    @DisplayName("pendingFlows — 透传 companyId + limit，返回 success(List)")
    void pendingFlows_passesArgs() {
        List<OpcFinanceBankFlow> mockList = Arrays.asList(sampleFlow(), sampleFlow());
        when(bankFlowService.listPending(COMPANY_ID, 30)).thenReturn(mockList);

        AjaxResult result = controller.pendingFlows(COMPANY_ID, 30);

        assertEquals(200, result.get("code"));
        assertSame(mockList, result.get("data"));
        verify(bankFlowService).listPending(COMPANY_ID, 30);
        verifyNoMoreInteractions(voucherService, bankFlowService);
    }

    // ==================== POST /flows/extract ====================

    @Test
    @DisplayName("extractFlows — 不调任何 service，返回 success({taskCode, date})")
    void extractFlows_returnsTaskCodeWithoutService() {
        AjaxResult result = controller.extractFlows(COMPANY_ID);

        assertEquals(200, result.get("code"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNotNull(data);
        assertNotNull(data.get("taskCode"),
                "taskCode 应非 null（同步生成的占位 taskCode）");
        assertTrue(data.get("taskCode").toString().startsWith("EXTRACT-"),
                "taskCode 应以 EXTRACT- 开头，实际: " + data.get("taskCode"));
        assertEquals(LocalDate.now().toString(), data.get("date"));
        // 验证不调任何 service
        verifyNoInteractions(voucherService, bankFlowService);
    }

    // ==================== POST /daily-report ====================

    @Test
    @DisplayName("dailyReport — 不调任何 service，返回 success({taskCode: 'DAILY-...', date: today})")
    void dailyReport_returnsTaskCodeAndDateWithoutService() {
        AjaxResult result = controller.dailyReport(COMPANY_ID);

        assertEquals(200, result.get("code"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNotNull(data);
        assertNotNull(data.get("taskCode"));
        assertTrue(data.get("taskCode").toString().startsWith("DAILY-"),
                "taskCode 应以 DAILY- 开头");
        assertEquals(LocalDate.now().toString(), data.get("date"));
        verifyNoInteractions(voucherService, bankFlowService);
    }
}
