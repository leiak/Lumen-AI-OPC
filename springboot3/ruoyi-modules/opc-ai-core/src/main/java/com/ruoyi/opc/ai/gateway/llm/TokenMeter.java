package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.constant.OpcConstants;
import com.ruoyi.system.api.model.LoginUser;
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
        record(ctx, model, resp, false);
    }

    /**
     * 记录一次 LLM 调用的 token 消耗与成本。
     *
     * @param fromCache 这次调用是<b>缓存命中</b>（没有真正调 provider）。
     *                   token 数照记 —— 客户确实消耗了同样的 token 量；
     *                   费用记 <b>0</b> —— 缓存的存在意义就是「同样的 prompt 不重复计费」，
     *                   缓存命中时平台并没有再付一次 provider 的钱。
     *                   费用列是账单口径（opc-finance 的聚合就是 SUM(cost)），
     *                   所以这里必须是 0, 否则等于对同一次 prompt 收两遍钱。
     */
    public void record(LlmGateway.ChatContext ctx, String model, ChatResponse resp, boolean fromCache) {
        if (resp == null) return;
        double inputPrice = resolveInputPrice(model);
        double outputPrice = resolveOutputPrice(model);

        int inputTokens = resp.getTokenInput() == null ? 0 : resp.getTokenInput();
        int outputTokens = resp.getTokenOutput() == null ? 0 : resp.getTokenOutput();
        int totalTokens = inputTokens + outputTokens;

        double cost = fromCache
                ? 0d
                : (inputTokens / 1000.0) * inputPrice + (outputTokens / 1000.0) * outputPrice;
        BigDecimal costBd = new BigDecimal(cost).setScale(4, RoundingMode.HALF_UP);

        String bizDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 归属（租户 + 用户）优先取调用方给的 ctx；ctx 里没有时从登录态补。
        //
        // 为什么要补: opc_agent_token_usage 的 company_id / user_id 都是 NOT NULL,
        // 而"谁在调 LLM"这件事服务端本来就知道(登录态里有) —— 让它白白丢成
        // 一个看不懂的 SQLIntegrityConstraintViolationException, 再被下面的 catch
        // 吞掉, 结果就是每一次调用的用量/成本记录都没了, 而账单页面读的就是这张表。
        Long companyId = ctx != null ? ctx.getCompanyId() : null;
        Long userId = ctx != null ? ctx.getUserId() : null;
        if (companyId == null || userId == null) {
            Long ctxCompany = currentCompanyId();
            Long ctxUser = currentUserId();
            if (companyId == null) companyId = ctxCompany;
            if (userId == null) userId = ctxUser;
            if (ctxCompany != null && ctxUser != null
                    && (ctx == null || ctx.getCompanyId() == null || ctx.getUserId() == null)) {
                log.debug("[TokenMeter] ctx 归属缺失, 已用登录态补齐 companyId={} userId={}", companyId, userId);
            }
        }

        if (companyId == null || userId == null) {
            // 补不上 = 这条记录注定写不进去(列是 NOT NULL)。与其让 SQL 抛一个
            // 语义不明的约束违例、再被 catch 吞掉, 不如在这里说清楚是哪边缺了。
            log.error("Token 计量归属缺失, 跳过落库 (companyId={}, userId={}, model={}, scene={}) —— "
                            + "该次调用的 token/成本没有计入账单",
                    companyId, userId, model, ctx != null ? ctx.getScene() : null);
            return;
        }

        TokenUsageRecord record = new TokenUsageRecord();
        record.companyId = companyId;
        record.userId = userId;
        record.instanceId = ctx != null ? ctx.getInstanceId() : null;
        record.taskId = ctx != null ? ctx.getTaskId() : null;
        record.model = model;
        // model_type 保持 CHAT —— 该列的取值是 CHAT/EMBEDDING，缓存命中也是一次
        // CHAT 调用。区分靠 request_id 的 "cache-" 前缀 (LlmGateway 在命中时打的)。
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

    /** 当前登录态的租户；无登录上下文（内部调用/定时任务）时返回 null。 */
    private Long currentCompanyId() {
        try {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            return loginUser == null ? null : loginUser.getCompanyId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前登录态的用户；无登录上下文时返回 null。 */
    private Long currentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private double resolveInputPrice(String model) {
        if (model == null) return OpcConstants.PRICE_DEEPSEEK_INPUT;
        if (model.startsWith("deepseek")) return OpcConstants.PRICE_DEEPSEEK_INPUT;
        if (model.startsWith("gpt-4o-mini")) return OpcConstants.PRICE_GPT_4O_MINI_INPUT;
        if (model.startsWith("gpt-4o")) return OpcConstants.PRICE_GPT_4O_INPUT;
        // W48.6: MiniMax(MiniMax) MiniMax-Text-01 走 MiniMax 价格
        if (model.startsWith("MiniMax") || model.startsWith("abab")) return OpcConstants.PRICE_MINIMAX_INPUT;
        return OpcConstants.PRICE_DEEPSEEK_INPUT;
    }

    private double resolveOutputPrice(String model) {
        if (model == null) return OpcConstants.PRICE_DEEPSEEK_OUTPUT;
        if (model.startsWith("deepseek")) return OpcConstants.PRICE_DEEPSEEK_OUTPUT;
        if (model.startsWith("gpt-4o-mini")) return OpcConstants.PRICE_GPT_4O_MINI_OUTPUT;
        if (model.startsWith("gpt-4o")) return OpcConstants.PRICE_GPT_4O_OUTPUT;
        if (model.startsWith("MiniMax") || model.startsWith("abab")) return OpcConstants.PRICE_MINIMAX_OUTPUT;
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
