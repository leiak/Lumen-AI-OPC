package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 内容生成请求 DTO — 创建脚本 + 触发 LLM 生成。
 * 前端可能带额外字段 (e.g. ui debug),ignoreUnknown 容错。
 *
 * <p>W75-C: 接受 snake_case (内部/契约) 与 camelCase (前端/e2e) 两种命名。
 * 使用 @JsonAlias 而非 @JsonProperty,避免 Lombok 把 @JsonProperty 复制到 setter
 * 覆盖字段注解。Jackson 反序列化时 @JsonAlias 会作为 fallback。
 *
 * <p>字段命名保持 camelCase (Java 习惯),snake_case 通过 @JsonAlias 接受。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpcContentGenerateRequest {

    @NotNull
    @JsonAlias({"companyId", "company_id"})
    private Long companyId;

    /** 脚本类型: DRAMA/VIDEO/ARTICLE (ADAPTER 走 /adapt) */
    @NotBlank
    private String type;

    /** 可选标题,空时用"未命名脚本" */
    private String title;

    /** LLM 入参(主题/角色/集数等),必填 */
    @NotBlank
    @JsonAlias({"promptInput", "prompt_input"})
    private String promptInput;
}