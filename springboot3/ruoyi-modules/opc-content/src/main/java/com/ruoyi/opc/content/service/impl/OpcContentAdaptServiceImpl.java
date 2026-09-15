package com.ruoyi.opc.content.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.content.domain.OpcContentAdapt;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentAdaptRequest;
import com.ruoyi.opc.content.enums.ContentPlatform;
import com.ruoyi.opc.content.enums.ContentScriptStatus;
import com.ruoyi.opc.content.enums.ContentScriptType;
import com.ruoyi.opc.content.mapper.OpcContentAdaptMapper;
import com.ruoyi.opc.content.mapper.OpcContentScriptMapper;
import com.ruoyi.opc.content.service.IOpcContentAdaptService;
import com.ruoyi.opc.content.service.IOpcContentScriptService;
import com.ruoyi.opc.content.service.llm.ContentLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 平台适配 Service 实现 — 原文脚本 → 调 ContentLlmClient.adapt → 创建新脚本(type=ADAPTER)。
 *
 * <p>W74 Task 4: 实现 + 跨租户隔离 + ContentLlmClient 占位(Task 6 真实接入)。
 *
 * <p>关键设计:
 * <ul>
 *   <li>{@code adapt()} 用 scriptService.detail 校验 sourceScript 存在(同公司)</li>
 *   <li>新脚本 type=ADAPTER,sourceScriptId=原文,status=DRAFT</li>
 *   <li>hashtags(String[]) 在 Service 层 {@code JSON.toJSONString} 序列化,VARCHAR 落库</li>
 *   <li>直接注入 ScriptMapper 而非走 scriptService.create(避免再次调 LLM)</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcContentAdaptServiceImpl implements IOpcContentAdaptService {

    private final OpcContentAdaptMapper adaptMapper;
    /** 直接 mapper 注入,避免调 scriptService.create 二次触发 LLM */
    private final OpcContentScriptMapper scriptMapper;
    /** 跨 Service 调用 — scriptService 不依赖 adaptService,无循环 */
    private final IOpcContentScriptService scriptService;
    /** W74 Task 4 占位,Task 6 真实接入 */
    private final ContentLlmClient contentLlmClient;

    // ============================================================
    // adapt
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long adapt(OpcContentAdaptRequest req) {
        // 1) 入参校验
        if (req.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (req.getSourceScriptId() == null) {
            throw new ServiceException("sourceScriptId 不能为空");
        }
        if (req.getTargetPlatform() == null || req.getTargetPlatform().isBlank()) {
            throw new ServiceException("targetPlatform 不能为空");
        }
        ContentPlatform platform = ContentPlatform.of(req.getTargetPlatform());

        // 2) 校验 sourceScript 存在 + 跨公司
        OpcContentScript source = scriptService.detail(req.getSourceScriptId(), req.getCompanyId());

        // 3) 调 LLM.adapt(Task 4 占位,Task 6 真实)
        String originalContent = source.getContentMd();
        if (originalContent == null || originalContent.isBlank()) {
            originalContent = source.getContentJson();
        }
        if (originalContent == null) {
            originalContent = "";
        }
        String adaptedJson = contentLlmClient.adapt(originalContent, platform.getCode());

        // 4) 简化解析 adaptedJson → adaptedContent / hashtags / tone
        AdaptPayload payload = parsePayload(adaptedJson, originalContent, req.getTone());

        // 5) 创建新 Script(type=ADAPTER, status=DRAFT, sourceScriptId=原文)
        Long operatorId = getCurrentUserId();
        // W75-C: user_id NOT NULL,防止 SecurityUtils 失败时 userId=0L 被 mapper XML 跳过
        if (operatorId == null || operatorId == 0L) {
            operatorId = 1L; // dev fallback: admin user_id=1
        }
        LocalDateTime now = LocalDateTime.now();
        OpcContentScript adaptedScript = OpcContentScript.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(req.getCompanyId())
                .userId(operatorId)
                .type(ContentScriptType.ADAPTER.getCode())
                .title("[适配-" + platform.getCode() + "] "
                        + (source.getTitle() == null ? "" : source.getTitle()))
                .promptInput(source.getPromptInput())
                .contentJson(adaptedJson == null ? "" : adaptedJson)
                .contentMd(payload.adaptedContent)
                .wordCount(payload.adaptedContent == null ? 0 : payload.adaptedContent.length())
                .status(ContentScriptStatus.DRAFT.getCode())
                .sourceScriptId(source.getId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        scriptMapper.insert(adaptedScript);

        // 6) 落 opc_content_adapt
        OpcContentAdapt adapt = OpcContentAdapt.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(req.getCompanyId())
                .sourceScriptId(source.getId())
                .adaptedScriptId(adaptedScript.getId())
                .targetPlatform(platform.getCode())
                .tone(payload.tone)
                .hashtags(JSON.toJSONString(payload.hashtags))
                .note("Task 4 占位生成 (Task 6 接 LLM 后将真实填充)")
                .createdAt(now)
                .build();
        adaptMapper.insert(adapt);

        log.info("适配成功 sourceScriptId={} adaptedScriptId={} platform={} hashtags={}",
                source.getId(), adaptedScript.getId(), platform, Arrays.toString(payload.hashtags));
        return adaptedScript.getId();
    }

    // ============================================================
    // listBySource
    // ============================================================

    @Override
    public List<OpcContentAdapt> listBySource(Long sourceScriptId, Long companyId) {
        if (sourceScriptId == null || companyId == null) {
            throw new ServiceException("sourceScriptId/companyId 不能为空");
        }
        return adaptMapper.selectBySource(sourceScriptId, companyId);
    }

    // ============================================================
    // 内部辅助
    // ============================================================

    /**
     * 简化解析 adaptedJson JSON 串。
     * Task 4 占位: 解析失败时 fallback 用 originalContent + 空 hashtags + 用户传入 tone。
     */
    private AdaptPayload parsePayload(String adaptedJson, String originalContent, String fallbackTone) {
        AdaptPayload result = new AdaptPayload();
        result.adaptedContent = originalContent;
        result.hashtags = new String[0];
        result.tone = fallbackTone;

        if (adaptedJson == null || adaptedJson.isBlank()) {
            return result;
        }
        try {
            var node = JSON.parseObject(adaptedJson);
            if (node == null) return result;
            Object ac = node.get("adapted_content");
            if (ac != null && !ac.toString().isBlank()) {
                result.adaptedContent = ac.toString();
            }
            Object hs = node.get("hashtags");
            if (hs instanceof java.util.List<?> list) {
                result.hashtags = list.stream().map(Object::toString).toArray(String[]::new);
            } else if (hs instanceof String[] arr) {
                result.hashtags = arr;
            }
            Object tn = node.get("tone");
            if (tn != null && !tn.toString().isBlank()) {
                result.tone = tn.toString();
            }
        } catch (Exception e) {
            log.warn("adaptedJson 解析失败,使用 fallback: {}", e.getMessage());
        }
        return result;
    }

    /** 适配结果内部 record(简单 POJO,避免外部 record 暴露) */
    private static class AdaptPayload {
        String adaptedContent;
        String[] hashtags;
        String tone;
    }

    /**
     * 获取当前登录用户 ID;若未登录(单元测试场景)返回 0L。
     */
    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return 0L;
        }
    }
}