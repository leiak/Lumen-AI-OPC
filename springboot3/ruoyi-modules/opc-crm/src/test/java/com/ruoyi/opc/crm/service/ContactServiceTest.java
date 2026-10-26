package com.ruoyi.opc.crm.service;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.crm.domain.CrmContact;
import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.mapper.CrmContactMapper;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.service.impl.ContactServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactServiceTest {

    @Mock CrmContactMapper mapper;
    @Mock CrmCustomerMapper customerMapper;
    @InjectMocks ContactServiceImpl service;

    @BeforeEach
    void setUp() {
        // 归属查询的通用桩: 客户 1 属于 owner 1
        when(customerMapper.selectByIdAndOwner(anyLong(), anyLong()))
            .thenReturn(CrmCustomer.builder().id(1L).ownerId(1L).build());
    }

    @Test
    void should_create_contact_for_customer() {
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmContact c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        CrmContact created = service.create(CrmContact.builder()
            .customerId(1L).name("张三").phone("13800000001").isPrimary(1).build(), 1L);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getCreateBy()).isEqualTo("1");
        assertThat(created.getDeleted()).isEqualTo(0);
        assertThat(created.getIsPrimary()).isEqualTo(1);
        verify(mapper).insert(any());
    }

    @Test
    void should_reject_blank_contact_name() {
        assertThatThrownBy(() ->
            service.create(CrmContact.builder().customerId(1L).name("").build(), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void should_list_contacts_by_customer() {
        when(mapper.selectByCustomerIdAndOwner(1L, 1L)).thenReturn(List.of(
            CrmContact.builder().id(1L).customerId(1L).name("A").build(),
            CrmContact.builder().id(2L).customerId(1L).name("B").build()
        ));
        List<CrmContact> list = service.listByCustomer(1L, 1L);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getName()).isEqualTo("A");
        assertThat(list.get(1).getName()).isEqualTo("B");
    }

    @Test
    void setPrimary_should_unset_others() {
        when(mapper.selectByIdAndOwner(5L, 1L)).thenReturn(
            CrmContact.builder().id(5L).customerId(1L).name("A").build());
        when(mapper.unsetPrimaryForCustomer(1L)).thenReturn(1);
        when(mapper.setPrimary(5L)).thenReturn(1);

        service.setPrimary(5L, 1L);

        // Verify the 2-step: unset all others for customer 1, then set primary to 5L
        verify(mapper).unsetPrimaryForCustomer(1L);
        verify(mapper).setPrimary(5L);
    }

    @Test
    void should_delete_contact_by_id() {
        when(mapper.selectByIdAndOwner(1L, 1L)).thenReturn(
            CrmContact.builder().id(1L).customerId(1L).name("X").build()
        );
        service.delete(1L, 1L);
        verify(mapper).updateById(argThat(c -> c.getDeleted() != null && c.getDeleted() == 1));
    }

    @Test
    void should_throw_when_delete_nonexistent() {
        when(mapper.selectByIdAndOwner(99L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.delete(99L, 1L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        assertThat(ex.getMessage()).contains("无权访问");
    }

    // ---------- 越权回归护栏 ----------

    @Test
    void should_not_read_contact_under_another_owners_customer() {
        // 联系人自己不带 owner_id, 归属沿客户走 —— 客户的 owner 不是我就查不到
        when(mapper.selectByIdAndOwner(30L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.getById(30L, 1L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
    }

    @Test
    void should_not_list_contacts_of_another_owners_customer() {
        when(mapper.selectByCustomerIdAndOwner(11L, 1L)).thenReturn(List.of());
        List<CrmContact> list = service.listByCustomer(11L, 1L);
        assertThat(list).isEmpty();
        // 查询必须带上 owner —— mapper 里已经没有不带 owner 的 selectByCustomerId 了
        verify(mapper).selectByCustomerIdAndOwner(11L, 1L);
    }

    @Test
    void should_not_create_contact_under_another_owners_customer() {
        when(customerMapper.selectByIdAndOwner(11L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.create(
            CrmContact.builder().customerId(11L).name("越权").build(), 1L),
            ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).insert(any());
    }

    @Test
    void setPrimary_should_derive_customer_from_contact_not_from_client() {
        // 客户端可以随便传 customerId —— 服务端必须用联系人自己身上的那个,
        // 否则「我的 contactId + 别人的 customerId」就能把人家客户的主联系人清掉
        when(mapper.selectByIdAndOwner(5L, 1L)).thenReturn(
            CrmContact.builder().id(5L).customerId(1L).name("A").build());
        service.setPrimary(5L, 1L);
        verify(mapper).unsetPrimaryForCustomer(1L);
    }

    @Test
    void setPrimary_should_reject_another_owners_contact() {
        when(mapper.selectByIdAndOwner(30L, 1L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.setPrimary(30L, 1L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).unsetPrimaryForCustomer(anyLong());
        verify(mapper, never()).setPrimary(anyLong());
    }
}
