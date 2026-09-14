package com.ruoyi.opc.hr.service.llm;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.dto.HrScoreResult;
import com.ruoyi.opc.hr.feign.OpcHrAiCoreGateway;
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
 * HrLlmClient 单测 (W73 Task 10 — 8 cases)。
 *
 * <p>覆盖: 三场景调用 + 降级解析 + 入参校验 + 显式失败语义。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HrLlmClient 单测 (8 cases)")
class HrLlmClientTest {

    @Mock
    private OpcHrAiCoreGateway aiCoreGateway;

    private HrLlmClient client;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        client = new HrLlmClient(aiCoreGateway);
    }

    // ===== 1. hr_jd_generate =====

    /** Test 1 */
    @Test
    @DisplayName("generateJd - LLM 返回 content,直接 trim 返回")
    void generateJd_success() {
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of(
                "content", "  【岗位概述】高级 Java 开发  ", "model", "deepseek",
                "tokenTotal", 500, "success", true)));

        String jd = client.generateJd("高级 Java 开发", "TECH", "5 年 Java 经验");

        assertThat(jd).isEqualTo("【岗位概述】高级 Java 开发");
        verifySceneAndTemp("hr_jd_generate", HrLlmPrompts.TEMP_JD_GENERATE);
    }

    /** Test 2 */
    @Test
    @DisplayName("generateJd - LLM 网关降级返回 R.fail 抛 ServiceException")
    void generateJd_gatewayFails_throws() {
        when(aiCoreGateway.chat(any())).thenReturn(R.fail("LLM 服务暂时不可用，请稍后重试"));

        assertThatThrownBy(() -> client.generateJd("t", "c", "d"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("LLM 生成的 JD 为空");
    }

    /** Test 3 */
    @Test
    @DisplayName("generateJd - title 空抛 ServiceException,不调 LLM")
    void generateJd_blankTitle_throws() {
        assertThatThrownBy(() -> client.generateJd("  ", "c", "d"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("title");
    }

    // ===== 2. hr_resume_parse =====

    /** Test 4 */
    @Test
    @DisplayName("parseResume - LLM 返回纯 JSON,原样返回")
    void parseResume_pureJson_returns() {
        String json = "{\"name\":\"张三\",\"email\":\"a@b.com\",\"skills\":[\"Java\"]}";
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of("content", json)));

        String parsed = client.parseResume("# 张三\n5 年 Java");

        assertThat(parsed).isEqualTo(json);
        verifySceneAndTemp("hr_resume_parse", HrLlmPrompts.TEMP_RESUME_PARSE);
    }

    /** Test 5 */
    @Test
    @DisplayName("parseResume - LLM 包 ```json ... ``` fence,自动剥掉")
    void parseResume_markdownFence_stripped() {
        String fenced = "```json\n{\"name\":\"李四\"}\n```";
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of("content", fenced)));

        String parsed = client.parseResume("简历内容");

        assertThat(parsed).isEqualTo("{\"name\":\"李四\"}");
    }

    /** Test 6 */
    @Test
    @DisplayName("parseResume - LLM 降级 R.fail 抛 ServiceException")
    void parseResume_gatewayFails_throws() {
        when(aiCoreGateway.chat(any())).thenReturn(R.fail("LLM 不可用"));

        assertThatThrownBy(() -> client.parseResume("resume"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("LLM 解析简历失败");
    }

    // ===== 3. hr_candidate_score =====

    /** Test 7 */
    @Test
    @DisplayName("scoreCandidate - LLM 返回标准 JSON,正确解析为 HrScoreResult")
    void scoreCandidate_validJson_parsed() {
        String json = "{\"score\":85,\"reason\":\"5年Java经验匹配JD\",\"highlights\":[\"Spring Cloud\"],\"gaps\":[\"未提及K8s\"]}";
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of("content", json)));

        HrScoreResult result = client.scoreCandidate("JD 全文", "{\"name\":\"张三\"}");

        assertThat(result.getScore()).isEqualTo(85);
        assertThat(result.getReason()).contains("5年Java");
        assertThat(result.safeHighlights()).containsExactly("Spring Cloud");
        assertThat(result.safeGaps()).containsExactly("未提及K8s");
        verifySceneAndTemp("hr_candidate_score", HrLlmPrompts.TEMP_CANDIDATE_SCORE);
    }

    /** Test 8 */
    @Test
    @DisplayName("scoreCandidate - LLM 返回非 JSON,降级 score=50 + reason=原文")
    void scoreCandidate_invalidJson_fallbackToFifty() {
        when(aiCoreGateway.chat(any())).thenReturn(R.ok(Map.of(
                "content", "这位候选人很优秀,我给 85 分")));

        HrScoreResult result = client.scoreCandidate("JD", "{\"name\":\"张三\"}");

        assertThat(result.getScore()).isEqualTo(50);
        assertThat(result.getReason()).startsWith("[LLM 输出非 JSON,降级默认分]");
        assertThat(result.getReason()).contains("85 分");
    }

    // ===== 辅助 =====

    /** 验证传给网关的 payload 包含正确 scene + temperature */
    @SuppressWarnings("unchecked")
    private void verifySceneAndTemp(String expectedScene, double expectedTemp) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiCoreGateway).chat(captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("scene")).isEqualTo(expectedScene);
        assertThat(payload.get("temperature")).isEqualTo(expectedTemp);
        assertThat((List<?>) payload.get("messages")).hasSize(2);
        Map<String, Object> sysMsg = (Map<String, Object>) ((List<?>) payload.get("messages")).get(0);
        assertThat(sysMsg.get("role")).isEqualTo("system");
        assertThat(sysMsg.get("content").toString()).isNotBlank();
    }
}