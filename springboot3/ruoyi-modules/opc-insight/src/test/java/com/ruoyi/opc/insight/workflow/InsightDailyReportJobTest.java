package com.ruoyi.opc.insight.workflow;

import com.ruoyi.opc.insight.domain.OpcInsightAnomaly;
import com.ruoyi.opc.insight.mapper.OpcInsightAnomalyMapper;
import com.ruoyi.opc.insight.service.IDailyReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link InsightDailyReportJob} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsightDailyReportJobTest {

    @Mock
    private IDailyReportService dailyReportService;

    @Mock
    private IActiveCompanyQuery companyQuery;

    @Mock
    private OpcInsightAnomalyMapper anomalyMapper;

    @InjectMocks
    private InsightDailyReportJob job;

    @BeforeEach
    void setUp() {
        // 日报异常插入不需要在本测试中模拟主键回填。
        when(anomalyMapper.insert(any(OpcInsightAnomaly.class))).thenReturn(1);
    }

    @Test
    void trigger_singleCompanySuccess() {
        when(companyQuery.activeCompanyIds()).thenReturn(List.of(1L));
        doNothing().when(dailyReportService).generate(1L, LocalDate.now().toString());

        job.trigger();

        verify(dailyReportService, times(1)).generate(1L, LocalDate.now().toString());
        verifyNoInteractions(anomalyMapper);
    }

    @Test
    void trigger_oneCompanyFails_continuesToNext() {
        when(companyQuery.activeCompanyIds()).thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("LLM down")).when(dailyReportService).generate(eq(1L), any());
        doNothing().when(dailyReportService).generate(eq(2L), any());

        job.trigger();

        verify(dailyReportService, times(1)).generate(eq(1L), any());
        verify(dailyReportService, times(1)).generate(eq(2L), any());
        verify(anomalyMapper, times(1)).insert(any(OpcInsightAnomaly.class));
    }

    @Test
    void trigger_allCompaniesFail_logsErrorButDoesNotThrow() {
        when(companyQuery.activeCompanyIds()).thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("LLM down")).when(dailyReportService).generate(any(), any());

        assertDoesNotThrow(() -> job.trigger());

        verify(dailyReportService, times(2)).generate(any(), any());
        verify(anomalyMapper, times(2)).insert(any(OpcInsightAnomaly.class));
    }

    @Test
    void trigger_duplicateDateForCompany_insertsAnomalyInstead() {
        when(companyQuery.activeCompanyIds()).thenReturn(List.of(1L));
        doThrow(new DataIntegrityViolationException("UNIQUE KEY uk_company_period"))
                .when(dailyReportService).generate(eq(1L), any());

        job.trigger();

        verify(anomalyMapper, times(1)).insert(any(OpcInsightAnomaly.class));
        ArgumentCaptor<OpcInsightAnomaly> captor = ArgumentCaptor.forClass(OpcInsightAnomaly.class);
        verify(anomalyMapper).insert(captor.capture());
        assertEquals("DAILY_REPORT_DUPLICATE", captor.getValue().getRuleCode());
        assertEquals("ACK", captor.getValue().getStatus());
    }
}
