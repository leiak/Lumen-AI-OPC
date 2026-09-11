package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OpcHrCandidateDto {
    private Long id;
    private String name;
    private String email;
    private String phone;
    @JsonProperty("resume_url")
    private String resumeUrl;
    @JsonProperty("resume_md")
    private String resumeMd;
    @JsonProperty("parsed_json")
    private String parsedJson;
    @JsonProperty("tags_json")
    private String tagsJson;
}
