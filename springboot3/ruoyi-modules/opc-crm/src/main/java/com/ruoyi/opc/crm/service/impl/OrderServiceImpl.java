package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmOrder;
import com.ruoyi.opc.crm.enums.OrderStatus;
import com.ruoyi.opc.crm.mapper.CrmOrderMapper;
import com.ruoyi.opc.crm.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CrmOrderMapper mapper;

    @Override
    @Transactional
    public CrmOrder create(CrmOrder order, Long operatorId) {
        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            throw new IllegalArgumentException("order_no required (订单编号必填)");
        }
        if (order.getItemsJson() == null || order.getItemsJson().isBlank()) {
            throw new IllegalArgumentException("items_json required (订单明细不能为空)");
        }
        if (order.getTotal() != null && order.getTotal().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("total cannot be negative (订单金额不能为负)");
        }
        if (mapper.countByOrderNo(order.getOrderNo()) > 0) {
            throw new IllegalArgumentException(
                "order_no already exists: " + order.getOrderNo() + " (订单编号已存在)");
        }
        if (order.getStatus() == null) order.setStatus(OrderStatus.PENDING.name());
        order.setCreateBy(String.valueOf(operatorId));
        order.setUpdateBy(String.valueOf(operatorId));
        if (order.getDeleted() == null) order.setDeleted(0);
        mapper.insert(order);
        log.info("Created order id={} orderNo={} total={}",
            order.getId(), order.getOrderNo(), order.getTotal());
        return order;
    }

    @Override
    @Transactional
    public CrmOrder update(Long id, CrmOrder update, Long operatorId) {
        CrmOrder existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Order not found: " + id);
        }
        if (update.getItemsJson() != null) existing.setItemsJson(update.getItemsJson());
        if (update.getTotal() != null) existing.setTotal(update.getTotal());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public CrmOrder pay(Long id, Long operatorId) {
        CrmOrder o = mapper.selectById(id);
        if (o == null) throw new IllegalArgumentException("Order not found: " + id);
        if (!OrderStatus.PENDING.name().equals(o.getStatus())) {
            throw new IllegalStateException(
                "Only PENDING can be paid (只有待支付订单可以支付). Current: " + o.getStatus());
        }
        o.setStatus(OrderStatus.PAID.name());
        o.setPaidAt(LocalDateTime.now());
        o.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(o);
        return o;
    }

    @Override
    @Transactional
    public CrmOrder ship(Long id, Long operatorId) {
        CrmOrder o = mapper.selectById(id);
        if (o == null) throw new IllegalArgumentException("Order not found: " + id);
        if (!OrderStatus.PAID.name().equals(o.getStatus())) {
            throw new IllegalStateException(
                "Only PAID can be shipped (只有已支付订单可以发货). Current: " + o.getStatus());
        }
        o.setStatus(OrderStatus.SHIPPED.name());
        o.setShippedAt(LocalDateTime.now());
        o.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(o);
        return o;
    }

    @Override
    @Transactional
    public CrmOrder complete(Long id, Long operatorId) {
        CrmOrder o = mapper.selectById(id);
        if (o == null) throw new IllegalArgumentException("Order not found: " + id);
        if (!OrderStatus.SHIPPED.name().equals(o.getStatus())) {
            throw new IllegalStateException(
                "Only SHIPPED can be completed (只有已发货订单可以完成). Current: " + o.getStatus());
        }
        o.setStatus(OrderStatus.COMPLETED.name());
        o.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(o);
        return o;
    }

    @Override
    @Transactional
    public CrmOrder cancel(Long id, String reason, Long operatorId) {
        CrmOrder o = mapper.selectById(id);
        if (o == null) throw new IllegalArgumentException("Order not found: " + id);
        if (OrderStatus.COMPLETED.name().equals(o.getStatus()) ||
            OrderStatus.CANCELLED.name().equals(o.getStatus())) {
            throw new IllegalStateException(
                "Order is final: " + o.getStatus() + " (订单已完成/取消，不能再次取消)");
        }
        o.setStatus(OrderStatus.CANCELLED.name());
        if (reason != null) o.setRemark(reason);
        o.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(o);
        return o;
    }

    @Override
    public CrmOrder getById(Long id) {
        CrmOrder o = mapper.selectById(id);
        if (o == null) throw new IllegalArgumentException("Order not found: " + id);
        return o;
    }

    @Override
    public List<CrmOrder> listByCustomer(Long customerId) {
        return mapper.selectByCustomerId(customerId);
    }

    @Override
    public List<CrmOrder> listByContract(Long contractId) {
        return mapper.selectByContractId(contractId);
    }
}
