package com.ruoyi.opc.ai.redteam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 红队用例领域模型。
 *
 * <p>与 {@link com.ruoyi.opc.ai.eval.EvalCase} 类似但字段不同：
 * <ul>
 *   <li>{@code category} — 攻击类型（CREDENTIAL_INJECTION / WITHDRAWAL_BYPASS / DATA_LEAKAGE）</li>
 *   <li>{@code attack_prompt} — 攻击者实际输入</li>
 *   <li>{@code success_criteria} — 判定攻击成功的可观测条件</li>
 *   <li>{@code expected_defense} — 应当在哪一层被拦下</li>
 * </ul>
 *
 * @author OAC
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedTeamCase {

    @com.fasterxml.jackson.annotation.JsonProperty("case_code")
    private String caseCode;

    @com.fasterxml.jackson.annotation.JsonProperty("category")
    private String category;

    @com.fasterxml.jackson.annotation.JsonProperty("severity")
    private String severity;

    @com.fasterxml.jackson.annotation.JsonProperty("attack_vector")
    private String attackVector;

    @com.fasterxml.jackson.annotation.JsonProperty("attack_prompt")
    private String attackPrompt;

    @com.fasterxml.jackson.annotation.JsonProperty("attack_goal")
    private String attackGoal;

    @com.fasterxml.jackson.annotation.JsonProperty("success_criteria")
    private String successCriteria;

    @com.fasterxml.jackson.annotation.JsonProperty("expected_defense")
    private String expectedDefense;

    @com.fasterxml.jackson.annotation.JsonProperty("difficulty")
    private String difficulty;

    /**
     * 攻击类型枚举常量（用于跨类聚合）
     */
    public static final String CAT_CREDENTIAL    = "CREDENTIAL_INJECTION";
    public static final String CAT_WITHDRAWAL    = "WITHDRAWAL_BYPASS";
    public static final String CAT_DATA_LEAKAGE  = "DATA_LEAKAGE";

    /**
     * 防御层级：PromptGuard 正则 → SensitiveWordFilter 关键词 → SystemPrompt 规则 → LLM 自主拒答
     */
    public static final String LAYER_PROMPT_GUARD     = "PromptGuard";
    public static final String LAYER_WORD_FILTER      = "SensitiveWordFilter";
    public static final String LAYER_SYSTEM_PROMPT    = "SystemPrompt";
    public static final String LAYER_LLM_REFUSED      = "LLMRefused";
    public static final String LAYER_NONE             = "NONE";

    /**
     * 把整条用例装进通用 Map，便于 {@link MockAgentProvider} 调试输出。
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "case_code", caseCode == null ? "" : caseCode,
                "category", category == null ? "" : category,
                "severity", severity == null ? "" : severity,
                "difficulty", difficulty == null ? "" : difficulty,
                "attack_prompt", attackPrompt == null ? "" : attackPrompt
        );
    }
}
