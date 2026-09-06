package com.ruoyi.opc.billing.controller;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.billing.domain.OpcBillingOrder;
import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.mapper.OpcBillingOrderMapper;
import com.ruoyi.opc.billing.service.IOpcWalletService;
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

    @lombok.Data
    public static class RechargeRequest {
        public Long companyId;
        public BigDecimal amount;
        public String payMethod;
    }

}
