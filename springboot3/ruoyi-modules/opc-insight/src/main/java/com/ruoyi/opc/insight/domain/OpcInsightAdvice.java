package com.ruoyi.opc.insight.domain;

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
 * INSIGHT 决策建议表 domain（M4 Task 9）。
 *
 * <p>对应表 {@code opc_insight_advice}
 * （V20260908__opc_insight_schema.sql）。
 *
 * <p><b>字段对照</b>：
 * <pre>
 *   id          BIGINT PK AUTO_INCREMENT
 *   company_id  BIGINT NOT NULL
 *   topic       VARCHAR(64) NOT NULL        (cost_optimization / revenue_growth / ...)
 *   advice_md   MEDIUMTEXT
 *   llm_used    VARCHAR(64)
 *   confidence  DECIMAL(3,2)                 (0.00-1.00)
 *   create_time DATETIME DEFAULT CURRENT_TIMESTAMP
 *   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>表设计为 append-only（代码层面不主动 UPDATE），但保留 {@code update_time}
 * 是为了 schema 演进（新增编辑/重生成场景）时无需 ALTER TABLE 加列。本 Task 9 的
 * {@code regenerate()} 通过 {@code updateById} mapper 方法更新 advice_md/llm_used/
 * confidence 三列（{@code update_time} 由 MySQL ON UPDATE CURRENT_TIMESTAMP 自动维护）。</p>
 *
 * <p>与 {@code OpcInsightDailyReport} / {@code OpcInsightAnomaly} 一致的纯 MyBatis
 * XML mapper 风格，不依赖 MyBatis-Plus。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcInsightAdvice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（AUTO_INCREMENT，由 mapper 回填） */
    private Long id;

    /** 所属公司 ID */
    private Long companyId;

    /** 建议主题（{@code cost_optimization / revenue_growth / cashflow_health / tax_planning / risk_warning}） */
    private String topic;

    /** AI 建议正文（Markdown） */
    private String adviceMd;

    /** 生成使用的 LLM 模型 ID；LLM 失败时存 "FALLBACK" */
    private String llmUsed;

    /** LLM 置信度 0.00-1.00 */
    private BigDecimal confidence;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}