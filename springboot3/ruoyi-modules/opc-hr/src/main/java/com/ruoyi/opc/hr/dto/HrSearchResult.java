package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HrSearchResult {
    private OpcHrCandidate candidate;
    /** 相似度 0-1 */
    private Double score;
    @JsonProperty("match_reason")
    private String matchReason;
}
