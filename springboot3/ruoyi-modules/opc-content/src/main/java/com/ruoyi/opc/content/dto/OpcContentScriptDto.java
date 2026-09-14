package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 内容脚本返回 DTO。
 * 字段镜像 {@link com.ruoyi.opc.content.domain.OpcContentScript}，
 * snake_case + @JsonFormat GMT+8 序列化给前端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcContentScriptDto {

    private Long id;

    @JsonProperty("company_id")
    private Long companyId;

    @JsonProperty("user_id")
    private Long userId;

    /** 脚本类型: DRAMA/VIDEO/ARTICLE/ADAPTER */
    private String type;

    private String title;

    @JsonProperty("prompt_input")
    private String promptInput;

    @JsonProperty("content_json")
    private String contentJson;

    @JsonProperty("content_md")
    private String contentMd;

    @JsonProperty("word_count")
    private Integer wordCount;

    /** 状态: DRAFT/READY/PUBLISHED/FAILED/DELETED */
    private String status;

    @JsonProperty("source_script_id")
    private Long sourceScriptId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}