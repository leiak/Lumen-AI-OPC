package com.ruoyi.opc.insight.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.insight.enums.AnomalyLevel;
import com.ruoyi.opc.insight.service.ISoftAnomalyDetector;
import com.ruoyi.opc.insight.vo.AnomalyVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 LlmGateway 的软异常检测实现（M4 Task 6）。
 *
 * <p>流程：
 * <ol>
 *   <li>把 KpiSnapshot 序列化为简短文本 prompt；</li>
 *   <li>调用 {@link LlmGateway#chat(List, ChatModelProvider.ChatOptions, LlmGateway.ChatContext)}；</li>
 *   <li>期望 LLM 返回严格 JSON 数组
 *       {@code [{"ruleCode":"...","level":"HIGH|MEDIUM|LOW","description":"...","confidence":0.0-1.0}, ...]}；</li>
 *   <li>JSON 解析失败 / LLM 调用失败 → 返回空列表（让 {@link AnomalyServiceImpl}
 *       的硬规则结果继续生效，<b>不阻断主流程</b>）。</li>
 * </ol>
 *
 * <p><b>失败容忍</b>：所有异常被 {@code try/catch} 吞掉并打 WARN 日志。
 * INSIGHT 是辅助决策系统，软扫失败不应影响硬规则结果，也绝不能抛出。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSoftAnomalyDetector implements ISoftAnomalyDetector {

    private final LlmGateway llmGateway;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<AnomalyVo> detect(KpiSnapshot snapshot) {
        if (snapshot == null) {
            return Collections.emptyList();
        }
        try {
            ChatResponse resp = llmGateway.chat(
                    buildPrompt(snapshot),
                    ChatModelProvider.ChatOptions.builder()
                            .temperature(0.2)
                            .maxTokens(800)
                            .build(),
                    LlmGateway.ChatContext.builder()
                            .companyId(snapshot.getCompanyId())
                            .scene("INSIGHT_SOFT_ANOMALY")
                            .build());
            return parseResponse(resp);
        } catch (Exception e) {
            log.warn("[INSIGHT] LLM 软异常检测失败：companyId={} period={} err={}",
                    snapshot.getCompanyId(), snapshot.getPeriod(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ChatMessage> buildPrompt(KpiSnapshot s) {
        String sys = "你是 OPC 财务异常分析助手。请基于用户给出的 KPI 快照,识别潜在的财务/经营异常,"
                + "并以严格 JSON 数组形式返回: "
                + "[{\"ruleCode\":\"LLM_<your_code>\",\"level\":\"HIGH|MEDIUM|LOW\","
                + "\"description\":\"<中文一句话>\",\"confidence\":0.0-1.0}, ...]. "
                + "若无可疑项返回 []。仅输出 JSON,不要解释。";
        String user = String.format(
                "companyId=%d; period=%s; revenue=%s; expense=%s; "
                        + "voucherCount=%s; pendingVoucherCount=%s; "
                        + "walletBalance=%s; tokenUsage=%s; activeDays=%s; partial=%s",
                s.getCompanyId(), s.getPeriod(),
                s.getTotalRevenue(), s.getTotalExpense(),
                s.getVoucherCount(), s.getPendingVoucherCount(),
                s.getWalletBalance(), s.getTokenUsage(),
                s.getCompanyActiveDays(), s.isPartial());
        return List.of(ChatMessage.system(sys), ChatMessage.user(user));
    }

    /**
     * 解析 LLM 响应为 AnomalyVo 列表。
     *
     * <p>提取策略：先尝试从 {@code rawJson}（若有）解析；若失败则尝试
     * {@code content}（直接 JSON 或 markdown ```json 包裹）。
     */
    private List<AnomalyVo> parseResponse(ChatResponse resp) {
        if (resp == null || resp.getContent() == null) {
            return Collections.emptyList();
        }
        String json = stripCodeFence(resp.getContent());
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(this::toVo)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("[INSIGHT] LLM 返回内容无法解析为 JSON 数组：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private AnomalyVo toVo(Map<String, Object> raw) {
        try {
            Object confObj = raw.get("confidence");
            BigDecimal conf = null;
            if (confObj instanceof Number n) {
                conf = BigDecimal.valueOf(n.doubleValue());
            }
            AnomalyLevel level = parseLevel((String) raw.get("level"));
            return AnomalyVo.builder()
                    .ruleCode(asString(raw.get("ruleCode"), "LLM_UNKNOWN"))
                    .description(asString(raw.get("description"), null))
                    .level(level)
                    .llmConfidence(conf)
                    .build();
        } catch (Exception e) {
            log.warn("[INSIGHT] 单条 LLM 异常记录解析失败：{}", e.getMessage());
            return null;
        }
    }

    private static AnomalyLevel parseLevel(String s) {
        if (s == null) return AnomalyLevel.LOW;
        try {
            return AnomalyLevel.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return AnomalyLevel.LOW;
        }
    }

    private static String asString(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o);
    }

    /**
     * 移除 markdown ```json ... ``` 包裹，便于宽松解析 LLM 输出。
     */
    private static String stripCodeFence(String s) {
        String trimmed = s.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
