package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmFollowUp;
import com.ruoyi.opc.crm.mapper.CrmFollowUpMapper;
import com.ruoyi.opc.crm.service.impl.FollowUpServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowUpServiceTest {

    @Mock CrmFollowUpMapper mapper;
    @InjectMocks FollowUpServiceImpl service;

    @Test
    void should_create_follow_up() {
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmFollowUp f = inv.getArgument(0);
            f.setId(1L);
            return 1;
        });

        CrmFollowUp created = service.create(CrmFollowUp.builder()
            .customerId(1L).contactId(2L)
            .content("电话沟通客户需求").nextAt(LocalDateTime.of(2026, 9, 15, 10, 0))
            .build(), 10L);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getCreateBy()).isEqualTo("10");
        assertThat(created.getOwnerId()).isEqualTo(10L);
        assertThat(created.getDeleted()).isEqualTo(0);
        assertThat(created.getType()).isEqualTo("PHONE");
        verify(mapper).insert(any());
    }

    @Test
    void should_reject_blank_content() {
        assertThatThrownBy(() -> service.create(
            CrmFollowUp.builder().customerId(1L).content("").build(), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content");
    }

    @Test
    void should_list_by_customer() {
        when(mapper.selectByCustomerId(1L)).thenReturn(List.of(
            CrmFollowUp.builder().id(1L).customerId(1L).content("A").build(),
            CrmFollowUp.builder().id(2L).customerId(1L).content("B").build()
        ));
        List<CrmFollowUp> list = service.listByCustomer(1L);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getCustomerId()).isEqualTo(1L);
        assertThat(list.get(1).getCustomerId()).isEqualTo(1L);
    }

    @Test
    void should_list_by_owner() {
        when(mapper.selectByOwnerId(10L)).thenReturn(List.of(
            CrmFollowUp.builder().id(1L).ownerId(10L).content("A").build(),
            CrmFollowUp.builder().id(2L).ownerId(10L).content("B").build()
        ));
        List<CrmFollowUp> list = service.listByOwner(10L);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getOwnerId()).isEqualTo(10L);
        assertThat(list.get(1).getOwnerId()).isEqualTo(10L);
    }

    @Test
    void should_list_upcoming() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime scheduled = LocalDateTime.of(2026, 9, 15, 10, 0);
        when(mapper.selectUpcoming(10L, from)).thenReturn(List.of(
            CrmFollowUp.builder().id(1L).ownerId(10L).nextAt(scheduled).build()
        ));
        List<CrmFollowUp> list = service.listUpcoming(10L, from);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getNextAt()).isAfter(from);
    }

    @Test
    void should_mark_follow_up_completed() {
        when(mapper.selectById(1L)).thenReturn(
            CrmFollowUp.builder().id(1L).customerId(1L)
                .content("待跟进").nextAt(LocalDateTime.of(2026, 9, 15, 10, 0)).build()
        );
        when(mapper.updateById(any())).thenReturn(1);

        CrmFollowUp result = service.markCompleted(1L, 10L);

        assertThat(result.getNextAt()).isNull();
        assertThat(result.getUpdateBy()).isEqualTo("10");
        verify(mapper).updateById(argThat(f -> f.getNextAt() == null));
    }
}
