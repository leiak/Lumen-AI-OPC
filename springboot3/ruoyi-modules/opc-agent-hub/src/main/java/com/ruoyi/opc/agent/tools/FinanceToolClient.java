package com.ruoyi.opc.agent.tools;

import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * opc-agent-hub → opc-finance 的工具侧远程调用（查真流水 / 真凭证）。
 *
 * <p><b>为什么返回 {@code R<Map<String,Object>>} 而不是 {@code R<FlowRangeVo>}</b>：
 * opc-agent-hub 的 pom 里<b>没有</b> opc-finance 的依赖（这正是把工具搬到 agent-hub 的
 * 前提 —— 搬过去以后另外三个进程就扫不到这些类了），所以不能引 finance 的 VO。
 * 用泛型 Map 接住，Jackson 会把嵌套对象解成 {@code LinkedHashMap}。
 *
 * <p>两个端点都是 {@code @InnerAuth}，靠 {@code FeignRequestInterceptor} 无条件注入的
 * {@code from-source: inner} 请求头放行。
 *
 * <p>区间缺省（{@code fromDate} / {@code toDate} 不传）由 finance 端负责：
 * fromDate = 该公司最早一条记录的日期，toDate = 昨天。工具层<b>不补默认值</b>。
 *
 * @author OAC
 */
@FeignClient(contextId = "financeToolClient", value = ServiceNameConstants.FINANCE_SERVICE)
public interface FinanceToolClient {

    /**
     * 银行流水区间查询。
     *
     * @param companyId 公司 id（由 ToolExecutor 注入，调用方改不了）
     * @param fromDate  起始日 yyyy-MM-dd，可选
     * @param toDate    截止日 yyyy-MM-dd，可选
     */
    @GetMapping("/opc/finance/tools/flows")
    R<Map<String, Object>> flows(@RequestParam("companyId") Long companyId,
                                  @RequestParam(value = "fromDate", required = false) String fromDate,
                                  @RequestParam(value = "toDate", required = false) String toDate);

    /**
     * 会计凭证区间查询。
     *
     * <p>口径：{@code totalIn} = 贷方（credit，收入）合计，{@code totalOut} = 借方（debit，支出）合计。
     */
    @GetMapping("/opc/finance/tools/vouchers")
    R<Map<String, Object>> vouchers(@RequestParam("companyId") Long companyId,
                                     @RequestParam(value = "fromDate", required = false) String fromDate,
                                     @RequestParam(value = "toDate", required = false) String toDate);

}
