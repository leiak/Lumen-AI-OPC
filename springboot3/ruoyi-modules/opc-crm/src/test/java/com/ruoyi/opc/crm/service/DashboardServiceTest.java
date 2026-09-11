package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmFollowUp;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.DashboardFunnelResponse;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmFollowUpMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;
import com.ruoyi.opc.crm.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock CrmCustomerMapper customerMapper;
    @Mock CrmOpportunityMapper opportunityMapper;
    @Mock CrmFollowUpMapper followUpMapper;
    @InjectMocks DashboardServiceImpl service;

    @Test
    void should_compute_funnel() {
        when(opportunityMapper.countByStage(10L, "LEAD")).thenReturn(20);
        when(opportunityMapper.countByStage(10L, "QUALIFIED")).thenReturn(12);
        when(opportunityMapper.countByStage(10L, "PROPOSAL")).thenReturn(8);
        when(opportunityMapper.countByStage(10L, "NEGOTIATION")).thenReturn(5);
        when(opportunityMapper.countByStage(10L, "WON")).thenReturn(2);
        when(opportunityMapper.countByStage(10L, "LOST")).thenReturn(3);
        when(opportunityMapper.sumAmountByOwnerAndStages(eq(10L), any())).thenReturn(new BigDecimal("500000"));
        when(opportunityMapper.selectTopByOwner(10L, 10)).thenReturn(List.of(
            CrmOpportunity.builder().id(1L).name("Top1").amount(new BigDecimal("100000")).build()
        ));

        DashboardFunnelResponse resp = service.getFunnel(10L);

        assertThat(resp.getFunnel()).containsEntry("LEAD", 20L);
        assertThat(resp.getFunnel()).containsEntry("WON", 2L);
        assertThat(resp.getTopOpportunities()).hasSize(1);
        assertThat(resp.getActiveAmount()).isEqualByComparingTo("500000");
    }

    @Test
    void should_aggregate_customers_by_level() {
        when(customerMapper.countByOwner(10L)).thenReturn(50);
        when(customerMapper.countByOwnerAndLevel(10L, "A")).thenReturn(5);
        when(customerMapper.countByOwnerAndLevel(10L, "B")).thenReturn(10);
        when(customerMapper.countByOwnerAndLevel(10L, "C")).thenReturn(20);
        when(customerMapper.countByOwnerAndLevel(10L, "D")).thenReturn(15);

        Map<String, Long> resp = service.getCustomerSummary(10L);

        assertThat(resp).containsEntry("total", 50L);
        assertThat(resp).containsEntry("A", 5L);
        assertThat(resp).containsEntry("B", 10L);
        assertThat(resp).containsEntry("C", 20L);
        assertThat(resp).containsEntry("D", 15L);
    }

    @Test
    void should_get_upcoming_follow_ups() {
        when(followUpMapper.selectUpcoming(eq(10L), any())).thenReturn(List.of(
            CrmFollowUp.builder().id(1L).ownerId(10L).content("call").nextAt(java.time.LocalDateTime.now().plusDays(1)).build()
        ));
        List<CrmFollowUp> list = service.getUpcomingFollowUps(10L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getOwnerId()).isEqualTo(10L);
    }

    @Test
    void should_get_recent_follow_ups_limited() {
        when(followUpMapper.selectByOwnerId(10L)).thenReturn(List.of(
            CrmFollowUp.builder().id(1L).content("A").createTime(java.time.LocalDateTime.now()).build(),
            CrmFollowUp.builder().id(2L).content("B").createTime(java.time.LocalDateTime.now().minusDays(1)).build(),
            CrmFollowUp.builder().id(3L).content("C").createTime(java.time.LocalDateTime.now().minusDays(2)).build()
        ));
        List<CrmFollowUp> list = service.getRecentFollowUps(10L, 2);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getContent()).isEqualTo("A");
        assertThat(list.get(1).getContent()).isEqualTo("B");
    }
}
