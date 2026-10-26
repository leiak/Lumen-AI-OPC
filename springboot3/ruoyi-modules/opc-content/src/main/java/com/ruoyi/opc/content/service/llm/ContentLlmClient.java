package com.ruoyi.opc.content.service.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
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
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    // ============================================================
    // 1. content_short_drama — 短剧剧本生成
    // ============================================================

    /**
     * 根据主题/角色/集数生成短剧剧本 JSON。
     *
     * @param companyId 归属公司 (租户) —— 计量归属,见 {@link #chatOnce}
     * @param promptInput 主题/角色/集数 等业务描述 (必填, 非空)
     * @return 严格 JSON 字符串 (符合 {@link ContentLlmPrompts#SHORT_DRAMA_SYSTEM} schema)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String generateDrama(Long companyId, String promptInput) {
        if (promptInput == null || promptInput.isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }

        String content = chatOnce(
                companyId,
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
     * @param companyId 归属公司 (租户) —— 计量归属,见 {@link #chatOnce}
     * @param promptInput 主题/卖点/目标受众 等业务描述 (必填, 非空)
     * @return 严格 JSON 字符串 (符合 {@link ContentLlmPrompts#VIDEO_SCRIPT_SYSTEM} schema)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String generateVideo(Long companyId, String promptInput) {
        if (promptInput == null || promptInput.isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }

        String content = chatOnce(
                companyId,
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
     * @param companyId 归属公司 (租户) —— 计量归属,见 {@link #chatOnce}
     * @param promptInput 主题/大纲/受众 等业务描述 (必填, 非空)
     * @return Markdown 字符串 (符合 {@link ContentLlmPrompts#ARTICLE_SYSTEM} 格式)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String generateArticle(Long companyId, String promptInput) {
        if (promptInput == null || promptInput.isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }

        String content = chatOnce(
                companyId,
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
     * @param companyId 归属公司 (租户) —— 计量归属,见 {@link #chatOnce}
     * @param originalContent 原文 Markdown 或纯文本 (必填, 非空)
     * @param targetPlatform  目标平台 DOUYIN/WECHAT 等 (必填, 非空)
     * @return JSON 字符串 (符合 {@link ContentLlmPrompts#PLATFORM_ADAPTER_SYSTEM} schema)
     * @throws ServiceException 当 LLM 网关不可用 / 业务失败 / 响应缺失 content 时
     */
    public String adapt(Long companyId, String originalContent, String targetPlatform) {
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
                companyId,
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

    /** W75-C: 重试次数。EOF/超时类瞬时错误重试可显著提升成功率。 */
    private static final int LLM_MAX_RETRY = 3;
    /** 两次重试之间的 base 间隔 (ms),指数退避 500ms / 1000ms / 2000ms。 */
    private static final long LLM_RETRY_BASE_MS = 500L;

    /**
     * 构造 payload + 调用网关 + 提取 content,带 retry。
     *
     * <p>W75-C: 处理 LLM HTTP 客户端不稳定 (EOF reached while reading / ConnectException /
     * SocketTimeoutException),最多重试 {@link #LLM_MAX_RETRY} 次,指数退避。
     *
     * <p><b>归属必须显式带过去</b>：计量发生在 opc-ai-core 那边, 而调用链是
     * opc-content → Feign → opc-ai-core。登录态靠 Authorization 头透传, 通常能还原出
     * 公司/用户; 但那条链子依赖「token 还在 Redis 里」—— 一旦中间某跳丢了登录态
     * (定时任务、token 刚过期), 对端就不知道这次调用算谁的, 于是用量记录被整条丢掉,
     * 而账单读的就是那张表。所以这里把 <b>业务对象所属的公司</b>(不是「当前登录用户
     * 属于哪家公司」) 一起传过去: 谁的数据被处理, 费用就算谁的。</p>
     *
     * @return content 字符串 (不含 Markdown fence); 网关不可用 / 业务失败 / 响应缺失 content 时抛 {@link ServiceException}
     */
    private String chatOnce(Long companyId, String scene, String systemPrompt, String userInput,
                            double temperature, int maxTokens, List<String> contextTags) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", scene);
        payload.put("temperature", temperature);
        payload.put("maxTokens", maxTokens);
        payload.put("companyId", companyId);
        Long userId = currentUserId();
        if (userId != null) {
            payload.put("userId", userId);
        }
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userInput)));

        log.info("[opc-content] LLM 调用 scene={} ctx={}", scene, contextTags);

        ServiceException lastEx = null;
        for (int attempt = 1; attempt <= LLM_MAX_RETRY; attempt++) {
            try {
                return doChatOnce(scene, payload);
            } catch (ServiceException e) {
                lastEx = e;
                // 仅瞬时错误重试: 业务失败 (R.code != 200) / 响应缺失 不重试,直接抛
                if (!isTransientError(e)) {
                    throw e;
                }
                if (attempt < LLM_MAX_RETRY) {
                    long backoff = LLM_RETRY_BASE_MS * (1L << (attempt - 1));
                    log.warn("[opc-content] LLM 瞬时失败 scene={} attempt={}/{} backoff={}ms err={}",
                            scene, attempt, LLM_MAX_RETRY, backoff, e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ServiceException("LLM 调用被中断");
                    }
                }
            }
        }
        log.error("[opc-content] LLM 重试 {} 次后仍失败 scene={}", LLM_MAX_RETRY, scene);
        throw lastEx;
    }

    /**
     * 单次 LLM 调用:网关 → R.SUCCESS → data.content。
     * 网关层异常 (EOF/ConnectException/SocketTimeout) 经 Feign fallback 转 ServiceException("LLM 服务暂时不可用")。
     */
    private String doChatOnce(String scene, Map<String, Object> payload) {
        R<Map<String, Object>> resp = aiCoreGateway.chat(payload);
        if (resp == null) {
            log.warn("[opc-content] LLM 响应为 null scene={}", scene);
            throw new ServiceException("LLM 服务暂时不可用");
        }
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

    /**
     * W75-C: 判断是否为可重试的瞬时错误。
     * 网关层 EOF / 连接拒绝 / 超时 / 服务不可用 都是瞬时;业务失败 (R.code != 200) 不是。
     */
    private boolean isTransientError(ServiceException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("LLM 服务暂时不可用")
                || msg.contains("EOF reached while reading")
                || msg.contains("Connection refused")
                || msg.contains("ConnectException")
                || msg.contains("SocketTimeout")
                || msg.contains("Read timed out");
    }

    /**
     * 当前登录用户 ID; 没有登录上下文 (定时任务/后台线程) 时返回 null。
     *
     * <p>null 的含义是「这次调用没有具体的人」—— 对端会记成系统(0),
     * 而不是把整条记录丢掉。</p>
     */
    private Long currentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
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
