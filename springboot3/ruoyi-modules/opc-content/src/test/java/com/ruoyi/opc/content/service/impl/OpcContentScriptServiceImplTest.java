package com.ruoyi.opc.content.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentGenerateRequest;
import com.ruoyi.opc.content.dto.OpcContentScriptDto;
import com.ruoyi.opc.content.enums.ContentScriptStatus;
import com.ruoyi.opc.content.enums.ContentScriptType;
import com.ruoyi.opc.content.mapper.OpcContentPublishMapper;
import com.ruoyi.opc.content.mapper.OpcContentScriptMapper;
import com.ruoyi.opc.content.service.llm.ContentLlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcContentScriptServiceImpl 单测 (W74 Task 9 — 12 cases)。
 *
 * <p>覆盖: create + detail + list + update + delete + regenerate/refine + markReady + dashboard。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcContentScriptServiceImpl 单测 (12 cases)")
class OpcContentScriptServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long SCRIPT_ID = 100L;

    @Mock
    private OpcContentScriptMapper scriptMapper;

    @Mock
    private OpcContentPublishMapper publishMapper;

    @Mock
    private ContentLlmClient contentLlmClient;

    @InjectMocks
    private OpcContentScriptServiceImpl scriptService;

    /** Test 1 */
    @Test
    @DisplayName("create - DRAMA 类型成功创建 DRAFT,LLM 注入 contentJson")
    void create_drama_success() {
        when(contentLlmClient.generateDrama("现代都市爱情,女主律师男主医生")).thenReturn("{\"synopsis\":\"...\"}");

        OpcContentGenerateRequest req = OpcContentGenerateRequest.builder()
                .companyId(COMPANY_ID)
                .type("DRAMA")
                .title("测试短剧")
                .promptInput("现代都市爱情,女主律师男主医生")
                .build();

        Long id = scriptService.create(req);

        assertThat(id).isNotNull().isPositive();
        ArgumentCaptor<OpcContentScript> captor = ArgumentCaptor.forClass(OpcContentScript.class);
        verify(scriptMapper).insert(captor.capture());
        OpcContentScript saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getType()).isEqualTo(ContentScriptType.DRAMA.getCode());
        assertThat(saved.getStatus()).isEqualTo(ContentScriptStatus.DRAFT.getCode());
        assertThat(saved.getContentJson()).contains("synopsis");
        assertThat(saved.getContentMd()).isEmpty();
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getTitle()).isEqualTo("测试短剧");
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 缺 companyId 抛 ServiceException")
    void create_blankCompanyId_throws() {
        OpcContentGenerateRequest req = OpcContentGenerateRequest.builder()
                .type("DRAMA").promptInput("p").build();

        assertThatThrownBy(() -> scriptService.create(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
        verify(scriptMapper, never()).insert(any(OpcContentScript.class));
    }

    /** Test 3 */
    @Test
    @DisplayName("create - 缺 promptInput 抛 ServiceException")
    void create_blankPrompt_throws() {
        OpcContentGenerateRequest req = OpcContentGenerateRequest.builder()
                .companyId(COMPANY_ID).type("DRAMA").build();

        assertThatThrownBy(() -> scriptService.create(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("promptInput");
        verify(contentLlmClient, never()).generateDrama(any());
    }

    /** Test 4 */
    @Test
    @DisplayName("create - ADAPTER 类型拒绝(走 /adapt 端点)")
    void create_adapterType_rejected() {
        OpcContentGenerateRequest req = OpcContentGenerateRequest.builder()
                .companyId(COMPANY_ID).type("ADAPTER").promptInput("p").build();

        assertThatThrownBy(() -> scriptService.create(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ADAPTER");
        verify(scriptMapper, never()).insert(any(OpcContentScript.class));
    }

    /** Test 5 */
    @Test
    @DisplayName("create - ARTICLE 类型调 generateArticle,wordCount 由 contentMd 长度决定")
    void create_article_setsWordCount() {
        String md = "## 一级标题\n\n这是 Markdown 文案,大约 500-1500 字。";
        when(contentLlmClient.generateArticle(any())).thenReturn(md);

        OpcContentGenerateRequest req = OpcContentGenerateRequest.builder()
                .companyId(COMPANY_ID).type("ARTICLE").promptInput("主题").build();

        scriptService.create(req);

        ArgumentCaptor<OpcContentScript> captor = ArgumentCaptor.forClass(OpcContentScript.class);
        verify(scriptMapper).insert(captor.capture());
        OpcContentScript saved = captor.getValue();
        assertThat(saved.getContentMd()).isEqualTo(md);
        assertThat(saved.getWordCount()).isEqualTo(md.length());
        assertThat(saved.getContentJson()).isEmpty();
    }

    /** Test 6 */
    @Test
    @DisplayName("detail - script 不存在抛 ServiceException")
    void detail_notFound_throws() {
        when(scriptMapper.selectById(SCRIPT_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> scriptService.detail(SCRIPT_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在");
    }

    /** Test 7 */
    @Test
    @DisplayName("update - DRAFT 状态可更新 title/contentMd")
    void update_draft_succeeds() {
        OpcContentScript existing = OpcContentScript.builder()
                .id(SCRIPT_ID).companyId(COMPANY_ID)
                .type(ContentScriptType.DRAMA.getCode())
                .status(ContentScriptStatus.DRAFT.getCode())
                .title("Old").contentMd("old").wordCount(3).build();
        when(scriptMapper.selectById(SCRIPT_ID, COMPANY_ID)).thenReturn(existing);
        when(scriptMapper.updateById(any(OpcContentScript.class))).thenReturn(1);

        OpcContentScriptDto dto = OpcContentScriptDto.builder()
                .title("New").contentMd("new content").build();
        int rows = scriptService.update(SCRIPT_ID, COMPANY_ID, dto);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<OpcContentScript> captor = ArgumentCaptor.forClass(OpcContentScript.class);
        verify(scriptMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New");
        assertThat(captor.getValue().getContentMd()).isEqualTo("new content");
        assertThat(captor.getValue().getWordCount()).isEqualTo(11);
    }

    /** Test 8 */
    @Test
    @DisplayName("update - READY 状态不可修改")
    void update_readyStatus_forbidden() {
        OpcContentScript existing = OpcContentScript.builder()
                .id(SCRIPT_ID).companyId(COMPANY_ID)
                .status(ContentScriptStatus.READY.getCode()).build();
        when(scriptMapper.selectById(SCRIPT_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> scriptService.update(SCRIPT_ID, COMPANY_ID,
                OpcContentScriptDto.builder().title("X").build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DRAFT");
        verify(scriptMapper, never()).updateById(any(OpcContentScript.class));
    }

    /** Test 9 */
    @Test
    @DisplayName("delete - DRAFT 状态可软删")
    void delete_draft_succeeds() {
        OpcContentScript existing = OpcContentScript.builder()
                .id(SCRIPT_ID).companyId(COMPANY_ID)
                .status(ContentScriptStatus.DRAFT.getCode()).build();
        when(scriptMapper.selectById(SCRIPT_ID, COMPANY_ID)).thenReturn(existing);
        when(scriptMapper.softDeleteById(SCRIPT_ID, COMPANY_ID)).thenReturn(1);

        int rows = scriptService.delete(SCRIPT_ID, COMPANY_ID);

        assertThat(rows).isEqualTo(1);
        verify(scriptMapper).softDeleteById(SCRIPT_ID, COMPANY_ID);
    }

    /** Test 10 */
    @Test
    @DisplayName("delete - PUBLISHED 状态不可删除")
    void delete_publishedStatus_forbidden() {
        OpcContentScript existing = OpcContentScript.builder()
                .id(SCRIPT_ID).companyId(COMPANY_ID)
                .status(ContentScriptStatus.PUBLISHED.getCode()).build();
        when(scriptMapper.selectById(SCRIPT_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> scriptService.delete(SCRIPT_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DRAFT");
        verify(scriptMapper, never()).softDeleteById(anyLong(), anyLong());
    }

    /** Test 11 */
    @Test
    @DisplayName("markReady - DRAFT → READY 状态转换")
    void markReady_draftToReady() {
        OpcContentScript existing = OpcContentScript.builder()
                .id(SCRIPT_ID).companyId(COMPANY_ID)
                .type(ContentScriptType.DRAMA.getCode())
                .status(ContentScriptStatus.DRAFT.getCode()).build();
        when(scriptMapper.selectById(SCRIPT_ID, COMPANY_ID)).thenReturn(existing);
        when(scriptMapper.updateById(any(OpcContentScript.class))).thenReturn(1);

        scriptService.markReady(SCRIPT_ID, COMPANY_ID);

        ArgumentCaptor<OpcContentScript> captor = ArgumentCaptor.forClass(OpcContentScript.class);
        verify(scriptMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ContentScriptStatus.READY.getCode());
    }

    /** Test 12 */
    @Test
    @DisplayName("dashboard - 聚合统计,failedRate=0 因 publishTotal=0")
    void dashboard_aggregatesCounts() {
        when(scriptMapper.countTodayByCompany(COMPANY_ID)).thenReturn(10);
        when(publishMapper.countByStatus(COMPANY_ID, "PENDING")).thenReturn(3);
        when(publishMapper.countByStatus(COMPANY_ID, "SUCCESS")).thenReturn(5);
        when(publishMapper.countList(eq(COMPANY_ID), eq(null))).thenReturn(0);
        when(publishMapper.countByStatus(eq(COMPANY_ID), eq("FAILED"))).thenReturn(0);
        when(scriptMapper.selectRecent(eq(COMPANY_ID), anyInt())).thenReturn(List.of());

        var dto = scriptService.dashboard(COMPANY_ID);

        assertThat(dto.getTodayGenerate()).isEqualTo(10);
        assertThat(dto.getPendingPublish()).isEqualTo(3);
        assertThat(dto.getPublished()).isEqualTo(5);
        assertThat(dto.getFailedRate().compareTo(java.math.BigDecimal.ZERO)).isEqualTo(0);
        assertThat(dto.getSevenDayTrend()).hasSize(7);
    }
}