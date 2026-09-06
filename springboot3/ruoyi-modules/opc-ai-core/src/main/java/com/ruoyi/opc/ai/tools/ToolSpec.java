package com.ruoyi.opc.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具描述（OpenAI function calling 规范）
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSpec {

    private String name;
    private String description;
    private Map<String, Object> parameters;

    public static ToolSpec of(String name, String description, Map<String, Object> parameters) {
        return ToolSpec.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();
    }

    /** 转换为 OpenAI tools 格式 */
    public Map<String, Object> toOpenAiFormat() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description == null ? "" : description,
                        "parameters", parameters == null ? Map.of() : parameters
                )
        );
    }

}
