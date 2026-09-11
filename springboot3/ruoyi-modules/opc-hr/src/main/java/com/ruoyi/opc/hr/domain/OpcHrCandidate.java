package com.ruoyi.opc.hr.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrCandidate {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    private String name;
    private String email;
    private String phone;
    @JsonProperty("resume_url")
    private String resumeUrl;
    @JsonProperty("resume_md")
    private String resumeMd;
    @JsonProperty("parsed_json")
    private String parsedJson;
    /** 候选人画像向量 1536 维 float32 序列化 */
    private byte[] embedding;
    private String source;
    @JsonProperty("tags_json")
    private String tagsJson;
    @JsonProperty("created_by")
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("create_time")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}
