package com.ruoyi.opc.ai.gateway.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpLlmClient} chat 通道的 <b>HTTP 状态码结构化</b>回归测试。
 *
 * <p><b>为什么必须有这条测试</b>：本 Task 的核心卖点是「厂商返回 429 / 503 时，
 * 状态码是一个<b>结构化字段</b>（{@link LlmHttpException#getStatusCode()}），
 * 上层才能判断『该不该重试』」。但在此之前，{@code HttpLlmClient} 只是把状态码
 * 拼进错误消息字符串就扔了 —— 那等于让上层去 {@code grep} 一个中文句子，
 * 是 CLAUDE.md 坑 #8 的同构病。</p>
 *
 * <p>本测试用 JDK 自带的 {@code com.sun.net.httpserver.HttpServer} 起一个
 * <b>本地随机端口</b>（不连外网、不依赖任何容器），真的发一次 HTTP 请求，
 * 走完「厂商返 429 → 抛异常 → 类型/状态码都对」这条链路。</p>
 *
 * <p><b>断言必须是「异常类型 + getStatusCode()」两样都要</b>：
 * 只断言「抛异常」或「消息里含 429」都测不出来 ——
 * 因为状态码即使被包成普通 {@code RuntimeException}，消息里照样含 "429"。
 * 真正的差别是：catch 顺序反了 → {@code LlmHttpException} 被
 * {@code catch (Exception)} 接住再包一层 → 抛出来的是普通 {@code RuntimeException}，
 * 状态码就又只剩字符串了。</p>
 *
 * @author OAC
 */
@DisplayName("HttpLlmClient chat 通道 HTTP 状态码 单测")
class HttpLlmClientChatTest {

    private HttpServer server;

    private HttpLlmClient client;

    /** 记录真实发出去几次请求 —— 证明「真发了 HTTP」，不是空跑。 */
    private final AtomicInteger requestCount = new AtomicInteger();

    /**
     * 起一个本地随机端口的假厂商，返回固定的 HTTP 状态码与响应体。
     */
    private void startFakeVendor(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    /** {@code HttpLlmClient.callChatCompletions} 自己会拼 {@code /chat/completions}。 */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private ChatModelProvider.ChatOptions options() {
        return ChatModelProvider.ChatOptions.builder()
                .timeoutSeconds(5)
                .maxTokens(16)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("厂商返回 429 → 抛 LlmHttpException，状态码 429 是结构化字段")
    void callChatCompletions_non2xx_throwsLlmHttpExceptionWithStatus() throws Exception {
        startFakeVendor(429, "{\"error\":{\"message\":\"rate limit reached\"}}");
        client = new HttpLlmClient();

        LlmHttpException e = assertThrows(LlmHttpException.class, () ->
                client.callChatCompletions(baseUrl(), "test-key", "deepseek-chat",
                        List.of(ChatMessage.user("hi")), options()));

        // 类型必须精确是 LlmHttpException —— 被 catch(Exception) 包成普通 RuntimeException 就是回归。
        assertEquals(LlmHttpException.class, e.getClass(),
                "必须是 LlmHttpException 本身，不能被包一层变成普通 RuntimeException（状态码会又只剩字符串）");
        assertEquals(429, e.getStatusCode(), "状态码必须是结构化字段，不能只拼进消息字符串");
        assertEquals(1, requestCount.get(), "必须真的发了一次 HTTP 请求");
    }

    @Test
    @DisplayName("厂商返回 503 → 抛 LlmHttpException，状态码 503 是结构化字段")
    void callChatCompletions_5xx_throwsLlmHttpExceptionWithStatus() throws Exception {
        startFakeVendor(503, "{\"error\":{\"message\":\"service unavailable\"}}");
        client = new HttpLlmClient();

        LlmHttpException e = assertThrows(LlmHttpException.class, () ->
                client.callChatCompletions(baseUrl(), "test-key", "deepseek-chat",
                        List.of(ChatMessage.user("hi")), options()));

        assertEquals(LlmHttpException.class, e.getClass(),
                "必须是 LlmHttpException 本身，不能被包一层变成普通 RuntimeException（状态码会又只剩字符串）");
        assertEquals(503, e.getStatusCode(), "状态码必须是结构化字段，不能只拼进消息字符串");
        assertEquals(1, requestCount.get(), "必须真的发了一次 HTTP 请求");
    }

    @Test
    @DisplayName("厂商返回 200 → 正常返回响应体，不抛异常")
    void callChatCompletions_2xx_returnsBody() throws Exception {
        String okBody = "{\"id\":\"chatcmpl-1\",\"model\":\"deepseek-chat\","
                + "\"choices\":[{\"message\":{\"content\":\"你好\"}}],"
                + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}";
        startFakeVendor(200, okBody);
        client = new HttpLlmClient();

        String raw = client.callChatCompletions(baseUrl(), "test-key", "deepseek-chat",
                List.of(ChatMessage.user("hi")), options());

        assertEquals(okBody, raw, "2xx 必须原样返回响应体");
        assertEquals(1, requestCount.get(), "必须真的发了一次 HTTP 请求");
    }
}
