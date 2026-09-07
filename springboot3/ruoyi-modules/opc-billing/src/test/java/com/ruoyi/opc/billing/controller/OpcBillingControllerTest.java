package com.ruoyi.opc.billing.controller;

import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.billing.controller.OpcBillingController.RechargeRequest;
import com.ruoyi.opc.billing.domain.OpcBillingOrder;
import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.mapper.OpcBillingOrderMapper;
import com.ruoyi.opc.billing.service.IOpcWalletService;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * {@link OpcBillingController} 单元测试 — W2.6
 *
 * <p>覆盖 4 个 endpoint：
 * <ul>
 *   <li>GET /wallet — SecurityUtils.getUserId + walletService.getOrCreate 透传</li>
 *   <li>POST /wallet/recharge — 业务编排（建单 → markPaid → walletService.recharge），含 payMethod 默认 ALIPAY</li>
 *   <li>GET /orders — orderMapper.selectByCompany 透传</li>
 *   <li>GET /order/{orderNo} — orderMapper.selectByOrderNo 透传</li>
 * </ul>
 *
 * <p>关键模式：{@link SecurityUtils#getUserId} 是 static — 用 mockStatic。本 controller 与
 * {@code OpcFinanceController}（W2.5）区别：调的是 {@code getUserId} 而非 {@code getUsername}。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcBillingControllerTest {

    @Mock
    private IOpcWalletService walletService;

    @Mock
    private OpcBillingOrderMapper orderMapper;

    @InjectMocks
    private OpcBillingController controller;

    private MockedStatic<SecurityUtils> securityMock;

    private static final Long COMPANY_ID = 1001L;
    private static final Long USER_ID = 2002L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @BeforeEach
    void setupSecurityMock() {
        securityMock = mockStatic(SecurityUtils.class);
        securityMock.when(SecurityUtils::getUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void teardownSecurityMock() {
        if (securityMock != null) securityMock.close();
    }

    private OpcWallet sampleWallet() {
        OpcWallet w = new OpcWallet();
        w.setId(7L);
        w.setCompanyId(COMPANY_ID);
        w.setUserId(USER_ID);
        w.setBalance(new BigDecimal("100.00"));
        w.setStatus("ACTIVE");
        return w;
    }

    private OpcBillingOrder sampleOrder() {
        OpcBillingOrder o = new OpcBillingOrder();
        o.setId(99L);
        o.setOrderNo("O20260907ABCDEF");
        o.setCompanyId(COMPANY_ID);
        o.setUserId(USER_ID);
        o.setOrderType("RECHARGE");
        o.setAmount(AMOUNT);
        o.setPayStatus("PAID");
        return o;
    }

    private RechargeRequest rechargeRequest() {
        RechargeRequest req = new RechargeRequest();
        req.companyId = COMPANY_ID;
        req.amount = AMOUNT;
        return req;
    }

    // ==================== GET /wallet ====================

    @Test
    @DisplayName("wallet — SecurityUtils.getUserId + walletService.getOrCreate 透传，返回 success(wallet)")
    void wallet_returnsFromService() {
        OpcWallet w = sampleWallet();
        when(walletService.getOrCreate(COMPANY_ID, USER_ID)).thenReturn(w);

        AjaxResult result = controller.wallet(COMPANY_ID);

        assertEquals(200, result.get("code"));
        assertSame(w, result.get("data"));
        verify(walletService).getOrCreate(COMPANY_ID, USER_ID);
        securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce());
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("wallet — service 返回 null → success(null)，不抛")
    void wallet_nullWallet() {
        when(walletService.getOrCreate(COMPANY_ID, USER_ID)).thenReturn(null);

        AjaxResult result = controller.wallet(COMPANY_ID);

        assertEquals(200, result.get("code"));
        assertNull(result.get("data"));
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    // ==================== POST /wallet/recharge ====================

    @Test
    @DisplayName("recharge — 写入 8 字段到 order：orderNo/orderType=TITLE/amount/discount=0/paidAmount/payMethod/payStatus/createBy")
    void recharge_setsAllFieldsOnOrder() {
        RechargeRequest req = rechargeRequest();
        req.payMethod = "WECHAT";

        when(walletService.recharge(anyLong(), anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        controller.recharge(req);

        ArgumentCaptor<OpcBillingOrder> captor = ArgumentCaptor.forClass(OpcBillingOrder.class);
        verify(orderMapper).insert(captor.capture());
        OpcBillingOrder order = captor.getValue();
        assertNotNull(order.getOrderNo(), "orderNo 应被自动生成");
        assertTrue(order.getOrderNo().startsWith("O"),
                "orderNo 应以 O 开头，实际: " + order.getOrderNo());
        assertEquals(COMPANY_ID, order.getCompanyId());
        assertEquals(USER_ID, order.getUserId());
        assertEquals("RECHARGE", order.getOrderType());
        assertEquals("钱包充值 ¥" + AMOUNT, order.getTitle());
        assertEquals(0, order.getAmount().compareTo(AMOUNT));
        assertEquals(0, order.getDiscount().compareTo(BigDecimal.ZERO),
                "discount 应为 0（无优惠）");
        assertEquals(0, order.getPaidAmount().compareTo(AMOUNT),
                "paidAmount 应等于 amount（无优惠）");
        assertEquals("WECHAT", order.getPayMethod());
        assertEquals("PENDING", order.getPayStatus(),
                "insert 时 payStatus 应为 PENDING（标记已支付前）");
        assertEquals(String.valueOf(USER_ID), order.getCreateBy());
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("recharge — payMethod=null → 默认 ALIPAY")
    void recharge_payMethodNull_defaultsToAlipay() {
        RechargeRequest req = rechargeRequest(); // payMethod 默认 null
        when(walletService.recharge(anyLong(), anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        controller.recharge(req);

        ArgumentCaptor<OpcBillingOrder> captor = ArgumentCaptor.forClass(OpcBillingOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals("ALIPAY", captor.getValue().getPayMethod(),
                "payMethod 为 null 时应默认 ALIPAY");
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("recharge — payMethod='WECHAT' → 透传到 order.payMethod 和 markPaid")
    void recharge_payMethodProvided_passesThrough() {
        RechargeRequest req = rechargeRequest();
        req.payMethod = "WECHAT";
        when(walletService.recharge(anyLong(), anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        controller.recharge(req);

        // 验证 markPaid 的 payMethod 参数
        verify(orderMapper).markPaid(anyString(), eq("WECHAT"), anyString());
        // 验证 walletService.recharge 的 bizType 参数
        verify(walletService).recharge(eq(COMPANY_ID), eq(USER_ID), eq(AMOUNT),
                eq("WECHAT"), anyString(), anyString());
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("recharge — markPaid 和 walletService.recharge 都被调，参数含 MOCK- 前缀")
    void recharge_marksPaidAndCallsWalletRecharge() {
        RechargeRequest req = rechargeRequest();
        when(walletService.recharge(anyLong(), anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        controller.recharge(req);

        // 1) orderMapper.insert
        verify(orderMapper, times(1)).insert(any(OpcBillingOrder.class));
        // 2) orderMapper.markPaid
        verify(orderMapper, times(1)).markPaid(anyString(), eq("ALIPAY"), argThat(s ->
                s != null && s.startsWith("MOCK-")));
        // 3) walletService.recharge
        verify(walletService, times(1)).recharge(eq(COMPANY_ID), eq(USER_ID), eq(AMOUNT),
                eq("ALIPAY"), argThat(s -> s != null && s.startsWith("MOCK-")),
                anyString());
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("recharge — 返回 success({orderNo, status:'PAID'})")
    void recharge_returnsOrderNoAndPaidStatus() {
        RechargeRequest req = rechargeRequest();
        when(walletService.recharge(anyLong(), anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        AjaxResult result = controller.recharge(req);

        assertEquals(200, result.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNotNull(data);
        assertNotNull(data.get("orderNo"));
        assertTrue(data.get("orderNo").toString().startsWith("O"));
        assertEquals("PAID", data.get("status"),
                "controller 模拟立即到账 → 返回 status='PAID'");
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("recharge — walletService.recharge 抛 OpcException（余额更新失败）→ 异常透传")
    void recharge_serviceThrowsPropagates() {
        RechargeRequest req = rechargeRequest();
        when(walletService.recharge(anyLong(), anyLong(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new OpcException("余额更新失败"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.recharge(req));
        assertTrue(ex.getMessage().contains("余额更新失败"));
        // 验证前面两步（insert + markPaid）已经执行
        verify(orderMapper).insert(any(OpcBillingOrder.class));
        verify(orderMapper).markPaid(anyString(), anyString(), anyString());
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("recharge — title 字符串包含金额（如 '钱包充值 ¥100.00'）")
    void recharge_titleIncludesAmount() {
        RechargeRequest req = rechargeRequest();
        req.amount = new BigDecimal("250.50");
        when(walletService.recharge(anyLong(), anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        controller.recharge(req);

        ArgumentCaptor<OpcBillingOrder> captor = ArgumentCaptor.forClass(OpcBillingOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals("钱包充值 ¥250.50", captor.getValue().getTitle());
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    // ==================== GET /orders ====================

    @Test
    @DisplayName("orders — 透传 companyId + payStatus + limit，返回 success(List)")
    void orders_passesAllArgs() {
        List<OpcBillingOrder> mockList = Arrays.asList(sampleOrder(), sampleOrder());
        when(orderMapper.selectByCompany(COMPANY_ID, "PAID", 50)).thenReturn(mockList);

        AjaxResult result = controller.orders(COMPANY_ID, "PAID", 50);

        assertEquals(200, result.get("code"));
        assertSame(mockList, result.get("data"));
        verify(orderMapper).selectByCompany(COMPANY_ID, "PAID", 50);
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("orders — payStatus=null 也能透传")
    void orders_payStatusNullPassesThrough() {
        when(orderMapper.selectByCompany(COMPANY_ID, null, 20)).thenReturn(new ArrayList<>());

        AjaxResult result = controller.orders(COMPANY_ID, null, 20);

        assertEquals(200, result.get("code"));
        verify(orderMapper).selectByCompany(COMPANY_ID, null, 20);
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    // ==================== GET /order/{orderNo} ====================

    @Test
    @DisplayName("orderDetail — 透传 orderNo，返回 success(Order)")
    void orderDetail_returnsOrder() {
        OpcBillingOrder o = sampleOrder();
        when(orderMapper.selectByOrderNo("O20260907ABCDEF")).thenReturn(o);

        AjaxResult result = controller.orderDetail("O20260907ABCDEF");

        assertEquals(200, result.get("code"));
        assertSame(o, result.get("data"));
        verify(orderMapper).selectByOrderNo("O20260907ABCDEF");
        verifyNoMoreInteractions(walletService, orderMapper);
    }

    @Test
    @DisplayName("orderDetail — order 不存在 → success(null)")
    void orderDetail_notFound() {
        when(orderMapper.selectByOrderNo("NOTFOUND")).thenReturn(null);

        AjaxResult result = controller.orderDetail("NOTFOUND");

        assertEquals(200, result.get("code"));
        assertNull(result.get("data"));
        verifyNoMoreInteractions(walletService, orderMapper);
    }
}
