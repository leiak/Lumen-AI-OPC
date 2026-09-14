package com.ruoyi.opc.content.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 内容脚本主表 — 短剧 / 视频 / 图文 / 适配 (LLM 生成内容)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcContentScript {

    private Long id;

    @JsonProperty("company_id")
    private Long companyId;

    @JsonProperty("user_id")
    private Long userId;

    /** 脚本类型: {@link com.ruoyi.opc.content.enums.ContentScriptType} */
    private String type;

    /** 脚本标题 */
    private String title;

    /** 用户原始 prompt 输入 */
    @JsonProperty("prompt_input")
    private String promptInput;

    /** LLM 输出 JSON (DRAMA/VIDEO/ADAPTER 用) */
    @JsonProperty("content_json")
    private String contentJson;

    /** Markdown 内容 (ARTICLE 用) */
    @JsonProperty("content_md")
    private String contentMd;

    /** 字数统计 */
    @JsonProperty("word_count")
    private Integer wordCount;

    /** 状态: {@link com.ruoyi.opc.content.enums.ContentScriptStatus} */
    private String status;

    /** ADAPTER 关联的原文脚本 ID */
    @JsonProperty("source_script_id")
    private Long sourceScriptId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}