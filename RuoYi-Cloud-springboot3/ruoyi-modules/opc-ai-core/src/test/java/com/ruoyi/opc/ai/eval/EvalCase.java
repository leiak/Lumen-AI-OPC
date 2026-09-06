package com.ruoyi.opc.ai.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 评测用例数据模型
 *
 * <p>字段对齐 JSON：case_code, agent_code, scene, input, expected, difficulty, tags
 *
 * @author OAC
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalCase implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("case_code")
    private String caseCode;

    @JsonProperty("agent_code")
    private String agentCode;

    @JsonProperty("scene")
    private String scene;

    @JsonProperty("input")
    private String input;

    @JsonProperty("expected")
    private Object expected;

    @JsonProperty("difficulty")
    private String difficulty;

    @JsonProperty("tags")
    private String tags;

}
