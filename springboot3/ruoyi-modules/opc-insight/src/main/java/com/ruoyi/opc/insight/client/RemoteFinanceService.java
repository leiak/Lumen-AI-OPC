package com.ruoyi.opc.insight.client;

import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.TokenUsageVo;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
import com.ruoyi.opc.insight.client.factory.RemoteFinanceFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * INSIGHT → opc-finance 远程调用（M4 Task 5）。
 *
 * <p>4 个端点均已在 opc-finance 端加 {@code @InnerAuth}，由 AuthFilter 配合
 * Feign 自动注入 {@code from-source: inner} 请求头穿透。
 *
 * <p>全部返回 {@code R<T>} 强类型 —— 跨模块反序列化必须用 {@code R<T>}，不能用
 * {@code R<?>} 或 {@code AjaxResult}（W11 C1 评审修复）。
 *
 * @author OAC
 */
@FeignClient(contextId = "remoteFinanceService", value = ServiceNameConstants.FINANCE_SERVICE,
        fallbackFactory = RemoteFinanceFallbackFactory.class)
public interface RemoteFinanceService {

    @GetMapping("/opc/finance/agg/voucher")
    R<VoucherAggVo> voucherAgg(@RequestParam Long companyId, @RequestParam String period);

    @GetMapping("/opc/finance/agg/flow")
    R<FlowAggVo> flowAgg(@RequestParam Long companyId, @RequestParam String period);

    /**
     * 返回的是 opc-finance 的 domain 类（不是 VO 包装）—— opc-insight 已经直接依赖
     * opc-finance（M4 决策：compile scope），所以可以引用 {@link OpcFinanceTaxReport}。
     * 若日后要彻底解耦，引入 TaxReportVo 包装即可。
     */
    @GetMapping("/opc/finance/agg/tax-report")
    R<OpcFinanceTaxReport> taxReport(@RequestParam Long companyId, @RequestParam String period);

    @GetMapping("/opc/finance/agg/token-usage")
    R<TokenUsageVo> tokenUsage(@RequestParam Long companyId, @RequestParam String period);

}
