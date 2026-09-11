package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmContact;
import com.ruoyi.opc.crm.mapper.CrmContactMapper;
import com.ruoyi.opc.crm.service.impl.ContactServiceImpl;
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
class ContactServiceTest {

    @Mock CrmContactMapper mapper;
    @InjectMocks ContactServiceImpl service;

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
        when(mapper.selectByCustomerId(1L)).thenReturn(List.of(
            CrmContact.builder().id(1L).customerId(1L).name("A").build(),
            CrmContact.builder().id(2L).customerId(1L).name("B").build()
        ));
        List<CrmContact> list = service.listByCustomer(1L);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getName()).isEqualTo("A");
        assertThat(list.get(1).getName()).isEqualTo("B");
    }

    @Test
    void setPrimary_should_unset_others() {
        when(mapper.unsetPrimaryForCustomer(1L)).thenReturn(1);
        when(mapper.setPrimary(5L)).thenReturn(1);

        service.setPrimary(1L, 5L);

        // Verify the 2-step: unset all others for customer 1, then set primary to 5L
        verify(mapper).unsetPrimaryForCustomer(1L);
        verify(mapper).setPrimary(5L);
    }

    @Test
    void should_delete_contact_by_id() {
        when(mapper.selectById(1L)).thenReturn(
            CrmContact.builder().id(1L).customerId(1L).name("X").build()
        );
        service.delete(1L, 1L);
        verify(mapper).updateById(argThat(c -> c.getDeleted() != null && c.getDeleted() == 1));
    }

    @Test
    void should_throw_when_delete_nonexistent() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }
}
