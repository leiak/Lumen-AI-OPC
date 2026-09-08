package com.ruoyi.opc.finance.service;

import com.ruoyi.opc.finance.vo.TokenUsageVo;

/**
 * Token 消耗聚合 Service（只读）
 *
 * <p>为 INSIGHT 的 KPI 快照提供「本月 token 用量」。数据来自
 * {@code opc_agent_token_usage}（opc-agent-hub 写入，本模块只读聚合）。
 *
 * @author OAC
 */
public interface IOpcFinanceTokenUsageService {

    /**
     * 聚合某公司在指定期间（YYYY-MM）的 Token 消耗。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 当 companyId 为空 / period 格式非法
     */
    TokenUsageVo aggregateByPeriod(Long companyId, String period);

}
