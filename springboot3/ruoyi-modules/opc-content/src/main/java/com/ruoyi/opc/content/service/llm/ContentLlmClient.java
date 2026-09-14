package com.ruoyi.opc.content.service.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.content.feign.OpcContentAiCoreGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * opc-content LLM 客户端 (W74 Task 6 — 真实接入 opc-ai-core 网关)。
 *
 * <p>封装 {@link OpcContentAiCoreGateway}, 对外暴露 4 个业务方法对应 4 个 prompt 场景:
 * <ul>
 *   <li>{@link #generateDrama} — scene {@code content_short_drama}</li>
 *   <li>{@link #generateVideo} — scene {@code content_video_script}</li>
 *   <li>{@link #generateArticle} — scene {@code content_article}</li>
 *   <li>{@link #adapt} — scene {@code content_platform_adapter}</li>
 * </ul>
 *
 * <p>设计要点:
 * <ol>
 *   <li><b>显式失败</b> — 网关返回 R.fail() 或响应缺失 content 字段时, 本类抛
 *       {@link ServiceException} 让业务层感知。绝不静默吞错。</li>
 *   <li><b>降级可观测</b> — 所有失败路径都打 warn 日志, 包含 scene + 输入摘要。</li>
 *   <li><b>参数校验</b> — 关键入参 (promptInput / originalContent / targetPlatform) 提前校验,
 *       避免无意义的 LLM 调用浪费 token。</li>
 *   <li><b>Jackson 单例</b> — ObjectMapper 复用, 避免每次 new 注入字段元数据。</li>
 *   <li><b>温度/maxTokens</b> — 来自 {@link ContentLlmPrompts} 常量, 调整时一处生效。</li>
 * </ol>
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentLlmClient {

    private final OpcContentAiCoreGateway aiCoreGateway;

    /** Jackson 单例: reusedFeatures 避免每次构建 mapper 时初始化 feature。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // ============================================================
    // 1. content_short_drama — 短剧剧本生成
    // ============================================================

    /**
     * 根据主题/角色/集数生成短剧剧本 JSON。
     *
     * @param promptInput 主题/角色/集数 等业务描述 (必填, 非空)
     * @return 严格 JSON 字符串 (符合 {@link ContentLlmPrompts#SHORT_DRAMA_SYSTEM} schema)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String generateDrama(String promptInput) {
        if (promptInput == null || promptInput.isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }

        String content = chatOnce(
                ContentLlmPrompts.SCENE_SHORT_DRAMA,
                ContentLlmPrompts.SHORT_DRAMA_SYSTEM,
                "请生成短剧剧本:\n\n" + promptInput,
                ContentLlmPrompts.TEMP_SHORT_DRAMA,
                ContentLlmPrompts.MAX_TOKENS_SHORT_DRAMA,
                List.of("promptLen=" + promptInput.length()));

        if (content == null || content.isBlank()) {
            log.warn("[opc-content] generateDrama 响应 content 为空 scene=content_short_drama");
            throw new ServiceException("LLM 生成的短剧剧本为空, 请重试");
        }
        return stripMarkdownFence(content);
    }

    // ============================================================
    // 2. content_video_script — 视频脚本生成
    // ============================================================

    /**
     * 根据业务描述生成 30-90 秒视频脚本 JSON。
     *
     * @param promptInput 主题/卖点/目标受众 等业务描述 (必填, 非空)
     * @return 严格 JSON 字符串 (符合 {@link ContentLlmPrompts#VIDEO_SCRIPT_SYSTEM} schema)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String generateVideo(String promptInput) {
        if (promptInput == null || promptInput.isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }

        String content = chatOnce(
                ContentLlmPrompts.SCENE_VIDEO_SCRIPT,
                ContentLlmPrompts.VIDEO_SCRIPT_SYSTEM,
                "请生成视频脚本:\n\n" + promptInput,
                ContentLlmPrompts.TEMP_VIDEO_SCRIPT,
                ContentLlmPrompts.MAX_TOKENS_VIDEO_SCRIPT,
                List.of("promptLen=" + promptInput.length()));

        if (content == null || content.isBlank()) {
            log.warn("[opc-content] generateVideo 响应 content 为空 scene=content_video_script");
            throw new ServiceException("LLM 生成的视频脚本为空, 请重试");
        }
        return stripMarkdownFence(content);
    }

    // ============================================================
    // 3. content_article — 图文文案生成
    // ============================================================

    /**
     * 根据业务描述生成 500-1500 字 Markdown 图文文案。
     *
     * @param promptInput 主题/大纲/受众 等业务描述 (必填, 非空)
     * @return Markdown 字符串 (符合 {@link ContentLlmPrompts#ARTICLE_SYSTEM} 格式)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String generateArticle(String promptInput) {
        if (promptInput == null || promptInput.isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }

        String content = chatOnce(
                ContentLlmPrompts.SCENE_ARTICLE,
                ContentLlmPrompts.ARTICLE_SYSTEM,
                "请生成图文文案:\n\n" + promptInput,
                ContentLlmPrompts.TEMP_ARTICLE,
                ContentLlmPrompts.MAX_TOKENS_ARTICLE,
                List.of("promptLen=" + promptInput.length()));

        if (content == null || content.isBlank()) {
            log.warn("[opc-content] generateArticle 响应 content 为空 scene=content_article");
            throw new ServiceException("LLM 生成的图文文案为空, 请重试");
        }
        // ARTICLE 是 Markdown 文本, 不强求剥离 fence, 但 LLM 偶尔包 ```markdown 包裹, 这里剥一次
        return stripMarkdownFence(content);
    }

    // ============================================================
    // 4. content_platform_adapter — 平台适配
    // ============================================================

    /**
     * 平台适配 — 原文 + 目标平台 → 适配后内容 JSON。
     *
     * @param originalContent 原文 Markdown 或纯文本 (必填, 非空)
     * @param targetPlatform  目标平台 DOUYIN/WECHAT 等 (必填, 非空)
     * @return JSON 字符串 (符合 {@link ContentLlmPrompts#PLATFORM_ADAPTER_SYSTEM} schema)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String adapt(String originalContent, String targetPlatform) {
        if (originalContent == null || originalContent.isBlank()) {
            throw new ServiceException("originalContent 不能为空");
        }
        if (targetPlatform == null || targetPlatform.isBlank()) {
            throw new ServiceException("targetPlatform 不能为空");
        }

        String userInput = String.format(
                "=== 原文 ===\n%s\n\n=== 目标平台 ===\n%s",
                originalContent, targetPlatform);

        String content = chatOnce(
                ContentLlmPrompts.SCENE_PLATFORM_ADAPTER,
                ContentLlmPrompts.PLATFORM_ADAPTER_SYSTEM,
                userInput,
                ContentLlmPrompts.TEMP_PLATFORM_ADAPTER,
                ContentLlmPrompts.MAX_TOKENS_PLATFORM_ADAPTER,
                List.of("origLen=" + originalContent.length(), "platform=" + targetPlatform));

        if (content == null || content.isBlank()) {
            log.warn("[opc-content] adapt 响应 content 为空 platform={}", targetPlatform);
            throw new ServiceException("LLM 平台适配结果为空, 请重试");
        }
        return stripMarkdownFence(content);
    }

    // ============================================================
    // 内部: 统一 chat 调用
    // ============================================================

    /**
     * 构造 payload + 调用网关 + 提取 content。
     *
     * @return content 字符串 (不含 Markdown fence); 网关不可用 / 业务失败 / 响应缺失 content 时抛 {@link ServiceException}
     */
    private String chatOnce(String scene, String systemPrompt, String userInput,
                            double temperature, int maxTokens, List<String> contextTags) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", scene);
        payload.put("temperature", temperature);
        payload.put("maxTokens", maxTokens);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userInput)));

        log.info("[opc-content] LLM 调用 scene={} ctx={}", scene, contextTags);
        R<Map<String, Object>> resp = aiCoreGateway.chat(payload);
        if (resp == null) {
            log.warn("[opc-content] LLM 响应为 null scene={}", scene);
            throw new ServiceException("LLM 服务暂时不可用");
        }
        // 直接判断 R.SUCCESS (200) 避免 R.isError 内部对 Boolean 自动拆箱时的潜在 NPE / 弃用警告
        if (resp.getCode() != R.SUCCESS) {
            log.warn("[opc-content] LLM 业务失败 scene={} code={} msg={}",
                    scene, resp.getCode(), resp.getMsg());
            throw new ServiceException("LLM 业务失败: " + resp.getMsg());
        }
        Map<String, Object> data = resp.getData();
        if (data == null) {
            log.warn("[opc-content] LLM 响应 data=null scene={}", scene);
            throw new ServiceException("LLM 响应为空");
        }
        Object content = data.get("content");
        if (content == null) {
            log.warn("[opc-content] LLM 响应 content=null scene={} keys={}", scene, data.keySet());
            throw new ServiceException("LLM 响应无 content 字段");
        }
        return content.toString();
    }

    /** 去掉 LLM 偶尔返回的 Markdown ```json ... ``` 或 ```markdown ... ``` 包裹。 */
    private static String stripMarkdownFence(String content) {
        if (content == null) {
            return null;
        }
        String s = content.trim();
        if (!s.startsWith("```")) {
            return s;
        }
        int firstLineEnd = s.indexOf('\n');
        int lastFence = s.lastIndexOf("```");
        if (firstLineEnd > 0 && lastFence > firstLineEnd) {
            return s.substring(firstLineEnd + 1, lastFence).trim();
        }
        return s;
    }
}
