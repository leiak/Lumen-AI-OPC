package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 平台适配请求 DTO — 原文脚本 → 适配后新脚本 (type=ADAPTER)。
 *
 * <p>W75-C: 接受 snake_case (内部/契约) 与 camelCase (前端/e2e) 两种命名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpcContentAdaptRequest {

    @NotNull
    @JsonAlias({"companyId", "company_id"})
    private Long companyId;

    @NotNull
    @JsonAlias({"sourceScriptId", "source_script_id"})
    private Long sourceScriptId;

    /** 目标平台: DOUYIN */
    @NotBlank
    @JsonAlias({"targetPlatform", "target_platform"})
    private String targetPlatform;

    /** 可选语气/风格提示 */
    private String tone;
}