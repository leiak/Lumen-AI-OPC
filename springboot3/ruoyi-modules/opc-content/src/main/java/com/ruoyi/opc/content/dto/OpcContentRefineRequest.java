package com.ruoyi.opc.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 内容部分精修请求 DTO — 注入 instruction 到 prompt 后重新生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpcContentRefineRequest {

    /** 行号(定位要修改的段落) */
    @NotNull
    @Min(1)
    @JsonProperty("line_no")
    private Integer lineNo;

    /** 精修指令 */
    @NotBlank
    private String instruction;
}