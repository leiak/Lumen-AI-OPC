package com.ruoyi.opc.insight.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * INSIGHT 日报 VO（M4 Task 8）。
 *
 * <p>与 domain {@code OpcInsightDailyReport} 字段一致，但 {@code period} 为
 * {@code String}（yyyy-MM-dd，由 mapper SQL 的 {@code DATE_FORMAT} 格式化），
 * 便于前端直接展示而无需再转换。</p>
 *
 * <p>写侧（{@code generate}）使用 domain，MyBatis 写库后回填 id；
 * 读侧（{@code getById} / {@code listByDateRange}）使用本 VO，通过
 * {@code @Builder} 字段一一映射。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long companyId;
    private String period;        // yyyy-MM-dd
    private String summaryMd;
    private String kpiJson;
    private String adviceMd;
    private String llmUsed;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
