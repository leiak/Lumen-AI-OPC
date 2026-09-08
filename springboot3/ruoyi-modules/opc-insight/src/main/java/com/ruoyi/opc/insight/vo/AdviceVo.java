package com.ruoyi.opc.insight.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * INSIGHT 决策建议 VO（M4 Task 9）。
 *
 * <p>对外（controller / 前端）展示用。与 domain {@code OpcInsightAdvice}
 * 字段一一对应，无类型转换需求。</p>
 *
 * <p>{@code confidence} 为 BigDecimal 类型，序列化时 Jackson 输出字符串以保留
 * 精度（DECIMAL(3,2) 范围 0.00-1.00，前端可直接 toFixed(2) 渲染）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdviceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long companyId;
    private String topic;
    private String adviceMd;
    private String llmUsed;
    private BigDecimal confidence;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}