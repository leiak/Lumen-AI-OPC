package com.ruoyi.opc.ai.gateway.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 共享 OpenAI-兼容 HTTP 客户端：DeepSeek / MiniMax / 任何走 /v1/chat/completions 的厂商。
 *
 * <p>设计要点（OAC-W48.6）：
 * <ul>
 *   <li>用 JDK 11+ {@link HttpClient}，零额外依赖（ai-core 是 Servlet 栈，没 WebClient）</li>
 *   <li>统一 Bearer auth + JSON body + 响应解析</li>
 *   <li>timeout 来自 {@link ChatModelProvider.ChatOptions#getTimeoutSeconds()}，默认 60s</li>
 *   <li>流式（SSE）走 {@link #chatStream}，逐行解析 {@code data: {...}} 推 callback</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Component
public class HttpLlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 同步 chat 调用。
     *
     * @param baseUrl     形如 {@code https://api.deepseek.com/v1}，方法会自动拼 {@code /chat/completions}
     * @param apiKey      Bearer token
     * @param model       模型名（gpt-4o-mini / deepseek-chat / MiniMax-Text-01 等）
     * @param messages    业务消息
     * @param options     调参（temperature / maxTokens / tools...）
     * @return 原始响应 JSON（解析失败的 fallback）
     */
    public String callChatCompletions(String baseUrl, String apiKey, String model,
                                      List<ChatMessage> messages, ChatModelProvider.ChatOptions options) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode msgs = body.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode mn = msgs.addObject();
            mn.put("role", m.getRole().name().toLowerCase());
            mn.put("content", m.getContent() == null ? "" : m.getContent());
        }
        if (options != null) {
            if (options.getTemperature() != null) body.put("temperature", options.getTemperature());
            if (options.getMaxTokens() != null) body.put("max_tokens", options.getMaxTokens());
            if (options.getTools() != null) body.set("tools", MAPPER.valueToTree(options.getTools()));
            if (options.getToolChoice() != null) body.put("tool_choice", options.getToolChoice());
            if (options.getUser() != null) body.put("user", options.getUser());
        }

        int timeoutSec = (options != null && options.getTimeoutSeconds() != null) ? options.getTimeoutSeconds() : 60;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("HTTP " + resp.statusCode() + " from " + baseUrl + ": " + resp.body());
            }
            return resp.body();
        } catch (Exception e) {
            throw new RuntimeException("LLM HTTP call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 从 OpenAI 兼容 JSON 响应里提取关键字段。
     * 例：{@code {"choices":[{"message":{"content":"hi"}}],"usage":{"prompt_tokens":10,"completion_tokens":5}}}
     */
    public ParsedChatResponse parseChatResponse(String rawJson) throws Exception {
        JsonNode root = MAPPER.readTree(rawJson);
        String content = null;
        String finishReason = null;
        if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
            JsonNode choice = root.get("choices").get(0);
            JsonNode msg = choice.get("message");
            if (msg != null && msg.has("content")) {
                content = msg.get("content").asText();
            }
            if (choice.has("finish_reason")) {
                finishReason = choice.get("finish_reason").asText();
            }
        }
        int inTok = 0, outTok = 0;
        if (root.has("usage")) {
            JsonNode u = root.get("usage");
            if (u.has("prompt_tokens")) inTok = u.get("prompt_tokens").asInt();
            if (u.has("completion_tokens")) outTok = u.get("completion_tokens").asInt();
        }
        String reqId = root.has("id") ? root.get("id").asText() : null;
        String model = root.has("model") ? root.get("model").asText() : null;
        return new ParsedChatResponse(content, finishReason, inTok, outTok, inTok + outTok, reqId, model);
    }

    /**
     * 流式（SSE）调用。每收到一个 {@code data: {...}} 就把 content delta 推给 callback。
     * 遇到 {@code data: [DONE]} 结束；任意非 2xx 抛错。
     */
    public void chatStream(String baseUrl, String apiKey, String model,
                           List<ChatMessage> messages, ChatModelProvider.ChatOptions options,
                           ChatModelProvider.StreamCallback callback) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", true);
        ArrayNode msgs = body.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode mn = msgs.addObject();
            mn.put("role", m.getRole().name().toLowerCase());
            mn.put("content", m.getContent() == null ? "" : m.getContent());
        }
        if (options != null) {
            if (options.getTemperature() != null) body.put("temperature", options.getTemperature());
            if (options.getMaxTokens() != null) body.put("max_tokens", options.getMaxTokens());
        }

        int timeoutSec = (options != null && options.getTimeoutSeconds() != null) ? options.getTimeoutSeconds() : 120;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<java.io.InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                byte[] err = resp.body().readAllBytes();
                throw new RuntimeException("SSE HTTP " + resp.statusCode() + ": " + new String(err, StandardCharsets.UTF_8));
            }
            StringBuilder fullContent = new StringBuilder();
            int inTok = 0, outTok = 0;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || !line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) break;
                    try {
                        JsonNode n = MAPPER.readTree(payload);
                        if (n.has("choices") && n.get("choices").size() > 0) {
                            JsonNode delta = n.get("choices").get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String c = delta.get("content").asText();
                                fullContent.append(c);
                                callback.onChunk(c);
                            }
                        }
                        if (n.has("usage")) {
                            JsonNode u = n.get("usage");
                            if (u.has("prompt_tokens")) inTok = u.get("prompt_tokens").asInt();
                            if (u.has("completion_tokens")) outTok = u.get("completion_tokens").asInt();
                        }
                    } catch (Exception parseErr) {
                        log.warn("[HttpLlmClient] SSE 块解析失败: {}", parseErr.getMessage());
                    }
                }
            }
            ChatResponse finalResp = ChatResponse.builder()
                    .requestId("sse-" + System.currentTimeMillis())
                    .model(model)
                    .content(fullContent.toString())
                    .tokenInput(inTok)
                    .tokenOutput(outTok)
                    .tokenTotal(inTok + outTok)
                    .finishReason("stop")
                    .success(true)
                    .build();
            callback.onComplete(finalResp);
        } catch (Exception e) {
            log.error("[HttpLlmClient] SSE 调用异常", e);
            callback.onError(e);
        }
    }

    /** {@link #parseChatResponse} 返回的扁平 DTO。 */
    public record ParsedChatResponse(String content, String finishReason,
                                     int inputTokens, int outputTokens, int totalTokens,
                                     String requestId, String model) {}
}
