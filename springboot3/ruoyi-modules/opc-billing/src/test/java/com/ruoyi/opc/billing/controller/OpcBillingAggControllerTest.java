package com.ruoyi.opc.billing.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.billing.service.IOpcBillingOrderService;
import com.ruoyi.opc.billing.service.IOpcWalletService;
import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;
import com.ruoyi.opc.billing.vo.WalletAggVo;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcBillingController} 聚合端点单测 — M4 INSIGHT Day 2 / Task 2
 *
 * <p>覆盖 3 个聚合端点 + 1 个异常处理路径：
 * <ul>
 *   <li>GET /opc/billing/agg/wallet — company 余额聚合</li>
 *   <li>GET /opc/billing/agg/orders — 订单月度聚合</li>
 *   <li>GET /opc/billing/agg/recharge — 充值月度聚合（含分渠道）</li>
 *   <li>@ExceptionHandler(OpcException.class) → R.fail(code, msg)，不走 AjaxResult</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>使用 {@link MockitoExtension} + {@link Strictness#LENIENT}（部分 stub 非每条用例都用到）</li>
 *   <li>聚合端点返回 {@link R} 类型（Feign 强类型反序列化），不是 {@code AjaxResult}</li>
 *   <li>{@code @InnerAuth} 不需要 Mockito stubbing — 它只是注解元数据，网关侧拦截生效</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcBillingAggControllerTest {

    @Mock
    private IOpcWalletService walletService;

    @Mock
    private IOpcBillingOrderService orderService;

    @InjectMocks
    private OpcBillingController controller;

    private static final Long COMPANY_ID = 1001L;
    private static final String PERIOD = "2026-09";

    // ==================== GET /opc/billing/agg/wallet ====================

    @Test
    @DisplayName("walletAgg — companyId 透传到 service，返回 R.ok(WalletAggVo)")
    void walletAgg_returnsVoFromService() {
        WalletAggVo expected = WalletAggVo.builder()
                .companyId(COMPANY_ID)
                .balance(new BigDecimal("500.00"))
                .currency("CNY")
                .build();
        when(walletService.aggregateWalletByCompany(COMPANY_ID)).thenReturn(expected);

        R<WalletAggVo> result = controller.walletAgg(COMPANY_ID);

        assertNotNull(result);
        assertEquals(200, result.getCode(), "R.ok() 默认 code=200");
        assertSame(expected, result.getData(),
                "R.data 应是 service 返回的 VO 引用（不强 copy）");
        assertEquals(200, result.getCode());
        verify(walletService).aggregateWalletByCompany(COMPANY_ID);
        verifyNoInteractions(orderService);
    }

    // ==================== GET /opc/billing/agg/orders ====================

    @Test
    @DisplayName("ordersAgg — companyId + period 透传到 orderService，返回 R.ok(OrdersAggVo)")
    void ordersAgg_returnsVoFromService() {
        OrdersAggVo expected = OrdersAggVo.builder()
                .companyId(COMPANY_ID)
                .period(PERIOD)
                .count(10L)
                .totalAmount(new BigDecimal("1000.00"))
                .paidAmount(new BigDecimal("800.00"))
                .refundedAmount(new BigDecimal("50.00"))
                .build();
        when(orderService.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(expected);

        R<OrdersAggVo> result = controller.ordersAgg(COMPANY_ID, PERIOD);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertSame(expected, result.getData());
        verify(orderService).aggregateByPeriod(COMPANY_ID, PERIOD);
        verifyNoInteractions(walletService);
    }

    // ==================== GET /opc/billing/agg/recharge ====================

    @Test
    @DisplayName("rechargeAgg — companyId + period 透传到 orderService，含 channels 列表")
    void rechargeAgg_returnsVoWithChannels() {
        RechargeAggVo.ChannelAmount alipay = new RechargeAggVo.ChannelAmount("ALIPAY", new BigDecimal("300.00"));
        RechargeAggVo.ChannelAmount wechat = new RechargeAggVo.ChannelAmount("WECHAT", new BigDecimal("200.00"));
        RechargeAggVo expected = RechargeAggVo.builder()
                .companyId(COMPANY_ID)
                .period(PERIOD)
                .count(5L)
                .totalAmount(new BigDecimal("500.00"))
                .channels(List.of(alipay, wechat))
                .build();
        when(orderService.aggregateRechargeByPeriod(COMPANY_ID, PERIOD)).thenReturn(expected);

        R<RechargeAggVo> result = controller.rechargeAgg(COMPANY_ID, PERIOD);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertSame(expected, result.getData());
        // 验证 channels 列表被透传
        assertEquals(2, result.getData().getChannels().size());
        assertEquals("ALIPAY", result.getData().getChannels().get(0).getChannel());
        assertEquals(0, result.getData().getChannels().get(0).getAmount().compareTo(new BigDecimal("300.00")));
        verify(orderService).aggregateRechargeByPeriod(COMPANY_ID, PERIOD);
        verifyNoInteractions(walletService);
    }

    // ==================== @ExceptionHandler(OpcException.class) ====================

    @Test
    @DisplayName("@ExceptionHandler — OpcException(code=500, msg=...) → R.fail(code, msg)，不抛 AjaxResult")
    void exceptionHandler_opcException_returnsRFail() {
        // 单测直接调 @ExceptionHandler 方法（不走 Spring MVC dispatch）——验证 handler 本身的契约
        OpcException ex = new OpcException(500, "period 格式错误，应为 YYYY-MM");

        R<Void> result = controller.handleOpcException(ex);

        assertNotNull(result, "@ExceptionHandler 应返回 R<Void>，不应让异常抛出");
        assertEquals(500, result.getCode(), "OpcException code=500 → R.fail(code, msg).code=500");
        assertEquals("period 格式错误，应为 YYYY-MM", result.getMsg());
        assertNull(result.getData(), "R<Void>.data 应为 null");
    }
}
