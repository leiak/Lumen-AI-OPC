package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmOrder;
import com.ruoyi.opc.crm.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单 Controller
 */
@RestController
@RequestMapping("/opc/crm/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public R<List<CrmOrder>> list(@RequestParam Long customerId,
                                  @RequestParam(required = false) Long contractId) {
        if (contractId != null) return R.ok(orderService.listByContract(contractId));
        return R.ok(orderService.listByCustomer(customerId));
    }

    @GetMapping("/{id}")
    public R<CrmOrder> getById(@PathVariable Long id) {
        return R.ok(orderService.getById(id));
    }

    @PostMapping
    public R<CrmOrder> create(@RequestBody CrmOrder order) {
        return R.ok(orderService.create(order, SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}")
    public R<CrmOrder> update(@PathVariable Long id, @RequestBody CrmOrder order) {
        return R.ok(orderService.update(id, order, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/pay")
    public R<CrmOrder> pay(@PathVariable Long id) {
        return R.ok(orderService.pay(id, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/ship")
    public R<CrmOrder> ship(@PathVariable Long id) {
        return R.ok(orderService.ship(id, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/complete")
    public R<CrmOrder> complete(@PathVariable Long id) {
        return R.ok(orderService.complete(id, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/cancel")
    public R<CrmOrder> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(orderService.cancel(id, reason, SecurityUtils.getUserId()));
    }
}