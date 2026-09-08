package com.ruoyi.opc.insight.vo;

import com.ruoyi.opc.insight.enums.AnomalyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * INSIGHT 异常 VO（M4 Task 6）。
 *
 * <p>对外（controller / 前端）展示用；不直接对应数据库行。
 * 与 {@code OpcInsightAnomaly} domain 的区别：
 * <ul>
 *   <li>{@code level} 字段是 {@link AnomalyLevel} 枚举（domain 是 VARCHAR 字符串）</li>
 *   <li>{@code period} 字段是 String "YYYY-MM" 格式（domain 是 LocalDate）</li>
 *   <li>{@code id} 在 scan 后由 mapper 回填</li>
 * </ul>
 *
 * <p>{@code status} 取值：OPEN（默认）/ ACK（已确认）/ RESOLVED（已处置）。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（scan 后由 mapper 回填） */
    private Long id;

    /** 公司 ID */
    private Long companyId;

    /** 所属期 YYYY-MM */
    private String period;

    /** 异常等级 */
    private AnomalyLevel level;

    /** 规则编码（{@link com.ruoyi.opc.insight.enums.AnomalyRule} 的 code 或 LLM 生成的 code） */
    private String ruleCode;

    /** 异常描述 */
    private String description;

    /** 处置状态：OPEN / ACK / RESOLVED */
    private String status;

    /** LLM 置信度（硬规则场景下为 null） */
    private BigDecimal llmConfidence;

    /** 创建时间 */
    private LocalDateTime createTime;
}
