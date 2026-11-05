package com.ruoyi.opc.ai.gateway.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MiniMax(MiniMaxAI) 适配器（OAC-W48.6）
 *
 * <p>API 协议：OpenAI 兼容 {@code /v1/chat/completions}。
 * base-url 默认 {@code https://api.minimaxi.com/v1}，
 * 鉴权走 {@code Authorization: Bearer <MINIMAX_API_KEY>}。
 *
 * <p>priority=5，比 DeepSeek 优先（10），让用户在 Nacos 配 {@code opc.llm.primary=MiniMax-M3.1-Flash}
 * 时默认走 MiniMax。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniMaxProvider implements ChatModelProvider {

    private final HttpLlmClient http;

    @Value("${opc.llm.minimax.api-key:}")
    private String apiKey;
    @Value("${opc.llm.minimax.base-url:https://api.minimaxi.com/v1}")
    private String baseUrl;
    // MiniMax-Text-01 已被官方下线（HTTP 404 not_found_error："The model MiniMax-Text-01 is no longer available"），
    // 改成 MiniMax-M3.1-Flash；谁要是「恢复旧值」，先去看 MiniMax 的 /v1/models。
    @Value("${opc.llm.minimax.model:MiniMax-M3.1-Flash}")
    private String modelName;
    @Value("${opc.llm.minimax.enabled:true}")
    private boolean enabled;

    /**
     * 思考强度（{@code reasoning_effort}）。
     *
     * <p><b>为什么必须显式传</b>：M3.1-Flash <b>不能关闭思考</b>，
     * 而这个字段不传时厂商默认按 {@code max} 思考 —— 思考 token 计入
     * {@code max_tokens}，于是正文被挤掉，响应里连 {@code content} 字段都没有
     * （2026-11-05 线上就是这个坑：调用「成功」、正文是 null）。</p>
     */
    @Value("${opc.llm.minimax.reasoning-effort:medium}")
    private String reasoningEffort;

    /**
     * {@code max_tokens} 的下限 —— <b>给思考留出空间</b>。
     *
     * <p>调用方给的额度是按「正文长度」定的（insight 的建议 600、AgentRuntime 2048），
     * 而 M3.1-Flash 的思考 token <b>从同一个额度里扣</b>。额度不够 → 正文为空。
     * 所以这里给一个下限，调用方给得更多就按调用方的来。</p>
     */
    @Value("${opc.llm.minimax.min-max-tokens:4096}")
    private int minMaxTokens;

    @Override public String name() { return "minimax"; }
    @Override public String model() { return modelName; }
    @Override public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }
    @Override public int priority() { return 5; }

    /**
     * 把调参调成「这个模型真的能回得出正文」的样子。
     *
     * <p>只动两处：{@code max_tokens} 抬到下限以上、{@code reasoning_effort} 补上。
     * 其余原样传下去。</p>
     */
    private ChatOptions withThinkingRoom(ChatOptions options) {
        ChatOptions src = (options == null) ? ChatOptions.builder().build() : options;
        int floor = Math.max(1, minMaxTokens);
        Integer callerMax = src.getMaxTokens();
        int effectiveMax = (callerMax == null || callerMax < floor) ? floor : callerMax;
        String effort = (src.getReasoningEffort() == null || src.getReasoningEffort().isBlank())
                ? reasoningEffort : src.getReasoningEffort();
        return ChatOptions.builder()
                .temperature(src.getTemperature())
                .maxTokens(effectiveMax)
                .tools(src.getTools())
                .toolChoice(src.getToolChoice())
                .user(src.getUser())
                .timeoutSeconds(src.getTimeoutSeconds())
                .reasoningEffort(effort)
                .build();
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
        long start = System.currentTimeMillis();
        try {
            String raw = http.callChatCompletions(baseUrl, apiKey, modelName, messages, withThinkingRoom(options));
            HttpLlmClient.ParsedChatResponse p = http.parseChatResponse(raw);
            // 「成功但没有正文」= <b>失败</b>。
            //
            // 以前这里直接把 content=null 当成功返回 —— 于是降级链（DeepSeek）根本不会被触发，
            // null 一路漏到工作流的 persist 层才炸成「产出不是字符串: n2」，
            // 而真正的病灶（思考吃满了 max_tokens）没人看得见。
            if (p.content() == null || p.content().isBlank()) {
                String why = "MiniMax 返回了空正文(finish_reason="
                        + (p.finishReason() == null ? "null" : p.finishReason())
                        + ") —— 思考 token 吃满了 max_tokens, 或厂商没给出正文";
                log.error("[MiniMax] {}", why);
                return ChatResponse.builder()
                        .model(modelName)
                        .success(false)
                        .errorMessage(why)
                        .latencyMs(System.currentTimeMillis() - start)
                        .rawJson(raw)
                        .build();
            }
            return ChatResponse.builder()
                    .requestId(p.requestId() != null ? p.requestId() : ("minimax-" + System.currentTimeMillis()))
                    .model(p.model() != null ? p.model() : modelName)
                    .content(p.content())
                    .tokenInput(p.inputTokens())
                    .tokenOutput(p.outputTokens())
                    .tokenTotal(p.totalTokens())
                    .finishReason(p.finishReason())
                    .latencyMs(System.currentTimeMillis() - start)
                    .success(true)
                    .rawJson(raw)
                    .build();
        } catch (LlmHttpException e) {
            // 有状态码 → 上层能判断该不该重试（429/4xx 不重试，5xx 重试）。
            // catch 顺序：LlmHttpException 必须在 Exception 前面（它是子类）。
            log.error("[MiniMax] 调用失败 httpStatus={}", e.getStatusCode(), e);
            return ChatResponse.builder()
                    .model(modelName)
                    .success(false)
                    .httpStatus(e.getStatusCode())
                    .errorMessage("MiniMax 调用失败(HTTP " + e.getStatusCode() + "): " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            // 网络错 / 超时 / 解析错 → httpStatus 留 null → 上层按「可重试」处理。
            // 不要设 0 或 -1：isRetryable 判的是 status == null。
            log.error("[MiniMax] 调用失败", e);
            return ChatResponse.builder()
                    .model(modelName)
                    .success(false)
                    .errorMessage("MiniMax 调用失败: " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, ChatOptions options, StreamCallback callback) {
        http.chatStream(baseUrl, apiKey, modelName, messages, options, callback);
    }
}
