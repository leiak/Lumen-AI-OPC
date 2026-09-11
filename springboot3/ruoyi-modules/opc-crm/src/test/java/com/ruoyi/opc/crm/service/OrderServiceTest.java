package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmOrder;
import com.ruoyi.opc.crm.enums.OrderStatus;
import com.ruoyi.opc.crm.mapper.CrmOrderMapper;
import com.ruoyi.opc.crm.service.impl.OrderServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock CrmOrderMapper mapper;
    @InjectMocks OrderServiceImpl service;

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
        when(mapper.selectById(1L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.PENDING.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.pay(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getPaidAt()).isNotNull();
    }

    @Test
    void should_ship_paid_order() {
        when(mapper.selectById(1L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.PAID.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.ship(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("SHIPPED");
        assertThat(result.getShippedAt()).isNotNull();
    }

    @Test
    void should_complete_shipped_order() {
        when(mapper.selectById(1L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.SHIPPED.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.complete(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void should_cancel_pending_order() {
        when(mapper.selectById(1L)).thenReturn(CrmOrder.builder()
            .id(1L).status(OrderStatus.PENDING.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOrder result = service.cancel(1L, "客户取消", 10L);
        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getRemark()).isEqualTo("客户取消");
    }
}
