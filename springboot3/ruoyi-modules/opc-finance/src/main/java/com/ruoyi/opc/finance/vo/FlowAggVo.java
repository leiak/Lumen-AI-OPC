package com.ruoyi.opc.finance.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 银行流水月度聚合结果（供 opc-insight 经 Feign 拉取）
 *
 * <p>字段来源：{@code opc_finance_bank_flow} 按 {@code company_id + trade_time} 的
 * 年月聚合，{@code direction} 为 IN 计入收入、OUT 计入支出。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 所属期 YYYY-MM */
    private String period;

    /** 收入合计（direction='IN'） */
    private BigDecimal inTotal;

    /** 支出合计（direction='OUT'） */
    private BigDecimal outTotal;

    /** 流水条数 */
    private Long flowCount;

    /** 已 AI 抽取条数（extracted=1） */
    private Long extractedCount;

}
