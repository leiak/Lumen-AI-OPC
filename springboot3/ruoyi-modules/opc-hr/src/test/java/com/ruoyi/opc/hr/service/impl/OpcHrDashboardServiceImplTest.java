package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.dto.OpcHrDashboardDto;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dashboard Service 单测 (5 cases):
 * 漏斗 / 转化率 / avg_hire_days null / job 状态分布 / 入参校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcHrDashboardService 单测 (5 cases)")
class OpcHrDashboardServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final int SINCE_DAYS = 30;

    @Mock
    private OpcHrApplicationMapper applicationMapper;

    @Mock
    private OpcHrJobMapper jobMapper;

    @InjectMocks
    private OpcHrDashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        // 默认 stub:job 各状态计数为 0(测试需要时再覆盖)
        when(jobMapper.countList(eq(COMPANY_ID), eq("DRAFT"))).thenReturn(0);
        when(jobMapper.countList(eq(COMPANY_ID), eq("OPEN"))).thenReturn(0);
        when(jobMapper.countList(eq(COMPANY_ID), eq("PAUSED"))).thenReturn(0);
        when(jobMapper.countList(eq(COMPANY_ID), eq("CLOSED"))).thenReturn(0);
    }

    /** Test 1 */
    @Test
    @DisplayName("funnel - 5 段计数正确,即使返回 0 也要出现")
    void getDashboard_funnelCorrect() {
        when(applicationMapper.countByStatusForDashboard(COMPANY_ID, SINCE_DAYS))
                .thenReturn(List.of(
                        statusRow("NEW", 12L),
                        statusRow("SCREENING", 8L),
                        statusRow("INTERVIEW", 5L),
                        statusRow("OFFER", 2L),
                        statusRow("HIRED", 1L)
                ));

        OpcHrDashboardDto dto = dashboardService.getDashboard(COMPANY_ID, SINCE_DAYS);

        assertThat(dto.getFunnel()).hasSize(5);
        assertThat(dto.getFunnel().get(0)).containsEntry("status", "NEW").containsEntry("count", 12);
        assertThat(dto.getFunnel().get(1)).containsEntry("status", "SCREENING").containsEntry("count", 8);
        assertThat(dto.getFunnel().get(2)).containsEntry("status", "INTERVIEW").containsEntry("count", 5);
        assertThat(dto.getFunnel().get(3)).containsEntry("status", "OFFER").containsEntry("count", 2);
        assertThat(dto.getFunnel().get(4)).containsEntry("status", "HIRED").containsEntry("count", 1);
    }

    /** Test 2 */
    @Test
    @DisplayName("funnel - 缺失段(REJECTED)不出现在 funnel 里,5 段始终存在且补 0")
    void getDashboard_funnelZeroSafeWhenSegmentMissing() {
        // mapper 只返回 REJECTED,funnel 5 段应仍全部为 0
        when(applicationMapper.countByStatusForDashboard(COMPANY_ID, SINCE_DAYS))
                .thenReturn(List.of(statusRow("REJECTED", 3L)));

        OpcHrDashboardDto dto = dashboardService.getDashboard(COMPANY_ID, SINCE_DAYS);

        assertThat(dto.getFunnel()).hasSize(5);
        assertThat(dto.getFunnel())
                .extracting(m -> m.get("status"))
                .containsExactly("NEW", "SCREENING", "INTERVIEW", "OFFER", "HIRED");
        assertThat(dto.getFunnel())
                .extracting(m -> m.get("count"))
                .containsExactly(0, 0, 0, 0, 0);
    }

    /** Test 3 */
    @Test
    @DisplayName("conversion_rates - NEW=10 SCREENING=5 → 0.5;from=0 时兜 0.0")
    void getDashboard_conversionRatesCorrect() {
        when(applicationMapper.countByStatusForDashboard(COMPANY_ID, SINCE_DAYS))
                .thenReturn(List.of(
                        statusRow("NEW", 10L),
                        statusRow("SCREENING", 5L),
                        statusRow("INTERVIEW", 2L),
                        statusRow("OFFER", 1L)
                        // HIRED 缺失 → OFFER_TO_HIRED 兜 0.0(因为 OFFER>0 但 HIRED=0)
                ));

        OpcHrDashboardDto dto = dashboardService.getDashboard(COMPANY_ID, SINCE_DAYS);

        assertThat(dto.getConversionRates())
                .containsEntry("NEW_TO_SCREENING", 0.5)
                .containsEntry("SCREENING_TO_INTERVIEW", 0.4)
                .containsEntry("INTERVIEW_TO_OFFER", 0.5)
                .containsEntry("OFFER_TO_HIRED", 0.0);
    }

    /** Test 4 */
    @Test
    @DisplayName("avg_hire_days - null 不 NPE,直接 set 到 dto")
    void getDashboard_avgHireDaysIsNull_safe() {
        when(applicationMapper.countByStatusForDashboard(COMPANY_ID, SINCE_DAYS))
                .thenReturn(List.of());
        when(applicationMapper.avgHireDaysForAcceptedOffers(COMPANY_ID, SINCE_DAYS))
                .thenReturn(null);

        OpcHrDashboardDto dto = dashboardService.getDashboard(COMPANY_ID, SINCE_DAYS);

        assertThat(dto.getAvgHireDays()).isNull();
    }

    /** Test 4b */
    @Test
    @DisplayName("avg_hire_days - 18.5 正常透传")
    void getDashboard_avgHireDaysPassedThrough() {
        when(applicationMapper.countByStatusForDashboard(COMPANY_ID, SINCE_DAYS))
                .thenReturn(List.of());
        when(applicationMapper.avgHireDaysForAcceptedOffers(COMPANY_ID, SINCE_DAYS))
                .thenReturn(18.5);

        OpcHrDashboardDto dto = dashboardService.getDashboard(COMPANY_ID, SINCE_DAYS);

        assertThat(dto.getAvgHireDays()).isEqualTo(18.5);
    }

    /** Test 5 */
    @Test
    @DisplayName("job_status_distribution - 4 段计数正确")
    void getDashboard_jobStatusDistributionCorrect() {
        when(applicationMapper.countByStatusForDashboard(COMPANY_ID, SINCE_DAYS))
                .thenReturn(List.of());
        when(jobMapper.countList(COMPANY_ID, "DRAFT")).thenReturn(3);
        when(jobMapper.countList(COMPANY_ID, "OPEN")).thenReturn(5);
        when(jobMapper.countList(COMPANY_ID, "PAUSED")).thenReturn(1);
        when(jobMapper.countList(COMPANY_ID, "CLOSED")).thenReturn(2);

        OpcHrDashboardDto dto = dashboardService.getDashboard(COMPANY_ID, SINCE_DAYS);

        assertThat(dto.getJobStatusDistribution())
                .containsEntry("DRAFT", 3)
                .containsEntry("OPEN", 5)
                .containsEntry("PAUSED", 1)
                .containsEntry("CLOSED", 2);
    }

    /** Test 6 */
    @Test
    @DisplayName("missing companyId - 抛 ServiceException,不调 mapper")
    void getDashboard_missingCompanyId_throws() {
        assertThatThrownBy(() -> dashboardService.getDashboard(null, 30))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
    }

    /** Test 7 */
    @Test
    @DisplayName("sinceDays<=0 - 兜底 30 天,且仍正常返回 funnel")
    void getDashboard_sinceDaysNegative_usesDefault() {
        when(applicationMapper.countByStatusForDashboard(COMPANY_ID, 30))
                .thenReturn(List.of(statusRow("NEW", 1L)));

        OpcHrDashboardDto dto = dashboardService.getDashboard(COMPANY_ID, -5);

        assertThat(dto.getFunnel()).hasSize(5);
        verify(applicationMapper).countByStatusForDashboard(COMPANY_ID, 30);
    }

    /**
     * 构造 mapper countByStatusForDashboard 的单行结果(类型匹配 XML resultType=java.util.Map)。
     */
    private static Map<String, Object> statusRow(String status, long count) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", status);
        row.put("count", count);
        return row;
    }
}
