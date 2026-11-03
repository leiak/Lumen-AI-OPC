package com.ruoyi.opc.insight.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作流写入日报的请求体（{@code POST /opc/insight/internal/daily/upsert}）。
 *
 * <p><b>这里没有 {@code source} 字段</b> —— 这是刻意的（偏离 Task 4 brief，裁定 R-85）。
 * brief 的 Interfaces 写的是「带 {@code String source;}」，但「谁写的这行」正是
 * 这张表要解决的问题本身：如果让调用方在 body 里声明「我是谁写的」，
 * 等于把这件事交回给调用方 —— 一个可以撒谎的调用方。</p>
 *
 * <p>因此 {@code reportSource} 由<b>端点身份</b>决定：走到这个 {@code @InnerAuth} 端点
 * 就说明是工作流写的，服务层写死 {@code "AGENT_WORKFLOW"}。这样也避免了
 * 「{@code report_source}（表列）/ {@code source}（DTO 字段）/ {@code reportSource}
 * （domain 字段）」三套名字并存。</p>
 *
 * <p>其余字段与 domain {@code OpcInsightDailyReport} 一一对应
 * （{@code reportStatus} / {@code sourceWorkflowRunId} 见 R-92）。</p>
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyReportUpsertRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属公司 ID（内部调用显式指定；{@code @InnerAuth} 端点的 body companyId 不被租户守卫改写） */
    private Long companyId;

    /** 报告日期 {@code yyyy-MM-dd}，不能晚于业务「今天」（Asia/Shanghai） */
    private String period;

    /** 自然语言总结（Markdown） */
    private String summaryMd;

    /** AI 建议（Markdown） */
    private String adviceMd;

    /** KPI 指标 JSON —— 必须是合法 JSON，且<b>原样落库</b>（不许重新序列化） */
    private String kpiJson;

    /** 生成使用的 LLM 模型 ID；LLM 失败时存 "FALLBACK" */
    private String llmUsed;

    /** 写到哪一步：{@code FULL} / {@code PARTIAL} / {@code FALLBACK}（必填，见 R-86） */
    private String reportStatus;

    /** 产出该日报的工作流运行记录 id —— 用来从日报反查回那次运行 */
    private Long sourceWorkflowRunId;
}
