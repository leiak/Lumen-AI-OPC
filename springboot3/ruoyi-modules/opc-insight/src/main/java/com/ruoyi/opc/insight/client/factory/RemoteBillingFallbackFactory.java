package com.ruoyi.opc.insight.client.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;
import com.ruoyi.opc.billing.vo.WalletAggVo;
import com.ruoyi.opc.insight.client.RemoteBillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * opc-billing 远程调用降级处理（M4 Task 5）。
 *
 * @author OAC
 */
@Component
public class RemoteBillingFallbackFactory implements FallbackFactory<RemoteBillingService> {

    private static final Logger log = LoggerFactory.getLogger(RemoteBillingFallbackFactory.class);

    @Override
    public RemoteBillingService create(Throwable throwable) {
        log.error("opc-billing 调用降级: {}", throwable.getMessage());
        return new RemoteBillingService() {
            @Override
            public R<WalletAggVo> wallet(Long companyId) {
                return R.fail("wallet 降级: " + throwable.getMessage());
            }

            @Override
            public R<OrdersAggVo> orders(Long companyId, String period) {
                return R.fail("orders 降级: " + throwable.getMessage());
            }

            @Override
            public R<RechargeAggVo> recharge(Long companyId, String period) {
                return R.fail("recharge 降级: " + throwable.getMessage());
            }
        };
    }
}
