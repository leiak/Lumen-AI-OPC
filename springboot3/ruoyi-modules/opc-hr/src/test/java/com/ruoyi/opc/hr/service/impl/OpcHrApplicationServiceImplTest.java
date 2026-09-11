package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.dto.OpcHrApplicationDto;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrCandidateMapper;
import com.ruoyi.opc.hr.mapper.OpcHrJobMapper;
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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcHrApplicationService 单测 (10 cases) — 状态机 + W50 score 兜底")
class OpcHrApplicationServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long JOB_ID = 100L;
    private static final Long CANDIDATE_ID = 200L;
    private static final Long APPLICATION_ID = 300L;

    @Mock
    private OpcHrApplicationMapper applicationMapper;

    @Mock
    private OpcHrJobMapper jobMapper;

    @Mock
    private OpcHrCandidateMapper candidateMapper;

    @InjectMocks
    private OpcHrApplicationServiceImpl applicationService;

    private OpcHrApplicationDto sampleDto;
    private OpcHrJob sampleJob;
    private OpcHrCandidate sampleCandidate;

    @BeforeEach
    void setUp() {
        sampleDto = OpcHrApplicationDto.builder()
                .companyId(COMPANY_ID)
                .jobId(JOB_ID)
                .candidateId(CANDIDATE_ID)
                .channel("MANUAL")
                .build();

        sampleJob = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).title("Java 开发").status("OPEN").build();
        sampleCandidate = OpcHrCandidate.builder()
                .id(CANDIDATE_ID).companyId(COMPANY_ID).name("张三").build();

        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(sampleJob);
        when(candidateMapper.selectById(CANDIDATE_ID, COMPANY_ID)).thenReturn(sampleCandidate);
    }

    /** Test 1 */
    @Test
    @DisplayName("create - 完整 dto 成功创建,score 默认 0(W50 教训 1)")
    void create_success_scoreDefaultsToZero() {
        when(applicationMapper.insert(any(OpcHrApplication.class))).thenReturn(1);

        Long id = applicationService.create(sampleDto);

        assertThat(id).isNotNull().isPositive();
        ArgumentCaptor<OpcHrApplication> captor = ArgumentCaptor.forClass(OpcHrApplication.class);
        verify(applicationMapper).insert(captor.capture());
        OpcHrApplication saved = captor.getValue();
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getJobId()).isEqualTo(JOB_ID);
        assertThat(saved.getCandidateId()).isEqualTo(CANDIDATE_ID);
        assertThat(saved.getChannel()).isEqualTo("MANUAL");
        assertThat(saved.getScore()).isEqualTo(0);
        assertThat(saved.getStatus()).isEqualTo("NEW");
        assertThat(saved.getAppliedAt()).isNotNull();
        assertThat(saved.getId()).isEqualTo(id);
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 显式传 score=85 保留,不被默认值覆盖")
    void create_scoreProvided_keepsValue() {
        sampleDto.setScore(85);
        sampleDto.setScoreReason("经验丰富");
        when(applicationMapper.insert(any(OpcHrApplication.class))).thenReturn(1);

        applicationService.create(sampleDto);

        ArgumentCaptor<OpcHrApplication> captor = ArgumentCaptor.forClass(OpcHrApplication.class);
        verify(applicationMapper).insert(captor.capture());
        OpcHrApplication saved = captor.getValue();
        assertThat(saved.getScore()).isEqualTo(85);
        assertThat(saved.getScoreReason()).isEqualTo("经验丰富");
    }

    /** Test 3 */
    @Test
    @DisplayName("create - jobId 不存在或跨租户抛 ServiceException")
    void create_jobNotFound_throws() {
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> applicationService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("岗位不存在");
        verify(applicationMapper, never()).insert(any(OpcHrApplication.class));
    }

    /** Test 4 */
    @Test
    @DisplayName("create - candidateId 不存在或跨租户抛 ServiceException")
    void create_candidateNotFound_throws() {
        when(candidateMapper.selectById(CANDIDATE_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> applicationService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("候选人不存在");
        verify(applicationMapper, never()).insert(any(OpcHrApplication.class));
    }

    /** Test 5 */
    @Test
    @DisplayName("transitionStatus - NEW -> SCREENING 合法转换")
    void transitionStatus_newToScreening_success() {
        OpcHrApplication existing = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).jobId(JOB_ID)
                .candidateId(CANDIDATE_ID).status("NEW").build();
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(existing);
        when(applicationMapper.updateById(any(OpcHrApplication.class))).thenReturn(1);

        applicationService.transitionStatus(APPLICATION_ID, COMPANY_ID, "SCREENING");

        ArgumentCaptor<OpcHrApplication> captor = ArgumentCaptor.forClass(OpcHrApplication.class);
        verify(applicationMapper).updateById(captor.capture());
        OpcHrApplication after = captor.getValue();
        assertThat(after.getStatus()).isEqualTo("SCREENING");
        assertThat(after.getCurrentStage()).isEqualTo("筛选中");
    }

    /** Test 6 */
    @Test
    @DisplayName("transitionStatus - SCREENING -> INTERVIEW 合法转换")
    void transitionStatus_screeningToInterview_success() {
        OpcHrApplication existing = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).status("SCREENING").build();
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(existing);
        when(applicationMapper.updateById(any(OpcHrApplication.class))).thenReturn(1);

        applicationService.transitionStatus(APPLICATION_ID, COMPANY_ID, "INTERVIEW");

        ArgumentCaptor<OpcHrApplication> captor = ArgumentCaptor.forClass(OpcHrApplication.class);
        verify(applicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("INTERVIEW");
    }

    /** Test 7 */
    @Test
    @DisplayName("transitionStatus - OFFER -> HIRED 合法转换")
    void transitionStatus_offerToHired_success() {
        OpcHrApplication existing = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).status("OFFER").build();
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(existing);
        when(applicationMapper.updateById(any(OpcHrApplication.class))).thenReturn(1);

        applicationService.transitionStatus(APPLICATION_ID, COMPANY_ID, "HIRED");

        ArgumentCaptor<OpcHrApplication> captor = ArgumentCaptor.forClass(OpcHrApplication.class);
        verify(applicationMapper).updateById(captor.capture());
        OpcHrApplication after = captor.getValue();
        assertThat(after.getStatus()).isEqualTo("HIRED");
        assertThat(after.getCurrentStage()).isEqualTo("已入职");
    }

    /** Test 8 */
    @Test
    @DisplayName("transitionStatus - HIRED 终态不可转换")
    void transitionStatus_hiredIsTerminal_throws() {
        OpcHrApplication existing = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).status("HIRED").build();
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> applicationService.transitionStatus(APPLICATION_ID, COMPANY_ID, "REJECTED"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("HIRED");
        verify(applicationMapper, never()).updateById(any(OpcHrApplication.class));
    }

    /** Test 9 */
    @Test
    @DisplayName("transitionStatus - NEW 跳级到 HIRED 非法转换")
    void transitionStatus_invalidTarget_throws() {
        OpcHrApplication existing = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).status("NEW").build();
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> applicationService.transitionStatus(APPLICATION_ID, COMPANY_ID, "HIRED"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无法转换");
        verify(applicationMapper, never()).updateById(any(OpcHrApplication.class));
    }

    /** Test 10 */
    @Test
    @DisplayName("list - 按 jobId 过滤返回投递列表")
    void list_withJobFilter() {
        OpcHrApplication app = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).jobId(JOB_ID).status("NEW").build();
        when(applicationMapper.selectList(eq(COMPANY_ID), eq(JOB_ID), eq(null), eq(null), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(app));

        List<OpcHrApplication> rows = applicationService.list(COMPANY_ID, JOB_ID, null, null, 0, 20);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getJobId()).isEqualTo(JOB_ID);
        assertThat(rows.get(0).getStatus()).isEqualTo("NEW");
        verify(applicationMapper).selectList(eq(COMPANY_ID), eq(JOB_ID), eq(null), eq(null), anyInt(), anyInt());
        verifyNoInteractions(jobMapper, candidateMapper);
    }
}