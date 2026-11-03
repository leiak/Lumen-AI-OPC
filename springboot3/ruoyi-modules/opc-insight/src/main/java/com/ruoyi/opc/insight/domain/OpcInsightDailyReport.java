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
 *   report_status        VARCHAR(16)  FULL/PARTIAL/FALLBACK
 *   report_source        VARCHAR(32)  AGENT_WORKFLOW/MANUAL
 *   source_workflow_run_id BIGINT     产出该日报的工作流运行记录 id
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

    /**
     * 写到哪一步：{@code FULL} / {@code PARTIAL} / {@code FALLBACK}。
     *
     * <p>FULL = 数据完整且 LLM 成功；PARTIAL = 数据降级（partial）或部分产出；
     * FALLBACK = LLM 挂了，只有数字摘要。</p>
     */
    private String reportStatus;

    /**
     * 谁写的这一行：{@code AGENT_WORKFLOW}（数字员工/工作流）/
     * {@code MANUAL}（公开接口手工生成）/ {@code SEED}（种子）。
     *
     * <p>这张表有三个写入方，都往同一个 {@code (company_id, period)} 上写 ——
     * 没有这一列就分不清是谁写的。</p>
     */
    private String reportSource;

    /** 产出该日报的工作流运行记录 id（手工生成时为 null）—— 用来从日报反查回那次运行 */
    private Long sourceWorkflowRunId;

    /** 创建人（系统自动跑时固定为 "system"） */
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新人（系统自动跑时固定为 "system"） */
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
