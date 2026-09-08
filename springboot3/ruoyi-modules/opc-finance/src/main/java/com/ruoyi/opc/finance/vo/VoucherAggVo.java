package com.ruoyi.opc.finance.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 凭证月度聚合结果（供 opc-insight 经 Feign 拉取）
 *
 * <p>字段来源：{@code opc_finance_voucher} 按 {@code company_id + period} 聚合。
 * 金额一律 {@link BigDecimal} 且 scale=2，避免不同 JDBC driver 返回的 SUM() scale
 * 不一致（见 W1.4.2 经验）。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 所属期 YYYY-MM */
    private String period;

    /** 借方合计（进项/支出侧） */
    private BigDecimal debitTotal;

    /** 贷方合计（销项/收入侧） */
    private BigDecimal creditTotal;

    /** 凭证总张数 */
    private Long voucherCount;

    /** 待审凭证张数（status IN ('DRAFT','REVIEW')） */
    private Long pendingCount;

    /** 已入账张数（status='POSTED'） */
    private Long postedCount;

    /** 被拒张数（status='REJECTED'） */
    private Long rejectedCount;

}
