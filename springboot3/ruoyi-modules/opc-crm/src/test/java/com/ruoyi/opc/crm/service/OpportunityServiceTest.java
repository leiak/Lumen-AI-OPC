package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.enums.OpportunityStage;
import com.ruoyi.opc.crm.gateway.AiCoreGateway;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;
import com.ruoyi.opc.crm.service.impl.OpportunityServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpportunityServiceTest {

    @Mock CrmOpportunityMapper mapper;
    @Mock CrmCustomerMapper customerMapper;
    @Mock AiCoreGateway aiCoreGateway;
    @InjectMocks OpportunityServiceImpl service;

    @Test
    void should_create_opportunity() {
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmOpportunity o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });

        CrmOpportunity created = service.create(CrmOpportunity.builder()
            .customerId(1L).name("云服务订单").amount(new BigDecimal("100000"))
            .stage(OpportunityStage.LEAD.name()).ownerId(10L).build(), 10L);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getCreateBy()).isEqualTo("10");
        verify(mapper).insert(any());
    }

    @Test
    void should_update_opportunity() {
        when(mapper.selectById(1L)).thenReturn(
            CrmOpportunity.builder().id(1L).customerId(1L).name("old").build()
        );
        CrmOpportunity result = service.update(1L, CrmOpportunity.builder()
            .name("new").amount(new BigDecimal("200000")).build(), 10L);
        assertThat(result.getName()).isEqualTo("new");
        assertThat(result.getAmount()).isEqualByComparingTo("200000");
    }

    @Test
    void changeStage_LEAD_to_QUALIFIED_OK() {
        when(mapper.selectById(1L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.LEAD.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOpportunity result = service.changeStage(1L, OpportunityStage.QUALIFIED, null, 10L);
        assertThat(result.getStage()).isEqualTo("QUALIFIED");
    }

    @Test
    void changeStage_WON_isTerminal_no_further_changes() {
        when(mapper.selectById(1L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.WON.name()).build());

        assertThatThrownBy(() -> service.changeStage(1L, OpportunityStage.LOST, null, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid");
    }

    @Test
    void changeStage_LOST_can_reopen_to_LEAD() {
        when(mapper.selectById(1L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.LOST.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOpportunity result = service.changeStage(1L, OpportunityStage.LEAD, null, 10L);
        assertThat(result.getStage()).isEqualTo("LEAD");
    }

    @Test
    void changeStage_invalid_transition_throws() {
        when(mapper.selectById(1L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.LEAD.name()).build());

        // LEAD → WON is invalid (must go through NEGOTIATION)
        assertThatThrownBy(() -> service.changeStage(1L, OpportunityStage.WON, null, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid");
    }

    @Test
    void changeStage_to_LOST_records_reason() {
        when(mapper.selectById(1L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.NEGOTIATION.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOpportunity result = service.changeStage(1L, OpportunityStage.LOST, "价格过高", 10L);
        assertThat(result.getStage()).isEqualTo("LOST");
        assertThat(result.getLostReason()).isEqualTo("价格过高");
    }

    @Test
    void should_score_via_ai_gateway() {
        CrmOpportunity opp = CrmOpportunity.builder()
            .id(1L).customerId(1L).name("订单").amount(new BigDecimal("50000")).build();
        CrmCustomer cust = CrmCustomer.builder().id(1L).name("客户A").build();
        when(mapper.selectById(1L)).thenReturn(opp);
        when(customerMapper.selectById(1L)).thenReturn(cust);
        when(aiCoreGateway.scoreOpportunity(eq(opp), eq(cust)))
            .thenReturn(OpportunityScoreResponse.builder().score(85).reason("需求明确").build());
        when(mapper.updateById(any())).thenReturn(1);

        OpportunityScoreResponse resp = service.score(1L, 10L);
        assertThat(resp.getScore()).isEqualTo(85);
        assertThat(resp.getReason()).isEqualTo("需求明确");
        verify(aiCoreGateway).scoreOpportunity(eq(opp), eq(cust));
        verify(mapper).updateById(argThat(o -> o.getScore() != null && o.getScore() == 85));
    }

    @Test
    void should_list_by_customer() {
        when(mapper.selectByCustomerId(1L)).thenReturn(List.of(
            CrmOpportunity.builder().id(1L).customerId(1L).name("A").build(),
            CrmOpportunity.builder().id(2L).customerId(1L).name("B").build()
        ));
        List<CrmOpportunity> list = service.listByCustomer(1L);
        assertThat(list).hasSize(2);
    }

    @Test
    void should_list_by_stage() {
        when(mapper.selectByStage("LEAD")).thenReturn(List.of(
            CrmOpportunity.builder().id(1L).stage("LEAD").build()
        ));
        List<CrmOpportunity> list = service.listByStage(OpportunityStage.LEAD);
        assertThat(list).hasSize(1);
    }
}
