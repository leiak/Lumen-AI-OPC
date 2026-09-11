package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.domain.OpcHrInterview;
import com.ruoyi.opc.hr.dto.OpcHrInterviewDto;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrInterviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcHrInterviewService 单测 (4 cases) — round 自增 + 反馈 + 跨租户防护")
class OpcHrInterviewServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long APPLICATION_ID = 300L;
    private static final Long INTERVIEWER_ID = 999L;
    private static final Long INTERVIEW_ID = 500L;

    @Mock
    private OpcHrInterviewMapper interviewMapper;

    @Mock
    private OpcHrApplicationMapper applicationMapper;

    @InjectMocks
    private OpcHrInterviewServiceImpl interviewService;

    private OpcHrInterviewDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcHrInterviewDto.builder()
                .companyId(COMPANY_ID)
                .applicationId(APPLICATION_ID)
                .type("ONSITE")
                .interviewerId(INTERVIEWER_ID)
                .scheduledAt(LocalDateTime.of(2026, 9, 20, 14, 30))
                .durationMin(45)
                .build();

        OpcHrApplication app = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).jobId(100L).candidateId(200L).build();
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(app);
    }

    /** Test 1 */
    @Test
    @DisplayName("create - 已有面试时 round 自增为 maxRound + 1")
    void create_secondRound_autoIncrement() {
        when(interviewMapper.maxRoundByApplication(APPLICATION_ID)).thenReturn(1);
        when(interviewMapper.insert(any(OpcHrInterview.class))).thenReturn(1);

        Long id = interviewService.create(sampleDto);

        assertThat(id).isNotNull().isPositive();
        ArgumentCaptor<OpcHrInterview> captor = ArgumentCaptor.forClass(OpcHrInterview.class);
        verify(interviewMapper).insert(captor.capture());
        OpcHrInterview saved = captor.getValue();
        assertThat(saved.getRound()).isEqualTo(2);
        assertThat(saved.getResult()).isEqualTo("PENDING");
        assertThat(saved.getDurationMin()).isEqualTo(45);
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getId()).isEqualTo(id);
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 首次面试 maxRound=null 时 round=1")
    void create_firstRound_whenMaxNull() {
        when(interviewMapper.maxRoundByApplication(APPLICATION_ID)).thenReturn(null);
        when(interviewMapper.insert(any(OpcHrInterview.class))).thenReturn(1);

        interviewService.create(sampleDto);

        ArgumentCaptor<OpcHrInterview> captor = ArgumentCaptor.forClass(OpcHrInterview.class);
        verify(interviewMapper).insert(captor.capture());
        OpcHrInterview saved = captor.getValue();
        assertThat(saved.getRound()).isEqualTo(1);
        // durationMin 为 null 时兜底 60
        sampleDto.setDurationMin(null);
        // 再创建一次,duration 走 default
        when(interviewMapper.maxRoundByApplication(APPLICATION_ID)).thenReturn(0);
        interviewService.create(sampleDto);
        verify(interviewMapper, org.mockito.Mockito.times(2)).insert(any(OpcHrInterview.class));
    }

    /** Test 3 */
    @Test
    @DisplayName("create - application 不存在或跨 companyId 抛 ServiceException")
    void create_applicationNotFound_throws() {
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> interviewService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("投递不存在");
        verify(interviewMapper, never()).insert(any(OpcHrInterview.class));
    }

    /** Test 4 */
    @Test
    @DisplayName("updateFeedback - 改 result=PASS,反馈文本写入")
    void updateFeedback_success() {
        OpcHrInterview existing = OpcHrInterview.builder()
                .id(INTERVIEW_ID).companyId(COMPANY_ID).applicationId(APPLICATION_ID)
                .round(1).result("PENDING").build();
        when(interviewMapper.selectById(INTERVIEW_ID, COMPANY_ID)).thenReturn(existing);
        when(interviewMapper.updateById(any(OpcHrInterview.class))).thenReturn(1);

        int rows = interviewService.updateFeedback(INTERVIEW_ID, COMPANY_ID,
                "技术扎实,通过", "PASS");

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<OpcHrInterview> captor = ArgumentCaptor.forClass(OpcHrInterview.class);
        verify(interviewMapper).updateById(captor.capture());
        OpcHrInterview updated = captor.getValue();
        assertThat(updated.getFeedback()).isEqualTo("技术扎实,通过");
        assertThat(updated.getResult()).isEqualTo("PASS");
    }
}
