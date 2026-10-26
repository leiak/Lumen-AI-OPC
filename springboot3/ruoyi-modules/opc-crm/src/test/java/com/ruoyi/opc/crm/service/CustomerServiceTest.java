package com.ruoyi.opc.crm.service;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceTest {

    @Mock CrmCustomerMapper mapper;
    @InjectMocks CustomerServiceImpl service;

    @BeforeEach
    void setUp() {}

    @Test
    void should_create_customer_with_ownerId_from_security() {
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmCustomer c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        CrmCustomer created = service.create(CrmCustomer.builder().name("Test Co").build(), 1L);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getOwnerId()).isEqualTo(1L);
        assertThat(created.getCreateBy()).isEqualTo("1");
        assertThat(created.getSource()).isEqualTo("OTHER"); // default
        assertThat(created.getLevel()).isEqualTo("C");       // default
        verify(mapper).insert(any());
    }

    @Test
    void should_reject_blank_name() {
        assertThatThrownBy(() -> service.create(CrmCustomer.builder().name("").build(), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void should_update_existing_customer() {
        CrmCustomer existing = CrmCustomer.builder().id(1L).name("Old").ownerId(1L).level("C").source("OTHER").build();
        when(mapper.selectByIdAndOwner(1L, 1L)).thenReturn(existing);

        CrmCustomer update = CrmCustomer.builder().name("New").level("B").build();
        CrmCustomer result = service.update(1L, update, 1L);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getLevel()).isEqualTo("B");
        verify(mapper).updateById(any());
    }

    @Test
    void should_throw_when_update_nonexistent() {
        when(mapper.selectByIdAndOwner(99L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(
            () -> service.update(99L, CrmCustomer.builder().name("X").build(), 1L),
            ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        assertThat(ex.getMessage()).contains("无权访问");
    }

    @Test
    void should_soft_delete_by_id() {
        when(mapper.selectByIdAndOwner(1L, 1L)).thenReturn(CrmCustomer.builder().id(1L).name("X").build());
        service.delete(1L, 1L);
        verify(mapper).updateById(argThat(c -> c.getDeleted() == 1));
    }

    @Test
    void should_list_with_filters() {
        when(mapper.selectList(any(), any(), any(), any(), any()))
            .thenReturn(List.of(CrmCustomer.builder().id(1L).name("A").build()));
        List<CrmCustomer> list = service.list(1L, "A", "REFERRAL", "VIP", "kw");
        assertThat(list).hasSize(1);
    }

    @Test
    void should_throw_when_get_nonexistent() {
        when(mapper.selectByIdAndOwner(99L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.getById(99L, 1L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
    }

    @Test
    void should_count_by_owner() {
        when(mapper.countByOwner(1L)).thenReturn(42);
        assertThat(service.countByOwner(1L)).isEqualTo(42);
    }

    // ---------- 越权回归护栏 ----------

    @Test
    void should_not_read_another_owners_customer() {
        // 客户 11 属于 owner 101 —— owner 1 来取, 归属查询查不到
        when(mapper.selectByIdAndOwner(11L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.getById(11L, 1L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
    }

    @Test
    void should_not_leak_existence_through_error_message() {
        // 「不存在」和「不是你的」必须是同一句话 ——
        // 分开报就等于送出一个存在性预言机, 探测方能把别人的 id 一个一个试出来
        when(mapper.selectByIdAndOwner(11L, 1L)).thenReturn(null);
        when(mapper.selectByIdAndOwner(999999L, 1L)).thenReturn(null);
        String foreign = catchThrowableOfType(() -> service.getById(11L, 1L), ServiceException.class).getMessage();
        String absent = catchThrowableOfType(() -> service.getById(999999L, 1L), ServiceException.class).getMessage();
        assertThat(foreign).isEqualTo(absent);
    }

    @Test
    void should_not_update_another_owners_customer() {
        when(mapper.selectByIdAndOwner(11L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(
            () -> service.update(11L, CrmCustomer.builder().name("X").build(), 1L),
            ServiceException.class);
        assertThat(ex).isNotNull();
        verify(mapper, never()).updateById(any());
    }

    @Test
    void should_not_delete_another_owners_customer() {
        when(mapper.selectByIdAndOwner(11L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.delete(11L, 1L), ServiceException.class);
        assertThat(ex).isNotNull();
        verify(mapper, never()).updateById(any());
    }
}
