package com.ruoyi.opc.finance.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * Token 消耗聚合 Mapper（只读）
 *
 * <p>读的是 {@code opc_agent_token_usage} —— 该表由 opc-agent-hub 写入
 * （{@code TokenUsageRecorderImpl}），opc-finance 只做只读聚合，
 * 用于对外提供 {@code GET /opc/finance/agg/token-usage}（INSIGHT 的 KPI 底座）。
 * 两个服务共用同一个 MySQL 库（{@code ry-vue-opc}），因此不需要跨服务调用。
 *
 * @author OAC
 */
public interface OpcFinanceTokenUsageMapper {

    /**
     * 聚合某公司在指定期间（YYYY-MM）的 Token 消耗。
     *
     * <p>返回 key 集合：
     * <ul>
     *   <li>input_tokens — 输入 token 合计</li>
     *   <li>output_tokens — 输出 token 合计</li>
     *   <li>total_tokens — 总 token 合计</li>
     *   <li>total_cost — 花费合计（元）</li>
     *   <li>call_count — 调用次数</li>
     * </ul>
     */
    Map<String, Object> aggregateByPeriod(@Param("companyId") Long companyId,
                                          @Param("period") String period);

}
