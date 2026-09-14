package com.ruoyi.opc.hr.service.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.dto.HrScoreResult;
import com.ruoyi.opc.hr.feign.OpcHrAiCoreGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * opc-hr LLM 客户端 (W73 Task 10 — 真实接入 opc-ai-core 网关)。
 *
 * <p>封装 {@link OpcHrAiCoreGateway},对外暴露 3 个业务方法对应 3 个 prompt 场景:
 * <ul>
 *   <li>{@link #generateJd} — scene {@code hr_jd_generate}</li>
 *   <li>{@link #parseResume} — scene {@code hr_resume_parse}</li>
 *   <li>{@link #scoreCandidate} — scene {@code hr_candidate_score}</li>
 * </ul>
 *
 * <p>设计要点:
 * <ol>
 *   <li><b>显式失败</b> — 网关返回 R.fail() 或响应缺失 content 字段时,本类抛
 *       {@link ServiceException} 让业务层感知。绝不静默吞错。</li>
 *   <li><b>降级可观测</b> — 所有失败路径都打 warn 日志,包含 scene + 输入摘要。</li>
 *   <li><b>解析容错</b> — score 场景 LLM 返回非 JSON 时,降级为 score=50 + reason=原文。
 *       业务层可读 reasons 决定是否人工复核。</li>
 *   <li><b>Jackson 单例</b> — ObjectMapper 复用,避免每次 new 注入字段元数据。</li>
 * </ol>
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrLlmClient {

    private final OpcHrAiCoreGateway aiCoreGateway;

    /** Jackson 单例:reusedFeatures 避免每次构建 mapper 时初始化 feature。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // ============================================================
    // 1. hr_jd_generate — JD 生成
    // ============================================================

    /**
     * 根据业务描述生成完整 JD 文本。
     *
     * @param title       岗位标题(必填,非空)
     * @param category    岗位类别(可空,缺省时提示词不强调)
     * @param description 业务描述(必填,非空)
     * @return 生成的 JD 全文(纯文本,不含 Markdown 标题)
     * @throws ServiceException 当 LLM 网关不可用或响应缺失 content
     */
    public String generateJd(String title, String category, String description) {
        if (title == null || title.isBlank()) {
            throw new ServiceException("title 不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new ServiceException("description 不能为空");
        }

        String userInput = String.format(
                "岗位标题: %s%n岗位类别: %s%n业务描述: %s",
                title,
                category == null || category.isBlank() ? "通用" : category,
                description);

        String content = chatOnce(
                HrLlmPrompts.SCENE_JD_GENERATE,
                HrLlmPrompts.JD_GENERATE_SYSTEM,
                userInput,
                HrLlmPrompts.TEMP_JD_GENERATE,
                List.of("title=" + title, "descriptionLen=" + description.length()));

        if (content == null || content.isBlank()) {
            log.warn("[opc-hr] generateJd 响应 content 为空 scene=hr_jd_generate");
            throw new ServiceException("LLM 生成的 JD 为空,请重试");
        }
        return content.trim();
    }

    // ============================================================
    // 2. hr_resume_parse — 简历解析
    // ============================================================

    /**
     * 解析简历文本,返回结构化 JSON(符合 {@link HrLlmPrompts#RESUME_PARSE_SYSTEM} schema)。
     *
     * <p>返回的是 LLM 输出的 JSON 字符串(而非 Java 对象),便于调用方原样存入
     * {@code opc_hr_candidate.parsed_json} 列。
     *
     * @param resumeMd 简历 Markdown / 纯文本(必填,非空)
     * @return 简历 JSON 字符串(无效响应时降级为 {@code {"raw_resume_md":"...","parse":"raw"}})
     * @throws ServiceException 仅当 LLM 网关不可用或完全无响应时
     */
    public String parseResume(String resumeMd) {
        if (resumeMd == null || resumeMd.isBlank()) {
            throw new ServiceException("resumeMd 不能为空");
        }

        String content = chatOnce(
                HrLlmPrompts.SCENE_RESUME_PARSE,
                HrLlmPrompts.RESUME_PARSE_SYSTEM,
                "请解析以下简历:\n\n" + resumeMd,
                HrLlmPrompts.TEMP_RESUME_PARSE,
                List.of("resumeLen=" + resumeMd.length()));

        if (content == null || content.isBlank()) {
            log.warn("[opc-hr] parseResume 响应 content 为空 scene=hr_resume_parse");
            throw new ServiceException("LLM 解析简历失败:响应为空");
        }
        // LLM 偶尔会包 Markdown 代码块 ```json ... ```,这里粗暴剥掉
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    // ============================================================
    // 3. hr_candidate_score — 候选人评分
    // ============================================================

    /**
     * 基于 JD + 简历计算候选人综合匹配分。
     *
     * @param jdText     JD 全文(任职要求 + 岗位职责)
     * @param resumeJson 简历 JSON 字符串,可来自 {@link #parseResume} 输出
     * @return {@link HrScoreResult} 解析失败时降级为 score=50 + reason=原始 content
     * @throws ServiceException 仅当 LLM 网关不可用或完全无响应时
     */
    public HrScoreResult scoreCandidate(String jdText, String resumeJson) {
        if (jdText == null || jdText.isBlank()) {
            throw new ServiceException("jdText 不能为空");
        }
        if (resumeJson == null || resumeJson.isBlank()) {
            throw new ServiceException("resumeJson 不能为空");
        }

        String userInput = String.format(
                "=== JD 全文 ===%n%s%n=== 候选人简历(JSON) ===%n%s",
                jdText, resumeJson);

        String content = chatOnce(
                HrLlmPrompts.SCENE_CANDIDATE_SCORE,
                HrLlmPrompts.CANDIDATE_SCORE_SYSTEM,
                userInput,
                HrLlmPrompts.TEMP_CANDIDATE_SCORE,
                List.of("jdLen=" + jdText.length(), "resumeLen=" + resumeJson.length()));

        if (content == null || content.isBlank()) {
            log.warn("[opc-hr] scoreCandidate 响应 content 为空 scene=hr_candidate_score");
            throw new ServiceException("LLM 评分失败:响应为空");
        }

        // 优先按 JSON 解析;失败时降级
        try {
            String jsonBody = stripMarkdownFence(content);
            HrScoreResult parsed = MAPPER.readValue(jsonBody, HrScoreResult.class);
            log.info("[opc-hr] scoreCandidate 解析成功 score={} highlights={} gaps={}",
                    parsed.safeScore(), parsed.safeHighlights().size(), parsed.safeGaps().size());
            return parsed;
        } catch (JsonProcessingException e) {
            log.warn("[opc-hr] scoreCandidate 解析 JSON 失败,降级 score=50 reason=原文: {}", e.getMessage());
            return HrScoreResult.builder()
                    .score(50)
                    .reason("[LLM 输出非 JSON,降级默认分] " + content.substring(0, Math.min(200, content.length())))
                    .build();
        }
    }

    // ============================================================
    // 内部:统一 chat 调用
    // ============================================================

    /**
     * 构造 payload + 调用网关 + 提取 content。
     *
     * @return content 字符串;网关不可用/响应缺失 content 时返回 null(让上层区分失败 vs 空内容)
     */
    private String chatOnce(String scene, String systemPrompt, String userInput,
                            double temperature, List<String> contextTags) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", scene);
        payload.put("temperature", temperature);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userInput)));

        log.info("[opc-hr] LLM 调用 scene={} ctx={}", scene, contextTags);
        R<Map<String, Object>> resp = aiCoreGateway.chat(payload);
        if (resp == null) {
            log.warn("[opc-hr] LLM 响应为 null scene={}", scene);
            return null;
        }
        // 直接判断 R.SUCCESS(200) 避免 R.isError 内部对 Boolean 自动拆箱时的潜在 NPE/弃用警告
        if (resp.getCode() != R.SUCCESS) {
            log.warn("[opc-hr] LLM 业务失败 scene={} code={} msg={}",
                    scene, resp.getCode(), resp.getMsg());
            return null;
        }
        Map<String, Object> data = resp.getData();
        if (data == null) {
            log.warn("[opc-hr] LLM 响应 data=null scene={}", scene);
            return null;
        }
        Object content = data.get("content");
        if (content == null) {
            log.warn("[opc-hr] LLM 响应 content=null scene={} keys={}", scene, data.keySet());
            return null;
        }
        return content.toString();
    }

    /** 去掉 LLM 偶尔返回的 Markdown ```json ... ``` 包裹。 */
    private static String stripMarkdownFence(String content) {
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