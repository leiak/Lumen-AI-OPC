package com.ruoyi.opc.insight.client.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.TokenUsageVo;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
import com.ruoyi.opc.insight.client.RemoteFinanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * opc-finance 远程调用降级处理（M4 Task 5）。
 *
 * <p>降级策略：返回 {@link R#fail(String)}（code=FAIL=500，msg=异常 message）。
 * KpiServiceImpl 看到 {@code r.getCode() != 200} 即走 partial=true 分支，无需 try/catch NPE。
 *
 * @author OAC
 */
@Component
public class RemoteFinanceFallbackFactory implements FallbackFactory<RemoteFinanceService> {

    private static final Logger log = LoggerFactory.getLogger(RemoteFinanceFallbackFactory.class);

    @Override
    public RemoteFinanceService create(Throwable throwable) {
        log.error("opc-finance 调用降级: {}", throwable.getMessage());
        return new RemoteFinanceService() {
            @Override
            public R<VoucherAggVo> voucherAgg(Long companyId, String period) {
                return R.fail("voucherAgg 降级: " + throwable.getMessage());
            }

            @Override
            public R<FlowAggVo> flowAgg(Long companyId, String period) {
                return R.fail("flowAgg 降级: " + throwable.getMessage());
            }

            @Override
            public R<OpcFinanceTaxReport> taxReport(Long companyId, String period) {
                return R.fail("taxReport 降级: " + throwable.getMessage());
            }

            @Override
            public R<TokenUsageVo> tokenUsage(Long companyId, String period) {
                return R.fail("tokenUsage 降级: " + throwable.getMessage());
            }
        };
    }
}
