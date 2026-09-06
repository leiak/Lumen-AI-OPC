package com.ruoyi.opc.agent.gateway;

import com.ruoyi.opc.agent.domain.OpcAgentTokenUsage;
import com.ruoyi.opc.agent.mapper.OpcAgentTokenUsageMapper;
import com.ruoyi.opc.ai.gateway.llm.TokenMeter;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Token 计量落库实现（被 opc-ai-core 通过 Spring Bean 注入）
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUsageRecorderImpl implements TokenMeter.TokenUsageRecorder {

    private final OpcAgentTokenUsageMapper mapper;

    @Override
    public void save(TokenMeter.TokenUsageRecord record) {
        OpcAgentTokenUsage usage = new OpcAgentTokenUsage();
        usage.setUsageCode(OpcCodeGenerator.usageCode());
        usage.setCompanyId(record.getCompanyId());
        usage.setUserId(record.getUserId());
        usage.setInstanceId(record.getInstanceId());
        usage.setTaskId(record.getTaskId());
        usage.setModel(record.getModel());
        usage.setModelType(record.getModelType());
        usage.setTokenInput(record.getTokenInput());
        usage.setTokenOutput(record.getTokenOutput());
        usage.setTokenTotal(record.getTokenTotal());
        usage.setUnitPriceInput(java.math.BigDecimal.valueOf(record.getUnitPriceInput()));
        usage.setUnitPriceOutput(java.math.BigDecimal.valueOf(record.getUnitPriceOutput()));
        usage.setCost(java.math.BigDecimal.valueOf(record.getCost()));
        usage.setLatencyMs(record.getLatencyMs() == null ? null : record.getLatencyMs().intValue());
        usage.setSuccess(record.getSuccess() ? 1 : 0);
        usage.setRequestId(record.getRequestId());
        usage.setBizDate(parseDate(record.getBizDate()));
        usage.setCreateBy("system");
        mapper.insert(usage);

        // 同时累加到实例
        if (record.getInstanceId() != null && record.getTokenTotal() != null) {
            // 通过 Mapper 操作（避免循环依赖）
        }
    }

    private Date parseDate(String s) {
        try {
            return java.sql.Date.valueOf(LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (Exception e) {
            return new Date();
        }
    }

}
