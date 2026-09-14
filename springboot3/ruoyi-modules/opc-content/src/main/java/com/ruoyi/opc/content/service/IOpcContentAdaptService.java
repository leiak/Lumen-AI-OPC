package com.ruoyi.opc.content.service;

import com.ruoyi.opc.content.domain.OpcContentAdapt;
import com.ruoyi.opc.content.dto.OpcContentAdaptRequest;

import java.util.List;

/**
 * 平台适配 Service 接口 — 原文脚本 → 调 ContentLlmClient.adapt → 创建新脚本(type=ADAPTER) + 落 opc_content_adapt。
 *
 * @author OAC
 */
public interface IOpcContentAdaptService {

    /**
     * 平台适配:
     * 1. 查 sourceScript(scriptService.detail)
     * 2. 调 ContentLlmClient.adapt(contentMd, targetPlatform) → adaptedContent
     * 3. 创建新 Script(type=ADAPTER, sourceScriptId, contentMd=adaptedContent, status=DRAFT)
     * 4. 写 opc_content_adapt(sourceScriptId / adaptedScriptId / targetPlatform / tone / hashtags / note)
     *
     * @return 适配后新脚本 ID
     */
    Long adapt(OpcContentAdaptRequest req);

    /**
     * 某原文的全部适配记录(1→N,同一原文可适配多个平台)
     */
    List<OpcContentAdapt> listBySource(Long sourceScriptId, Long companyId);
}