package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.finance.mapper.OpcFinanceTokenUsageMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceTokenUsageService;
import com.ruoyi.opc.finance.vo.TokenUsageVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Token 消耗聚合 Service 实现（M4 Task 1）
 *
 * @author OAC
 */
@Service
@RequiredArgsConstructor
public class OpcFinanceTokenUsageServiceImpl implements IOpcFinanceTokenUsageService {

    private final OpcFinanceTokenUsageMapper mapper;

    @Override
    public TokenUsageVo aggregateByPeriod(Long companyId, String period) {
        AggSupport.validate(companyId, period);
        Map<String, Object> agg = AggSupport.orEmpty(mapper.aggregateByPeriod(companyId, period));

        return TokenUsageVo.builder()
                .companyId(companyId)
                .period(period)
                .inputTokens(AggSupport.count(agg.get("input_tokens")))
                .outputTokens(AggSupport.count(agg.get("output_tokens")))
                .totalTokens(AggSupport.count(agg.get("total_tokens")))
                .totalCost(AggSupport.decimal(agg.get("total_cost")))
                .callCount(AggSupport.count(agg.get("call_count")))
                .build();
    }

}
