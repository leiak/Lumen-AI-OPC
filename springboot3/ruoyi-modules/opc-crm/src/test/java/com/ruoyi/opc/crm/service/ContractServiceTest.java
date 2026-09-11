package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmContract;
import com.ruoyi.opc.crm.enums.ContractStatus;
import com.ruoyi.opc.crm.mapper.CrmContractMapper;
import com.ruoyi.opc.crm.service.impl.ContractServiceImpl;
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
class ContractServiceTest {

    @Mock CrmContractMapper mapper;
    @InjectMocks ContractServiceImpl service;

    @Test
    void should_create_contract() {
        when(mapper.countByContractNo("C-2026-001")).thenReturn(0);
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmContract c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        CrmContract created = service.create(CrmContract.builder()
            .customerId(1L).contractNo("C-2026-001").title("SaaS订阅合同")
            .amount(new BigDecimal("50000")).build(), 10L);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getStatus()).isEqualTo("DRAFT");
        verify(mapper).insert(any());
    }

    @Test
    void should_reject_duplicate_contract_no() {
        when(mapper.countByContractNo("C-2026-001")).thenReturn(1);

        assertThatThrownBy(() -> service.create(CrmContract.builder()
            .customerId(1L).contractNo("C-2026-001").title("dup").build(), 10L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contract_no already exists");
    }

    @Test
    void should_activate_draft_contract() {
        when(mapper.selectById(1L)).thenReturn(CrmContract.builder()
            .id(1L).status(ContractStatus.DRAFT.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmContract result = service.activate(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getSignedAt()).isNotNull();
    }

    @Test
    void should_expire_active_contract() {
        when(mapper.selectById(1L)).thenReturn(CrmContract.builder()
            .id(1L).status(ContractStatus.ACTIVE.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmContract result = service.expire(1L, 10L);
        assertThat(result.getStatus()).isEqualTo("EXPIRED");
        assertThat(result.getExpireAt()).isNotNull();
    }

    @Test
    void should_terminate_active_contract() {
        when(mapper.selectById(1L)).thenReturn(CrmContract.builder()
            .id(1L).status(ContractStatus.ACTIVE.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmContract result = service.terminate(1L, "客户违约", 10L);
        assertThat(result.getStatus()).isEqualTo("TERMINATED");
    }

    @Test
    void should_list_by_customer() {
        when(mapper.selectByCustomerId(1L)).thenReturn(List.of(
            CrmContract.builder().id(1L).customerId(1L).contractNo("C-1").build(),
            CrmContract.builder().id(2L).customerId(1L).contractNo("C-2").build()
        ));
        List<CrmContract> list = service.listByCustomer(1L);
        assertThat(list).hasSize(2);
    }
}