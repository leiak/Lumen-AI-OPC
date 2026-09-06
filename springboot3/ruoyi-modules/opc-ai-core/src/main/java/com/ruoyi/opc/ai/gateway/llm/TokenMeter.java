package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.opc.common.constant.OpcConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Token 计量器（记录每次 LLM 调用的 Token 消耗与成本）
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenMeter {

    private final TokenUsageRecorder recorder;

    public void record(LlmGateway.ChatContext ctx, String model, ChatResponse resp) {
        if (resp == null) return;
        double inputPrice = resolveInputPrice(model);
        double outputPrice = resolveOutputPrice(model);

        int inputTokens = resp.getTokenInput() == null ? 0 : resp.getTokenInput();
        int outputTokens = resp.getTokenOutput() == null ? 0 : resp.getTokenOutput();
        int totalTokens = inputTokens + outputTokens;

        double cost = (inputTokens / 1000.0) * inputPrice + (outputTokens / 1000.0) * outputPrice;
        BigDecimal costBd = new BigDecimal(cost).setScale(4, RoundingMode.HALF_UP);

        String bizDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        TokenUsageRecord record = new TokenUsageRecord();
        record.companyId = ctx != null ? ctx.getCompanyId() : null;
        record.userId = ctx != null ? ctx.getUserId() : null;
        record.instanceId = ctx != null ? ctx.getInstanceId() : null;
        record.taskId = ctx != null ? ctx.getTaskId() : null;
        record.model = model;
        record.modelType = "CHAT";
        record.tokenInput = inputTokens;
        record.tokenOutput = outputTokens;
        record.tokenTotal = totalTokens;
        record.unitPriceInput = inputPrice;
        record.unitPriceOutput = outputPrice;
        record.cost = costBd.doubleValue();
        record.latencyMs = resp.getLatencyMs();
        record.success = resp.getSuccess() != null && resp.getSuccess();
        record.requestId = resp.getRequestId();
        record.bizDate = bizDate;
        record.scene = ctx != null ? ctx.getScene() : null;

        try {
            recorder.save(record);
        } catch (Exception e) {
            log.error("Token 计量写入失败", e);
        }
    }

    private double resolveInputPrice(String model) {
        if (model == null) return OpcConstants.PRICE_DEEPSEEK_INPUT;
        if (model.startsWith("deepseek")) return OpcConstants.PRICE_DEEPSEEK_INPUT;
        if (model.startsWith("gpt-4o-mini")) return OpcConstants.PRICE_GPT_4O_MINI_INPUT;
        if (model.startsWith("gpt-4o")) return OpcConstants.PRICE_GPT_4O_INPUT;
        return OpcConstants.PRICE_DEEPSEEK_INPUT;
    }

    private double resolveOutputPrice(String model) {
        if (model == null) return OpcConstants.PRICE_DEEPSEEK_OUTPUT;
        if (model.startsWith("deepseek")) return OpcConstants.PRICE_DEEPSEEK_OUTPUT;
        if (model.startsWith("gpt-4o-mini")) return OpcConstants.PRICE_GPT_4O_MINI_OUTPUT;
        if (model.startsWith("gpt-4o")) return OpcConstants.PRICE_GPT_4O_OUTPUT;
        return OpcConstants.PRICE_DEEPSEEK_OUTPUT;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class TokenUsageRecord {
        private Long companyId;
        private Long userId;
        private Long instanceId;
        private Long taskId;
        private String model;
        private String modelType;
        private Integer tokenInput;
        private Integer tokenOutput;
        private Integer tokenTotal;
        private Double unitPriceInput;
        private Double unitPriceOutput;
        private Double cost;
        private Long latencyMs;
        private Boolean success;
        private String requestId;
        private String bizDate;
        private String scene;
    }

    /** 记录器（具体落库实现由 Agent Hub 模块提供，此处定义接口） */
    public interface TokenUsageRecorder {
        void save(TokenUsageRecord record);
    }

}
