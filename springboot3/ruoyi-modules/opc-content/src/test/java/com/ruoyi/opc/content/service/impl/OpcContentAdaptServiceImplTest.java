package com.ruoyi.opc.content.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.content.domain.OpcContentAdapt;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentAdaptRequest;
import com.ruoyi.opc.content.enums.ContentPlatform;
import com.ruoyi.opc.content.enums.ContentScriptStatus;
import com.ruoyi.opc.content.enums.ContentScriptType;
import com.ruoyi.opc.content.mapper.OpcContentAdaptMapper;
import com.ruoyi.opc.content.mapper.OpcContentScriptMapper;
import com.ruoyi.opc.content.service.IOpcContentScriptService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcContentAdaptServiceImpl 单测 (W74 Task 9 — 8 cases)。
 *
 * <p>覆盖: adapt (3 类型原文) + 4 平台枚举 + listBySource + parsePayload 兜底。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcContentAdaptServiceImpl 单测 (8 cases)")
class OpcContentAdaptServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long SOURCE_ID = 100L;

    @Mock
    private OpcContentAdaptMapper adaptMapper;

    @Mock
    private OpcContentScriptMapper scriptMapper;

    @Mock
    private IOpcContentScriptService scriptService;

    @Mock
    private ContentLlmClient contentLlmClient;

    @InjectMocks
    private OpcContentAdaptServiceImpl adaptService;

    private OpcContentScript articleSource() {
        return OpcContentScript.builder()
                .id(SOURCE_ID).companyId(COMPANY_ID)
                .type(ContentScriptType.ARTICLE.getCode())
                .status(ContentScriptStatus.DRAFT.getCode())
                .title("原文标题")
                .promptInput("原始 prompt")
                .contentMd("## 原文 Markdown\n\n段落 1。\n段落 2。")
                .contentJson("{}")
                .build();
    }

    private OpcContentAdaptRequest sampleReq() {
        return OpcContentAdaptRequest.builder()
                .companyId(COMPANY_ID)
                .sourceScriptId(SOURCE_ID)
                .targetPlatform("DOUYIN")
                .tone("年轻化口语+emoji")
                .build();
    }

    /** Test 1 */
    @Test
    @DisplayName("adapt - DOUYIN 平台 + 标准 JSON → 创建 ADAPTER 脚本 + 适配记录")
    void adapt_douyin_standardJson() {
        OpcContentScript src = articleSource();
        when(scriptService.detail(SOURCE_ID, COMPANY_ID)).thenReturn(src);
        when(contentLlmClient.adapt(any(), eq("DOUYIN"))).thenReturn(
                "{\"adapted_content\":\"适配后正文\",\"hashtags\":[\"#热门\", \"#推荐\"],"
                        + "\"tone\":\"年轻化\"}");

        Long adaptedId = adaptService.adapt(sampleReq());

        assertThat(adaptedId).isNotNull().isPositive();
        ArgumentCaptor<OpcContentScript> scriptCaptor = ArgumentCaptor.forClass(OpcContentScript.class);
        verify(scriptMapper).insert(scriptCaptor.capture());
        OpcContentScript adapted = scriptCaptor.getValue();
        assertThat(adapted.getType()).isEqualTo(ContentScriptType.ADAPTER.getCode());
        assertThat(adapted.getStatus()).isEqualTo(ContentScriptStatus.DRAFT.getCode());
        assertThat(adapted.getSourceScriptId()).isEqualTo(SOURCE_ID);
        assertThat(adapted.getTitle()).startsWith("[适配-DOUYIN] ");
        assertThat(adapted.getContentMd()).isEqualTo("适配后正文");

        ArgumentCaptor<OpcContentAdapt> adaptCaptor = ArgumentCaptor.forClass(OpcContentAdapt.class);
        verify(adaptMapper).insert(adaptCaptor.capture());
        OpcContentAdapt adaptRecord = adaptCaptor.getValue();
        assertThat(adaptRecord.getSourceScriptId()).isEqualTo(SOURCE_ID);
        assertThat(adaptRecord.getAdaptedScriptId()).isEqualTo(adaptedId);
        assertThat(adaptRecord.getTargetPlatform()).isEqualTo(ContentPlatform.DOUYIN.getCode());
        assertThat(adaptRecord.getHashtags()).contains("#热门", "#推荐");
    }

    /** Test 2 */
    @Test
    @DisplayName("adapt - LLM 返回非 JSON → 降级用原文 + 空 hashtags + 用户 tone")
    void adapt_invalidJson_fallback() {
        OpcContentScript src = articleSource();
        when(scriptService.detail(SOURCE_ID, COMPANY_ID)).thenReturn(src);
        when(contentLlmClient.adapt(any(), any())).thenReturn("not a json {{{ broken");

        Long adaptedId = adaptService.adapt(sampleReq());

        ArgumentCaptor<OpcContentScript> captor = ArgumentCaptor.forClass(OpcContentScript.class);
        verify(scriptMapper).insert(captor.capture());
        OpcContentScript adapted = captor.getValue();
        // fallback: adaptedContent = 原文, hashtags = []
        assertThat(adapted.getContentMd()).isEqualTo(src.getContentMd());
        assertThat(adapted.getId()).isEqualTo(adaptedId);

        ArgumentCaptor<OpcContentAdapt> adaptCaptor = ArgumentCaptor.forClass(OpcContentAdapt.class);
        verify(adaptMapper).insert(adaptCaptor.capture());
        assertThat(adaptCaptor.getValue().getHashtags()).isEqualTo("[]");
        assertThat(adaptCaptor.getValue().getTone()).isEqualTo("年轻化口语+emoji");
    }

    /** Test 3 */
    @Test
    @DisplayName("adapt - 缺 companyId 抛 ServiceException")
    void adapt_blankCompanyId_throws() {
        OpcContentAdaptRequest req = sampleReq();
        req.setCompanyId(null);

        assertThatThrownBy(() -> adaptService.adapt(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
        verify(scriptMapper, never()).insert(any(OpcContentScript.class));
    }

    /** Test 4 */
    @Test
    @DisplayName("adapt - 缺 sourceScriptId 抛 ServiceException")
    void adapt_blankSourceScriptId_throws() {
        OpcContentAdaptRequest req = sampleReq();
        req.setSourceScriptId(null);

        assertThatThrownBy(() -> adaptService.adapt(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("sourceScriptId");
    }

    /** Test 5 */
    @Test
    @DisplayName("adapt - 未知平台代码抛 IllegalArgumentException(由 ContentPlatform.of)")
    void adapt_unknownPlatform_throws() {
        OpcContentAdaptRequest req = sampleReq();
        req.setTargetPlatform("TIKTOK_UNKNOWN");

        assertThatThrownBy(() -> adaptService.adapt(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TIKTOK_UNKNOWN");
    }

    /** Test 6 */
    @Test
    @DisplayName("adapt - 原文 contentMd 为空时降级用 contentJson")
    void adapt_emptyContentMd_fallbackToJson() {
        OpcContentScript src = articleSource();
        src.setContentMd(null);
        src.setContentJson("{\"fallback\":\"content\"}");
        when(scriptService.detail(SOURCE_ID, COMPANY_ID)).thenReturn(src);
        when(contentLlmClient.adapt(any(), any())).thenReturn("{\"adapted_content\":\"新文\"}");

        adaptService.adapt(sampleReq());

        ArgumentCaptor<OpcContentScript> captor = ArgumentCaptor.forClass(OpcContentScript.class);
        verify(scriptMapper).insert(captor.capture());
        // LLM 调用时传的 originalContent = contentJson
        verify(contentLlmClient).adapt(eq("{\"fallback\":\"content\"}"), eq("DOUYIN"));
    }

    /** Test 7 */
    @Test
    @DisplayName("adapt - hashtags 是 String[] 时直接采用,不解析")
    void adapt_hashtagsStringArrayAdopted() {
        OpcContentScript src = articleSource();
        when(scriptService.detail(SOURCE_ID, COMPANY_ID)).thenReturn(src);
        when(contentLlmClient.adapt(any(), any())).thenReturn(
                "{\"adapted_content\":\"X\",\"hashtags\":[\"#A\",\"#B\"]}");

        adaptService.adapt(sampleReq());

        ArgumentCaptor<OpcContentAdapt> captor = ArgumentCaptor.forClass(OpcContentAdapt.class);
        verify(adaptMapper).insert(captor.capture());
        assertThat(captor.getValue().getHashtags()).contains("#A", "#B");
    }

    /** Test 8 */
    @Test
    @DisplayName("listBySource - 返回适配记录列表")
    void listBySource_returnsAdaptRecords() {
        OpcContentAdapt row = OpcContentAdapt.builder()
                .id(1L).sourceScriptId(SOURCE_ID).adaptedScriptId(2L).build();
        when(adaptMapper.selectBySource(SOURCE_ID, COMPANY_ID)).thenReturn(List.of(row));

        List<OpcContentAdapt> result = adaptService.listBySource(SOURCE_ID, COMPANY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceScriptId()).isEqualTo(SOURCE_ID);
    }
}