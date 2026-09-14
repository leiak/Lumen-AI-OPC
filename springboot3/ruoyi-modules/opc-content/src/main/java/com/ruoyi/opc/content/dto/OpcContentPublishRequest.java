package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

/**
 * 发布请求 DTO — 把脚本发布到指定平台账号。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpcContentPublishRequest {

    @NotNull
    @JsonProperty("company_id")
    private Long companyId;

    @NotNull
    @JsonProperty("script_id")
    private Long scriptId;

    @NotNull
    @JsonProperty("platform_account_id")
    private Long platformAccountId;

    /** 发布标题(可覆盖脚本 title) */
    private String title;

    /** 标签数组(存 DB 为 JSON 字符串,VARCHAR(1024)) */
    private String[] tags;
}