package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 平台适配请求 DTO — 原文脚本 → 适配后新脚本 (type=ADAPTER)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpcContentAdaptRequest {

    @JsonProperty("company_id")
    private Long companyId;

    @NotNull
    @JsonProperty("source_script_id")
    private Long sourceScriptId;

    /** 目标平台: DOUYIN */
    @NotBlank
    @JsonProperty("target_platform")
    private String targetPlatform;

    /** 可选语气/风格提示 */
    private String tone;
}