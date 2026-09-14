package com.ruoyi.opc.content.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 平台适配关系 — 一篇原文 → 适配后的新脚本
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcContentAdapt {

    private Long id;

    @JsonProperty("company_id")
    private Long companyId;

    /** 原文脚本 ID */
    @JsonProperty("source_script_id")
    private Long sourceScriptId;

    /** 适配后脚本 ID (一对一, UNIQUE) */
    @JsonProperty("adapted_script_id")
    private Long adaptedScriptId;

    /** 目标平台: DOUYIN */
    @JsonProperty("target_platform")
    private String targetPlatform;

    /** 适配语气/风格描述 */
    private String tone;

    /** 推荐 hashtags */
    private String[] hashtags;

    /** 备注 */
    private String note;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}