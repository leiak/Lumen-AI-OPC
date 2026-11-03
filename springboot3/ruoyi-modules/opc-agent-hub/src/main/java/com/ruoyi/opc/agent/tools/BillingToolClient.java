package com.ruoyi.opc.agent.tools;

import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * opc-agent-hub → opc-billing 的工具侧远程调用（查真钱包余额）。
 *
 * <p>同 {@link FinanceToolClient}：opc-agent-hub 不依赖 opc-billing，只能用
 * {@code R<Map<String,Object>>} 接住返回值。
 *
 * @author OAC
 */
@FeignClient(contextId = "billingToolClient", value = ServiceNameConstants.BILLING_SERVICE)
public interface BillingToolClient {

    /**
     * 公司级钱包余额聚合。
     *
     * @param companyId 公司 id（由 ToolExecutor 注入，调用方改不了）
     */
    @GetMapping("/opc/billing/agg/wallet")
    R<Map<String, Object>> wallet(@RequestParam("companyId") Long companyId);

}
