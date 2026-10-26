package com.ruoyi.opc.crm.service;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.enums.OpportunityStage;
import com.ruoyi.opc.crm.gateway.AiCoreGateway;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;
import com.ruoyi.opc.crm.service.impl.OpportunityServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpportunityServiceTest {

    @Mock CrmOpportunityMapper mapper;
    @Mock CrmCustomerMapper customerMapper;
    @Mock AiCoreGateway aiCoreGateway;
    @InjectMocks OpportunityServiceImpl service;

    @BeforeEach
    void setUp() {
        when(customerMapper.selectByIdAndOwner(anyLong(), anyLong()))
            .thenReturn(CrmCustomer.builder().id(1L).ownerId(1L).build());
    }

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
    void should_force_ownerId_to_operator_on_create() {
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmOpportunity o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });

        // 客户端把 ownerId 填成别人(10L 在这里就是操作人) —— 归属由服务端定
        CrmOpportunity created = service.create(CrmOpportunity.builder()
            .customerId(1L).name("挂到别人名下").ownerId(999L).build(), 10L);

        assertThat(created.getOwnerId()).isEqualTo(10L);
    }

    @Test
    void should_update_opportunity() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(
            CrmOpportunity.builder().id(1L).customerId(1L).name("old").build()
        );
        CrmOpportunity result = service.update(1L, CrmOpportunity.builder()
            .name("new").amount(new BigDecimal("200000")).build(), 10L);
        assertThat(result.getName()).isEqualTo("new");
        assertThat(result.getAmount()).isEqualByComparingTo("200000");
    }

    @Test
    void should_not_allow_client_to_rewrite_ownerId() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(
            CrmOpportunity.builder().id(1L).customerId(1L).name("old").ownerId(10L).build()
        );
        // 客户端想把商机转给别人 —— 原来这行直接生效, 等于可以把自己的商机送人
        CrmOpportunity result = service.update(1L, CrmOpportunity.builder()
            .ownerId(999L).build(), 10L);
        assertThat(result.getOwnerId()).isEqualTo(10L);
    }

    @Test
    void changeStage_LEAD_to_QUALIFIED_OK() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.LEAD.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOpportunity result = service.changeStage(1L, OpportunityStage.QUALIFIED, null, 10L);
        assertThat(result.getStage()).isEqualTo("QUALIFIED");
    }

    @Test
    void changeStage_WON_isTerminal_no_further_changes() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.WON.name()).build());

        assertThatThrownBy(() -> service.changeStage(1L, OpportunityStage.LOST, null, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid");
    }

    @Test
    void changeStage_LOST_can_reopen_to_LEAD() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.LOST.name()).build());
        when(mapper.updateById(any())).thenReturn(1);

        CrmOpportunity result = service.changeStage(1L, OpportunityStage.LEAD, null, 10L);
        assertThat(result.getStage()).isEqualTo("LEAD");
    }

    @Test
    void changeStage_invalid_transition_throws() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOpportunity.builder()
            .id(1L).stage(OpportunityStage.LEAD.name()).build());

        // LEAD → WON is invalid (must go through NEGOTIATION)
        assertThatThrownBy(() -> service.changeStage(1L, OpportunityStage.WON, null, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid");
    }

    @Test
    void changeStage_to_LOST_records_reason() {
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(CrmOpportunity.builder()
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
        when(mapper.selectByIdAndOwner(1L, 10L)).thenReturn(opp);
        when(customerMapper.selectByIdAndOwner(1L, 10L)).thenReturn(cust);
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
        when(mapper.selectByCustomerIdAndOwner(1L, 10L)).thenReturn(List.of(
            CrmOpportunity.builder().id(1L).customerId(1L).name("A").build(),
            CrmOpportunity.builder().id(2L).customerId(1L).name("B").build()
        ));
        List<CrmOpportunity> list = service.listByCustomer(1L, 10L);
        assertThat(list).hasSize(2);
    }

    @Test
    void should_list_by_stage() {
        when(mapper.selectByStageAndOwner("LEAD", 10L)).thenReturn(List.of(
            CrmOpportunity.builder().id(1L).stage("LEAD").build()
        ));
        List<CrmOpportunity> list = service.listByStage(OpportunityStage.LEAD, 10L);
        assertThat(list).hasSize(1);
    }

    @Test
    void list_without_params_should_return_callers_own_opportunities() {
        // 以前「什么都不给」直接返回空数组 —— 看板因此永远是空的
        when(mapper.selectList(eq(10L), isNull(), isNull())).thenReturn(List.of(
            CrmOpportunity.builder().id(1L).ownerId(10L).name("A").build()
        ));
        List<CrmOpportunity> list = service.list(10L, null, null);
        assertThat(list).hasSize(1);
    }

    // ---------- 越权回归护栏 ----------

    @Test
    void should_not_read_another_owners_opportunity() {
        when(mapper.selectByIdAndOwner(20L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.getById(20L, 10L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
    }

    @Test
    void should_not_change_stage_of_another_owners_opportunity() {
        when(mapper.selectByIdAndOwner(20L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(
            () -> service.changeStage(20L, OpportunityStage.QUALIFIED, null, 10L),
            ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).updateById(any());
    }

    @Test
    void should_not_list_another_owners_opportunities_by_stage() {
        // 阶段是全局枚举 —— 不带 owner 就是全员数据
        when(mapper.selectByStageAndOwner("LEAD", 10L)).thenReturn(List.of());
        List<CrmOpportunity> list = service.listByStage(OpportunityStage.LEAD, 10L);
        assertThat(list).isEmpty();
        // mapper 里已经没有不带 owner 的 selectByStage 了
        verify(mapper).selectByStageAndOwner("LEAD", 10L);
    }

    @Test
    void should_not_create_opportunity_under_another_owners_customer() {
        when(customerMapper.selectByIdAndOwner(11L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.create(
            CrmOpportunity.builder().customerId(11L).name("越权").build(), 10L),
            ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(mapper, never()).insert(any());
    }

    @Test
    void should_not_score_opportunity_of_another_owner() {
        when(mapper.selectByIdAndOwner(20L, 10L)).thenReturn(null);
        ServiceException ex = catchThrowableOfType(() -> service.score(20L, 10L), ServiceException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(403);
        verify(aiCoreGateway, never()).scoreOpportunity(any(), any());
    }
}
