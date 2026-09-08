package com.ruoyi.opc.insight.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * INSIGHT 日报表 domain（M4 Task 8）。
 *
 * <p>对应表 {@code opc_insight_daily_report}
 * （V20260908__opc_insight_schema.sql）。</p>
 *
 * <p><b>字段对照</b>：
 * <pre>
 *   id          BIGINT PK AUTO_INCREMENT
 *   company_id  BIGINT NOT NULL
 *   period      DATE NOT NULL            (e.g. 2026-09-08)
 *   summary_md  MEDIUMTEXT
 *   kpi_json    JSON
 *   advice_md   MEDIUMTEXT
 *   llm_used    VARCHAR(64)
 *   create_by   VARCHAR(64) DEFAULT NULL
 *   create_time DATETIME DEFAULT CURRENT_TIMESTAMP
 *   update_by   VARCHAR(64) DEFAULT NULL
 *   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>与 {@code OpcInsightAnomaly} 一致的纯 MyBatis XML mapper 风格。
 * 采用 {@code @Builder} 以便 Service 装配；{@code @Data} 生成的 setter
 * 在单测中被 Mockito 的 {@code thenAnswer} 用以模拟 MyBatis
 * {@code useGeneratedKeys} 回填主键（详见 {@code DailyReportServiceImplTest}）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcInsightDailyReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（AUTO_INCREMENT，由 mapper 回填） */
    private Long id;

    /** 所属公司 ID */
    private Long companyId;

    /** 报告日期（schema DATE 列，存储 yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate period;

    /** 自然语言总结（Markdown） */
    private String summaryMd;

    /** KPI 指标 JSON（KpiSnapshot 序列化字符串） */
    private String kpiJson;

    /** AI 建议（Markdown） */
    private String adviceMd;

    /** 生成使用的 LLM 模型 ID；LLM 失败时存 "FALLBACK" */
    private String llmUsed;

    /** 创建人（系统自动跑时固定为 "system"） */
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新人（系统自动跑时固定为 "system"） */
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
