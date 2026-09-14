package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * hr_candidate_score prompt 输出 DTO。
 *
 * <p>解析 LLM 响应: 期望 JSON {@code {score: int, reason: str, highlights: [...], gaps: [...]}}
 * 见 {@link com.ruoyi.opc.hr.service.llm.HrLlmPrompts#CANDIDATE_SCORE_SYSTEM}。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HrScoreResult {

    private Integer score;
    private String reason;
    private List<String> highlights;
    private List<String> gaps;

    /**
     * 安全取值:score 为 null 或越界时 clamp 到 [0, 100]。
     */
    public int safeScore() {
        if (score == null) {
            return 0;
        }
        if (score < 0) {
            return 0;
        }
        if (score > 100) {
            return 100;
        }
        return score;
    }

    public String safeReason() {
        return reason == null ? "" : reason;
    }

    public List<String> safeHighlights() {
        return highlights == null ? Collections.emptyList() : highlights;
    }

    public List<String> safeGaps() {
        return gaps == null ? Collections.emptyList() : gaps;
    }
}