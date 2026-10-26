package com.ruoyi.opc.crm.service;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.crm.domain.CrmContract;
import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOrder;
import com.ruoyi.opc.crm.enums.OrderStatus;
import com.ruoyi.opc.crm.mapper.CrmContractMapper;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmOrderMapper;
import com.ruoyi.opc.crm.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock CrmOrderMapper mapper;
    @Mock CrmCustomerMapper customerMapper;
    @Mock CrmContractMapper contractMapper;
    @InjectMocks OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        when(customerMapper.selectByIdAndOwner(anyLong(), anyLong()))
            .thenReturn(CrmCustomer.builder().id(1L).ownerId(1L).build());
        when(contractMapper.selectByIdAndOwner(anyLong(), anyLong()))
            .thenReturn(CrmContract.builder().id(1L).customerId(1L).build());
    }

    @Test
    void should_create_order() {
        when(mapper.countByOrderNo("O-2026-001")).thenReturn(0);
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });

        CrmOrder created = service.create(CrmOrder.builder()
            .customerId(1L).orderNo("O-2026-001")
            .itemsJson("[{\"sku\":\"X\",\"qty\":2,\"price\":100}]")
            .total(new BigDecimal("200")).build(), 10L);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getStatus()).isEqualTo("PENDING");
        verify(mapper).insert(any());
    }

    @Test
    void should_reject_blank_items_json() {
        when(mapper.countByOrderNo("O-X")).thenReturn(0);
        assertThatThrownBy(() -> service.create(CrmOrder.builder()
            .customerId(1L).orderNo("O-X").itemsJson("").build(), 10L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("items_json");
    }

    @Test
    void should_pay_pending_order() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.PENDING.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.pay(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getPaidAt()).isNotNull();
    }

    @Test
    void should_ship_paid_order() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.PAID.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.ship(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("SHIPPED");
        assertThat(result.getShippedAt()).isNotNull();
    }

    @Test
    void should_complete_shipped_order() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.SHIPPED.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.complete(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void should_cancel_pending_order() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.PENDING.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.cancel(1L, "客户取消", 10L);
        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getRemark()).isEqualTo("客户取消");
    }

    // ---------- 越权回归护栏 ----------

    @Test
    void should_not_read_another_owners_order() {
        when(mapper.selectByIdAndOwner(12L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.getById(12L, 10L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
    }

    @Test
    void should_not_pay_another_owners_order() {
        when(mapper.selectByIdAndOwner(12L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.pay(12L, 10L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).updateById(any());
    }

    @Test
    void should_not_cancel_another_owners_order() {
        when(mapper.selectByIdAndOwner(12L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.cancel(12L, "越权", 10L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).updateById(any());
    }

    @Test
    void should_not_create_order_under_another_owners_customer() {
        when(mapper.countByOrderNo("O-X")).thenReturn(0);
        when(customerMapper.selectByIdAndOwner(11L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.create(CrmOrder.builder()
            .customerId(11L).orderNo("O-X")
            .itemsJson("[]").total(BigDecimal.ONE).build(), 10L),
            ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).insert(any());
    }

    @Test
    void should_not_create_order_referencing_another_owners_contract() {
        when(mapper.countByOrderNo("O-Y")).thenReturn(0);
        when(contractMapper.selectByIdAndOwner(8L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.create(CrmOrder.builder()
            .customerId(1L).contractId(8L).orderNo("O-Y")
            .itemsJson("[]").total(BigDecimal.ONE).build(), 10L),
            ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).insert(any());
    }

    @Test
    void should_list_orders_of_contract_only_when_contract_is_own() {
        // 合同 id 是客户端给的 —— 挂到别人合同上的订单不能被列出来
        when(mapper.selectByContractIdAndOwner(8L, 10L)).thenReturn(List.of());
        List<CrmOrder> list = service.listByContract(8L, 10L);
        assertThat(list).isEmpty();
        // mapper 里已经没有不带 owner 的 selectByContractId 了
        verify(mapper).selectByContractIdAndOwner(8L, 10L);
    }
}
