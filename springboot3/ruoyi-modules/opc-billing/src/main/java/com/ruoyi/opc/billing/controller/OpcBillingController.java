package com.ruoyi.opc.billing.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.billing.domain.OpcBillingOrder;
import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.mapper.OpcBillingOrderMapper;
import com.ruoyi.opc.billing.service.IOpcBillingOrderService;
import com.ruoyi.opc.billing.service.IOpcWalletService;
import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;
import com.ruoyi.opc.billing.vo.WalletAggVo;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OPC 计费 API
 *
 * @author OAC
 */
@Tag(name = "OPC 计费")
@RestController
@RequestMapping("/opc/billing")
@RequiredArgsConstructor
public class OpcBillingController extends BaseController {

    private final IOpcWalletService walletService;
    private final OpcBillingOrderMapper orderMapper;
    private final IOpcBillingOrderService orderService;

    @Operation(summary = "钱包余额")
    @GetMapping("/wallet")
    public AjaxResult wallet(@RequestParam Long companyId) {
        Long userId = SecurityUtils.getUserId();
        OpcWallet w = walletService.getOrCreate(companyId, userId);
        return success(w);
    }

    @Operation(summary = "钱包充值（模拟）")
    @PostMapping("/wallet/recharge")
    public AjaxResult recharge(@RequestBody RechargeRequest req) {
        Long userId = SecurityUtils.getUserId();
        // 实际：调用支付宝/微信预下单；这里直接落账
        OpcBillingOrder order = new OpcBillingOrder();
        order.setOrderNo(OpcCodeGenerator.orderNo());
        order.setCompanyId(req.companyId);
        order.setUserId(userId);
        order.setOrderType("RECHARGE");
        order.setTitle("钱包充值 ¥" + req.amount);
        order.setAmount(req.amount);
        order.setDiscount(BigDecimal.ZERO);
        order.setPaidAmount(req.amount);
        order.setPayMethod(req.payMethod == null ? "ALIPAY" : req.payMethod);
        order.setPayStatus("PENDING");
        order.setCreateBy(String.valueOf(userId));
        orderMapper.insert(order);

        // 模拟立即到账
        orderMapper.markPaid(order.getOrderNo(), order.getPayMethod(),
                "MOCK-" + System.currentTimeMillis());
        walletService.recharge(req.companyId, userId, req.amount, order.getPayMethod(),
                "MOCK-" + System.currentTimeMillis());

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("status", "PAID");
        return success(data);
    }

    @Operation(summary = "订单列表")
    @GetMapping("/orders")
    public AjaxResult orders(@RequestParam Long companyId,
                              @RequestParam(required = false) String payStatus,
                              @RequestParam(defaultValue = "20") Integer limit) {
        return success(orderMapper.selectByCompany(companyId, payStatus, limit));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/order/{orderNo}")
    public AjaxResult orderDetail(@PathVariable String orderNo) {
        return success(orderMapper.selectByOrderNo(orderNo));
    }

    // ==================== 聚合端点（M4 INSIGHT Day 2 / Task 2，供 opc-insight 经 Feign 拉取） ====================
    // 返回 R<T> 而非 AjaxResult：Feign 侧声明的是 R<WalletAggVo> 等强类型，便于直接反序列化。
    //
    // C2 (W11 评审修复)：全部 3 个端点加 @InnerAuth —— 要求请求头 from-source: inner，
    //   网关 AuthFilter 会剥掉外部请求的该请求头，因此这些端点不会被公网触达。
    //   仅有 opc-insight 等服务间 Feign 调用（Feign 客户端会带上 from-source: inner）能命中。
    //
    // C1 (W11 评审修复)：全局 GlobalExceptionHandler.handleServiceException 把 OpcException
    //   转成 AjaxResult。若任由 advice 兜底，Feign 侧声明的 R<WalletAggVo> 解析会失败。
    //   这里用 controller 级 @ExceptionHandler 把 OpcException 转成 R.fail(code, msg)，
    //   让 3 个 agg 端点的响应体永远是 R<T>。

    @Operation(summary = "钱包公司级余额聚合（INSIGHT KPI）")
    @InnerAuth
    @GetMapping("/agg/wallet")
    public R<WalletAggVo> walletAgg(@RequestParam Long companyId) {
        return R.ok(walletService.aggregateWalletByCompany(companyId));
    }

    @Operation(summary = "订单月度聚合（INSIGHT KPI）")
    @InnerAuth
    @GetMapping("/agg/orders")
    public R<OrdersAggVo> ordersAgg(@RequestParam Long companyId, @RequestParam String period) {
        return R.ok(orderService.aggregateByPeriod(companyId, period));
    }

    @Operation(summary = "充值月度聚合（INSIGHT KPI，含渠道分桶）")
    @InnerAuth
    @GetMapping("/agg/recharge")
    public R<RechargeAggVo> rechargeAgg(@RequestParam Long companyId, @RequestParam String period) {
        return R.ok(orderService.aggregateRechargeByPeriod(companyId, period));
    }

    /**
     * 把 {@link OpcException} 转成 {@link R#fail(int, String)} —— 让 3 个聚合端点的响应体
     * 形状永远是 {@code R<T>}，不会被全局 advice 转成 {@code AjaxResult}。
     * <p>
     * 仅作用于本 controller —— 其他端点（如 wallet CRUD）继续走 advice 返回 AjaxResult，
     * 不破坏现有约定。
     */
    @ExceptionHandler(OpcException.class)
    public R<Void> handleOpcException(OpcException e) {
        return R.fail(e.getCode().intValue(), e.getMessage());
    }

    @lombok.Data
    public static class RechargeRequest {
        public Long companyId;
        public BigDecimal amount;
        public String payMethod;
    }

}
