package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 内容生成请求 DTO — 创建脚本 + 触发 LLM 生成。
 * 前端可能带额外字段 (e.g. ui debug),ignoreUnknown 容错。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpcContentGenerateRequest {

    @JsonProperty("company_id")
    private Long companyId;

    /** 脚本类型: DRAMA/VIDEO/ARTICLE (ADAPTER 走 /adapt) */
    @NotBlank
    private String type;

    /** 可选标题,空时用"未命名脚本" */
    private String title;

    /** LLM 入参(主题/角色/集数等),必填 */
    @NotBlank
    @JsonProperty("prompt_input")
    private String promptInput;
}