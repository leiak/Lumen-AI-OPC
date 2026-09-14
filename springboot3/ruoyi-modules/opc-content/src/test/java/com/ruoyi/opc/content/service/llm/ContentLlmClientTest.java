package com.ruoyi.opc.content.service.llm;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.content.feign.OpcContentAiCoreGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContentLlmClient 单测 (W74 Task 9 — 10 cases)。
 *
 * <p>覆盖: 4 场景(scene 一致性) + JSON fence 剥离 + 网关降级 + 入参校验。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContentLlmClient 单测 (10 cases)")
class ContentLlmClientTest {

    @Mock
    private OpcContentAiCoreGateway aiCoreGateway;

    private ContentLlmClient client;

    @BeforeEach
    void setUp() {
        client = new ContentLlmClient(aiCoreGateway);
    }

    // ===== 1. content_short_drama =====

    /** Test 1 */
    @Test
    @DisplayName("generateDrama - LLM 返回 JSON, scene=content_short_drama, temp=0.8")
    void generateDrama_success() {
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of(
                "content", "{\"synopsis\":\"...\"}", "model", "deepseek")));

        String result = client.generateDrama("现代都市爱情,女主律师");

        assertThat(result).isEqualTo("{\"synopsis\":\"...\"}");
        verifySceneAndTemp(ContentLlmPrompts.SCENE_SHORT_DRAMA,
                ContentLlmPrompts.TEMP_SHORT_DRAMA,
                ContentLlmPrompts.MAX_TOKENS_SHORT_DRAMA,
                ContentLlmPrompts.SHORT_DRAMA_SYSTEM,
                "请生成短剧剧本:");
    }

    /** Test 2 */
    @Test
    @DisplayName("generateDrama - ```json ... ``` fence 自动剥离")
    void generateDrama_markdownFence_stripped() {
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of(
                "content", "```json\n{\"synopsis\":\"x\"}\n```")));

        String result = client.generateDrama("p");

        assertThat(result).isEqualTo("{\"synopsis\":\"x\"}");
    }

    /** Test 3 */
    @Test
    @DisplayName("generateDrama - LLM 网关返回 R.fail 抛 ServiceException")
    void generateDrama_gatewayFails_throws() {
        when(aiCoreGateway.chat(any())).thenReturn(R.fail("LLM 服务暂时不可用"));

        assertThatThrownBy(() -> client.generateDrama("p"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("LLM");
    }

    // ===== 2. content_video_script =====

    /** Test 4 */
    @Test
    @DisplayName("generateVideo - scene=content_video_script, temp=0.6")
    void generateVideo_success() {
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of(
                "content", "{\"hook\":\"3秒钩子\",\"cta\":\"关注\"}")));

        String result = client.generateVideo("产品卖点");

        assertThat(result).contains("\"hook\":\"3秒钩子\"");
        verifySceneAndTemp(ContentLlmPrompts.SCENE_VIDEO_SCRIPT,
                ContentLlmPrompts.TEMP_VIDEO_SCRIPT,
                ContentLlmPrompts.MAX_TOKENS_VIDEO_SCRIPT,
                ContentLlmPrompts.VIDEO_SCRIPT_SYSTEM,
                "请生成视频脚本:");
    }

    /** Test 5 */
    @Test
    @DisplayName("generateVideo - promptInput 为空抛 ServiceException,不调 LLM")
    void generateVideo_blankPrompt_throws() {
        assertThatThrownBy(() -> client.generateVideo(""))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("promptInput");
        org.mockito.Mockito.verifyNoInteractions(aiCoreGateway);
    }

    // ===== 3. content_article =====

    /** Test 6 */
    @Test
    @DisplayName("generateArticle - scene=content_article, temp=0.5, 返回 Markdown")
    void generateArticle_success() {
        String md = "## 一级标题\n\n段落 1。";
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of("content", md)));

        String result = client.generateArticle("主题");

        assertThat(result).isEqualTo(md);
        verifySceneAndTemp(ContentLlmPrompts.SCENE_ARTICLE,
                ContentLlmPrompts.TEMP_ARTICLE,
                ContentLlmPrompts.MAX_TOKENS_ARTICLE,
                ContentLlmPrompts.ARTICLE_SYSTEM,
                "请生成图文文案:");
    }

    // ===== 4. content_platform_adapter =====

    /** Test 7 */
    @Test
    @DisplayName("adapt - scene=content_platform_adapter, 含原文 + 平台")
    void adapt_success() {
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of(
                "content", "{\"adapted_content\":\"x\",\"hashtags\":[\"#A\"]}")));

        String result = client.adapt("原文 Markdown", "DOUYIN");

        assertThat(result).contains("adapted_content");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiCoreGateway).chat(captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("scene")).isEqualTo(ContentLlmPrompts.SCENE_PLATFORM_ADAPTER);
        assertThat(payload.get("temperature")).isEqualTo(ContentLlmPrompts.TEMP_PLATFORM_ADAPTER);
        assertThat(payload.get("maxTokens")).isEqualTo(ContentLlmPrompts.MAX_TOKENS_PLATFORM_ADAPTER);

        // user 消息应包含原文 + 平台
        List<?> messages = (List<?>) payload.get("messages");
        Map<?, ?> userMsg = (Map<?, ?>) messages.get(1);
        assertThat(userMsg.get("content").toString()).contains("原文 Markdown").contains("DOUYIN");
    }

    /** Test 8 */
    @Test
    @DisplayName("adapt - targetPlatform 空抛 ServiceException")
    void adapt_blankPlatform_throws() {
        assertThatThrownBy(() -> client.adapt("原文", ""))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("targetPlatform");
    }

    // ===== 5. 异常路径 =====

    /** Test 9 */
    @Test
    @DisplayName("generateDrama - 网关返回 null 抛 ServiceException")
    void generateDrama_nullResponse_throws() {
        when(aiCoreGateway.chat(any())).thenReturn(null);

        assertThatThrownBy(() -> client.generateDrama("p"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂时不可用");
    }

    /** Test 10 */
    @Test
    @DisplayName("generateDrama - 网关返回 data 缺 content 抛 ServiceException")
    void generateDrama_missingContent_throws() {
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of("model", "deepseek")));

        assertThatThrownBy(() -> client.generateDrama("p"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("content");
    }

    // ===== 辅助 =====

    @SuppressWarnings("unchecked")
    private void verifySceneAndTemp(String expectedScene, double expectedTemp,
                                    int expectedMaxTokens, String expectedSystemPrefix,
                                    String expectedUserPrefix) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiCoreGateway).chat(captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("scene")).isEqualTo(expectedScene);
        assertThat(payload.get("temperature")).isEqualTo(expectedTemp);
        assertThat(payload.get("maxTokens")).isEqualTo(expectedMaxTokens);
        assertThat((List<?>) payload.get("messages")).hasSize(2);
        Map<String, Object> sysMsg = (Map<String, Object>) ((List<?>) payload.get("messages")).get(0);
        assertThat(sysMsg.get("role")).isEqualTo("system");
        assertThat(sysMsg.get("content").toString()).startsWith(expectedSystemPrefix.substring(0, 8));
        Map<String, Object> userMsg = (Map<String, Object>) ((List<?>) payload.get("messages")).get(1);
        assertThat(userMsg.get("role")).isEqualTo("user");
        assertThat(userMsg.get("content").toString()).startsWith(expectedUserPrefix);
    }
}