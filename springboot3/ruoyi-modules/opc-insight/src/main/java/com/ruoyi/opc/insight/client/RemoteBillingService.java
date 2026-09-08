package com.ruoyi.opc.insight.client;

import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;
import com.ruoyi.opc.billing.vo.WalletAggVo;
import com.ruoyi.opc.insight.client.factory.RemoteBillingFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * INSIGHT → opc-billing 远程调用（M4 Task 5）。
 *
 * @author OAC
 */
@FeignClient(contextId = "remoteBillingService", value = ServiceNameConstants.BILLING_SERVICE,
        fallbackFactory = RemoteBillingFallbackFactory.class)
public interface RemoteBillingService {

    @GetMapping("/opc/billing/agg/wallet")
    R<WalletAggVo> wallet(@RequestParam Long companyId);

    @GetMapping("/opc/billing/agg/orders")
    R<OrdersAggVo> orders(@RequestParam Long companyId, @RequestParam String period);

    @GetMapping("/opc/billing/agg/recharge")
    R<RechargeAggVo> recharge(@RequestParam Long companyId, @RequestParam String period);

}
