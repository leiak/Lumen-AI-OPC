package com.ruoyi.opc.insight.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * INSIGHT 异常表 domain（M4 Task 6）。
 *
 * <p>对应表 {@code opc_insight_anomaly}（V20260908__opc_insight_schema.sql）。
 *
 * <p><b>字段对照</b>：
 * <pre>
 *   id              BIGINT PK AUTO_INCREMENT
 *   company_id      BIGINT NOT NULL
 *   period          DATE NOT NULL            (e.g. 2026-09-01)
 *   level           VARCHAR(8) NOT NULL      (HIGH / MEDIUM / LOW)
 *   rule_code       VARCHAR(64) NOT NULL     (e.g. VOUCHER_OVER_100K / LLM_SOFT_*)
 *   description     VARCHAR(512)
 *   create_by       VARCHAR(64) DEFAULT ''
 *   status          VARCHAR(16) DEFAULT 'OPEN'   (OPEN / ACK / RESOLVED / IGNORED)
 *   llm_confidence  DECIMAL(3,2)
 *   create_time     DATETIME DEFAULT CURRENT_TIMESTAMP
 *   update_by       VARCHAR(64) DEFAULT ''
 *   update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>采用与 {@code OpcFinanceTaxReport} 一致的纯 MyBatis XML mapper 风格（不依赖
 * MyBatis-Plus 的 BaseMapper），保持 opc-* 模块间的编码一致性。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcInsightAnomaly implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（AUTO_INCREMENT，由 mapper 回填） */
    private Long id;

    /** 所属公司 ID */
    private Long companyId;

    /** 检测日期（YYYY-MM-01，schema DATE 列） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate period;

    /** 等级 HIGH / MEDIUM / LOW（VARCHAR 字段，存枚举名） */
    private String level;

    /** 规则编码（用于去重 / 排查） */
    private String ruleCode;

    /** 异常描述 */
    private String description;

    /** 创建人（默认 '' per schema） */
    private String createBy;

    /** 处置状态 OPEN / ACK / RESOLVED / IGNORED（默认 OPEN per schema） */
    private String status;

    /** LLM 置信度 0.00-1.00（硬规则场景下为 null） */
    private BigDecimal llmConfidence;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新人（默认 '' per schema） */
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
