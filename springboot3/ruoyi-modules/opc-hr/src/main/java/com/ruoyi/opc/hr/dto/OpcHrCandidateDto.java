package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrCandidateDto {
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
    private String source;
    @JsonProperty("tags_json")
    private String tagsJson;
}