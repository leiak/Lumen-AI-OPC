package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import com.ruoyi.opc.hr.dto.OpcHrCandidateDto;
import com.ruoyi.opc.hr.mapper.OpcHrCandidateMapper;
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
@DisplayName("OpcHrCandidateService 单测 (10 cases)")
class OpcHrCandidateServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long CANDIDATE_ID = 456L;

    @Mock
    private OpcHrCandidateMapper candidateMapper;

    @InjectMocks
    private OpcHrCandidateServiceImpl candidateService;

    private OpcHrCandidateDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcHrCandidateDto.builder()
                .companyId(COMPANY_ID)
                .name("张三")
                .email("zhang.san@example.com")
                .phone("13800138000")
                .resumeUrl("https://example.com/resumes/456.pdf")
                .resumeMd("# 张三\nJava 开发 5 年")
                .tagsJson("[\"Java\",\"Spring Cloud\"]")
                .source("MANUAL")
                .build();
    }

    /** Test 1 */
    @Test
    @DisplayName("create - 完整 dto 成功创建,返回雪花 ID")
    void create_success() {
        when(candidateMapper.selectByEmail(eq(COMPANY_ID), eq("zhang.san@example.com"))).thenReturn(null);
        when(candidateMapper.insert(any(OpcHrCandidate.class))).thenReturn(1);

        Long id = candidateService.create(sampleDto);

        assertThat(id).isNotNull().isPositive();
        ArgumentCaptor<OpcHrCandidate> captor = ArgumentCaptor.forClass(OpcHrCandidate.class);
        verify(candidateMapper).insert(captor.capture());
        OpcHrCandidate saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("张三");
        assertThat(saved.getEmail()).isEqualTo("zhang.san@example.com");
        assertThat(saved.getResumeUrl()).isEqualTo("https://example.com/resumes/456.pdf");
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getSource()).isEqualTo("MANUAL");
        assertThat(saved.getId()).isEqualTo(id);
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 同公司邮箱重复抛 ServiceException")
    void create_duplicateEmail_throws() {
        OpcHrCandidate existing = OpcHrCandidate.builder()
                .id(999L).companyId(COMPANY_ID).email("zhang.san@example.com").build();
        when(candidateMapper.selectByEmail(eq(COMPANY_ID), eq("zhang.san@example.com")))
                .thenReturn(existing);

        assertThatThrownBy(() -> candidateService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同公司邮箱已存在");
        verify(candidateMapper, never()).insert(any(OpcHrCandidate.class));
    }

    /** Test 3 */
    @Test
    @DisplayName("create - 缺 companyId 抛 ServiceException")
    void create_missingCompanyId_throws() {
        sampleDto.setCompanyId(null);
        assertThatThrownBy(() -> candidateService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
        verifyNoInteractions(candidateMapper);
    }

    /** Test 4 */
    @Test
    @DisplayName("create - 缺 name 抛 ServiceException")
    void create_missingName_throws() {
        sampleDto.setName(null);
        assertThatThrownBy(() -> candidateService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("name");
        verifyNoInteractions(candidateMapper);
    }

    /** Test 5 */
    @Test
    @DisplayName("create - 缺 resumeUrl 抛 ServiceException")
    void create_missingResumeUrl_throws() {
        sampleDto.setResumeUrl(null);
        assertThatThrownBy(() -> candidateService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("resumeUrl");
        verifyNoInteractions(candidateMapper);
    }

    /** Test 6 */
    @Test
    @DisplayName("update - 成功更新 tags + parsed,基础信息保留")
    void update_success() {
        OpcHrCandidate existing = OpcHrCandidate.builder()
                .id(CANDIDATE_ID).companyId(COMPANY_ID)
                .name("张三").email("zhang.san@example.com")
                .resumeUrl("https://example.com/resumes/456.pdf")
                .build();
        when(candidateMapper.selectById(CANDIDATE_ID, COMPANY_ID)).thenReturn(existing);
        when(candidateMapper.updateById(any(OpcHrCandidate.class))).thenReturn(1);

        OpcHrCandidateDto updateDto = OpcHrCandidateDto.builder()
                .tagsJson("[\"Java\",\"Kafka\"]")
                .parsedJson("{\"skills\":[\"Java\",\"Kafka\"]}")
                .build();
        int rows = candidateService.update(CANDIDATE_ID, COMPANY_ID, updateDto);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<OpcHrCandidate> captor = ArgumentCaptor.forClass(OpcHrCandidate.class);
        verify(candidateMapper).updateById(captor.capture());
        OpcHrCandidate updated = captor.getValue();
        assertThat(updated.getTagsJson()).isEqualTo("[\"Java\",\"Kafka\"]");
        assertThat(updated.getParsedJson()).isEqualTo("{\"skills\":[\"Java\",\"Kafka\"]}");
        // 基础信息不变
        assertThat(updated.getName()).isEqualTo("张三");
        assertThat(updated.getEmail()).isEqualTo("zhang.san@example.com");
        assertThat(updated.getResumeUrl()).isEqualTo("https://example.com/resumes/456.pdf");
    }

    /** Test 7 */
    @Test
    @DisplayName("detail - 存在返回实体")
    void detail_found() {
        OpcHrCandidate existing = OpcHrCandidate.builder()
                .id(CANDIDATE_ID).companyId(COMPANY_ID).name("张三").build();
        when(candidateMapper.selectById(CANDIDATE_ID, COMPANY_ID)).thenReturn(existing);

        OpcHrCandidate result = candidateService.detail(CANDIDATE_ID, COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(CANDIDATE_ID);
        assertThat(result.getName()).isEqualTo("张三");
    }

    /** Test 8 */
    @Test
    @DisplayName("detail - 不存在抛 ServiceException")
    void detail_notFound_throws() {
        when(candidateMapper.selectById(999L, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> candidateService.detail(999L, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("候选人不存在");
    }

    /** Test 9 */
    @Test
    @DisplayName("delete - 存在候选人成功删除")
    void delete_success() {
        OpcHrCandidate existing = OpcHrCandidate.builder()
                .id(CANDIDATE_ID).companyId(COMPANY_ID).name("张三").build();
        when(candidateMapper.selectById(CANDIDATE_ID, COMPANY_ID)).thenReturn(existing);
        when(candidateMapper.deleteById(CANDIDATE_ID, COMPANY_ID)).thenReturn(1);

        int rows = candidateService.delete(CANDIDATE_ID, COMPANY_ID);

        assertThat(rows).isEqualTo(1);
        verify(candidateMapper).deleteById(CANDIDATE_ID, COMPANY_ID);
    }

    /** Test 10 */
    @Test
    @DisplayName("list - 返回分页(companyId 隔离)")
    void list_returnsAll() {
        OpcHrCandidate c1 = OpcHrCandidate.builder()
                .id(1L).companyId(COMPANY_ID).name("张三").build();
        OpcHrCandidate c2 = OpcHrCandidate.builder()
                .id(2L).companyId(COMPANY_ID).name("李四").build();
        when(candidateMapper.selectList(eq(COMPANY_ID), eq(0), anyInt()))
                .thenReturn(Collections.singletonList(c1));
        when(candidateMapper.selectList(eq(COMPANY_ID), eq(10), eq(50)))
                .thenReturn(List.of(c1, c2));

        // 默认 limit<=0 转 Integer.MAX_VALUE,offset 0
        List<OpcHrCandidate> all = candidateService.list(COMPANY_ID, 0, 0);
        assertThat(all).hasSize(1);
        verify(candidateMapper).selectList(eq(COMPANY_ID), eq(0), eq(Integer.MAX_VALUE));

        // 指定 limit=50,offset=10
        List<OpcHrCandidate> page = candidateService.list(COMPANY_ID, 10, 50);
        assertThat(page).hasSize(2);
        verify(candidateMapper).selectList(COMPANY_ID, 10, 50);
    }
}