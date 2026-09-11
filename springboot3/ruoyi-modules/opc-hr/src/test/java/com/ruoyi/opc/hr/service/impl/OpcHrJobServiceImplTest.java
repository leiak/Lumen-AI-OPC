package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.dto.OpcHrJobDto;
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

import java.math.BigDecimal;
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
@DisplayName("OpcHrJobService 单测 (15 cases)")
class OpcHrJobServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long JOB_ID = 123L;

    @Mock
    private OpcHrJobMapper jobMapper;

    @InjectMocks
    private OpcHrJobServiceImpl jobService;

    private OpcHrJobDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcHrJobDto.builder()
                .companyId(COMPANY_ID)
                .title("高级 Java 开发")
                .category("TECH")
                .description("5 年 Java 经验,熟悉 Spring Cloud")
                .fullJd("JD 全文本")
                .skillsJson("[\"Java\",\"Spring Cloud\"]")
                .salaryMin(new BigDecimal("20000"))
                .salaryMax(new BigDecimal("35000"))
                .location("北京")
                .build();
    }

    /** Test 1 */
    @Test
    @DisplayName("create - 成功创建 DRAFT JD,返回雪花 ID")
    void create_success() {
        when(jobMapper.insert(any(OpcHrJob.class))).thenReturn(1);

        Long id = jobService.create(sampleDto);

        assertThat(id).isNotNull().isPositive();
        ArgumentCaptor<OpcHrJob> captor = ArgumentCaptor.forClass(OpcHrJob.class);
        verify(jobMapper).insert(captor.capture());
        OpcHrJob saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("高级 Java 开发");
        assertThat(saved.getCategory()).isEqualTo("TECH");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getId()).isEqualTo(id);
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 缺 companyId 抛 ServiceException")
    void create_missingCompanyId_throws() {
        sampleDto.setCompanyId(null);
        assertThatThrownBy(() -> jobService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
        verifyNoInteractions(jobMapper);
    }

    /** Test 3 */
    @Test
    @DisplayName("create - 缺 title 抛 ServiceException")
    void create_missingTitle_throws() {
        sampleDto.setTitle(null);
        assertThatThrownBy(() -> jobService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("title");
        verifyNoInteractions(jobMapper);
    }

    /** Test 4 */
    @Test
    @DisplayName("create - 缺 category 抛 ServiceException")
    void create_missingCategory_throws() {
        sampleDto.setCategory(null);
        assertThatThrownBy(() -> jobService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("category");
        verifyNoInteractions(jobMapper);
    }

    /** Test 5 */
    @Test
    @DisplayName("update - DRAFT 状态可成功修改")
    void update_draft_succeeds() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).title("Old")
                .category("TECH").status("DRAFT").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);
        when(jobMapper.updateById(any(OpcHrJob.class))).thenReturn(1);

        int rows = jobService.update(JOB_ID, COMPANY_ID, sampleDto);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<OpcHrJob> captor = ArgumentCaptor.forClass(OpcHrJob.class);
        verify(jobMapper).updateById(captor.capture());
        OpcHrJob updated = captor.getValue();
        assertThat(updated.getTitle()).isEqualTo("高级 Java 开发");
        assertThat(updated.getSalaryMin()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(updated.getId()).isEqualTo(JOB_ID);
    }

    /** Test 6 */
    @Test
    @DisplayName("update - OPEN 状态不可修改")
    void update_openStatus_forbidden() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).title("Old").status("OPEN").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> jobService.update(JOB_ID, COMPANY_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DRAFT");
        verify(jobMapper, never()).updateById(any(OpcHrJob.class));
    }

    /** Test 7 */
    @Test
    @DisplayName("delete - DRAFT 状态可成功删除")
    void delete_draft_succeeds() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).status("DRAFT").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);
        when(jobMapper.deleteById(JOB_ID, COMPANY_ID)).thenReturn(1);

        int rows = jobService.delete(JOB_ID, COMPANY_ID);

        assertThat(rows).isEqualTo(1);
        verify(jobMapper).deleteById(JOB_ID, COMPANY_ID);
    }

    /** Test 8 */
    @Test
    @DisplayName("delete - OPEN 状态不可删除")
    void delete_openStatus_forbidden() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).status("OPEN").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> jobService.delete(JOB_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DRAFT");
        verify(jobMapper, never()).deleteById(anyLong(), anyLong());
    }

    /** Test 9 */
    @Test
    @DisplayName("detail - 存在返回实体")
    void detail_found() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).title("Title").status("OPEN").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);

        OpcHrJob result = jobService.detail(JOB_ID, COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(JOB_ID);
        assertThat(result.getTitle()).isEqualTo("Title");
    }

    /** Test 10 */
    @Test
    @DisplayName("detail - 不存在抛 ServiceException")
    void detail_notFound_throws() {
        when(jobMapper.selectById(999L, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> jobService.detail(999L, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("岗位不存在");
    }

    /** Test 11 */
    @Test
    @DisplayName("list - 按 companyId + status 过滤返回列表")
    void list_withFilters_returnsRows() {
        OpcHrJob job = OpcHrJob.builder().id(JOB_ID).companyId(COMPANY_ID).status("OPEN").build();
        when(jobMapper.selectList(eq(COMPANY_ID), eq("OPEN"), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(job));

        List<OpcHrJob> rows = jobService.list(COMPANY_ID, "OPEN");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("OPEN");
        verify(jobMapper).selectList(eq(COMPANY_ID), eq("OPEN"), anyInt(), anyInt());
    }

    /** Test 12 */
    @Test
    @DisplayName("publish - DRAFT 状态改为 OPEN 并设置 publishAt")
    void publish_draftToOpen() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).status("DRAFT").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);
        when(jobMapper.updateById(any(OpcHrJob.class))).thenReturn(1);

        jobService.publish(JOB_ID, COMPANY_ID);

        ArgumentCaptor<OpcHrJob> captor = ArgumentCaptor.forClass(OpcHrJob.class);
        verify(jobMapper).updateById(captor.capture());
        OpcHrJob after = captor.getValue();
        assertThat(after.getStatus()).isEqualTo("OPEN");
        assertThat(after.getPublishAt()).isNotNull();
    }

    /** Test 13 */
    @Test
    @DisplayName("publish - 已是 OPEN 状态禁止再次发布")
    void publish_alreadyOpen_forbidden() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).status("OPEN").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> jobService.publish(JOB_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DRAFT/PAUSED");
        verify(jobMapper, never()).updateById(any(OpcHrJob.class));
    }

    /** Test 14 */
    @Test
    @DisplayName("close - OPEN 状态改为 CLOSED 并设置 closeAt")
    void close_openToClosed() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).status("OPEN").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);
        when(jobMapper.updateById(any(OpcHrJob.class))).thenReturn(1);

        jobService.close(JOB_ID, COMPANY_ID);

        ArgumentCaptor<OpcHrJob> captor = ArgumentCaptor.forClass(OpcHrJob.class);
        verify(jobMapper).updateById(captor.capture());
        OpcHrJob after = captor.getValue();
        assertThat(after.getStatus()).isEqualTo("CLOSED");
        assertThat(after.getCloseAt()).isNotNull();
    }

    /** Test 15 */
    @Test
    @DisplayName("close - DRAFT 状态不可关闭")
    void close_draft_forbidden() {
        OpcHrJob existing = OpcHrJob.builder()
                .id(JOB_ID).companyId(COMPANY_ID).status("DRAFT").build();
        when(jobMapper.selectById(JOB_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> jobService.close(JOB_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("OPEN");
        verify(jobMapper, never()).updateById(any(OpcHrJob.class));
    }
}
