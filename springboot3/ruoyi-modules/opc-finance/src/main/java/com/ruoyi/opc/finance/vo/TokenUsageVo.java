package com.ruoyi.opc.finance.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Token 消耗月度聚合结果（供 opc-insight 经 Feign 拉取）
 *
 * <p>字段来源：{@code opc_agent_token_usage} 按 {@code company_id + biz_date} 的年月聚合。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 所属期 YYYY-MM */
    private String period;

    /** 输入 token 合计 */
    private Long inputTokens;

    /** 输出 token 合计 */
    private Long outputTokens;

    /** 总 token 合计 */
    private Long totalTokens;

    /** 花费合计（元，scale=4 同 opc_agent_token_usage.cost） */
    private BigDecimal totalCost;

    /** LLM 调用次数 */
    private Long callCount;

}
