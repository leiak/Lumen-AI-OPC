package com.ruoyi.opc.content.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内容生成 LLM Client(W74 Task 4 占位 — 真实接入在 Task 6)。
 *
 * <p>Task 6 将:
 * <ul>
 *   <li>注入 {@code OpcContentAiCoreGateway}(Feign → opc-ai-core:9301 /opc/llm/chat)</li>
 *   <li>封装 4 个 scene: SCENE_SHORT_DRAMA / VIDEO_SCRIPT / ARTICLE / PLATFORM_ADAPTER</li>
 *   <li>调 {@link ContentLlmPrompts} 的 system prompt + temperature + maxTokens</li>
 * </ul>
 *
 * <p>Task 4 阶段: 全部方法 stub 返回 mock 字符串,让 ServiceImpl 可编译。
 *
 * @author OAC
 */
@Slf4j
@Component
public class ContentLlmClient {

    /** W74 Task 4: 占位 - Task 6 真实接入 opc-ai-core */
    public String generateDrama(String promptInput) {
        log.warn("[opc-content] ContentLlmClient.generateDrama 暂用占位 promptLen={} (Task 6 接入)",
                promptInput == null ? 0 : promptInput.length());
        // TODO Task 6: 调 opc-ai-core /opc/llm/chat scene=content_short_drama
        return "{\"synopsis\":\"TODO\",\"total_episodes\":1,\"scenes\":[]}";
    }

    /** W74 Task 4: 占位 - Task 6 真实接入 opc-ai-core */
    public String generateVideo(String promptInput) {
        log.warn("[opc-content] ContentLlmClient.generateVideo 暂用占位 promptLen={} (Task 6 接入)",
                promptInput == null ? 0 : promptInput.length());
        // TODO Task 6: 调 opc-ai-core /opc/llm/chat scene=content_video_script
        return "{\"hook\":\"TODO\",\"body\":[],\"cta\":\"TODO\"}";
    }

    /** W74 Task 4: 占位 - Task 6 真实接入 opc-ai-core */
    public String generateArticle(String promptInput) {
        log.warn("[opc-content] ContentLlmClient.generateArticle 暂用占位 promptLen={} (Task 6 接入)",
                promptInput == null ? 0 : promptInput.length());
        // TODO Task 6: 调 opc-ai-core /opc/llm/chat scene=content_article
        return "# TODO 暂用占位 Markdown\n\n请等待 Task 6 接入 opc-ai-core...";
    }

    /**
     * 平台适配 — 原文 → 目标平台风格。
     * W74 Task 4: 占位 - Task 6 真实接入 opc-ai-core
     */
    public String adapt(String originalContent, String targetPlatform) {
        log.warn("[opc-content] ContentLlmClient.adapt 暂用占位 origLen={} platform={} (Task 6 接入)",
                originalContent == null ? 0 : originalContent.length(), targetPlatform);
        // TODO Task 6: 调 opc-ai-core /opc/llm/chat scene=content_platform_adapter
        return "{\"adapted_content\":\"TODO\",\"tone\":\"TODO\",\"hashtags\":[]}";
    }
}