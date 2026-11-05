package com.ruoyi.opc.ai.gateway.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「模型返回了没有正文的响应」不许被当成成功 —— 回归测试。
 *
 * <p><b>线上怎么炸的（2026-11-05）</b>：MiniMax 把 {@code MiniMax-Text-01} 下线了，
 * 换成 {@code MiniMax-M3.1-Flash} 之后调用「成功」了（HTTP 200、usage 里有 token），
 * 但响应体里<b>没有 {@code choices[0].message.content}</b> ——
 * M3.1-Flash 是<b>强制思考</b>模型，思考 token 计入 {@code max_tokens}，
 * 而调用方只给了 600 / 2048 —— 思考把额度吃光，
 * 于是返回 {@code finish_reason="length"} 且<b>正文字段整个不存在</b>。
 * 解析层把「没有 content」读成 {@code null}，
 * 一路传到工作流的 persist 层才炸成一句看不懂的「产出不是字符串: n2」。</p>
 *
 * <p><b>这三条断言守的是同一件事的三个位置</b>：</p>
 * <ol>
 *   <li><b>协议层</b>：请求体要给思考留出空间（{@code max_tokens} 下限）
 *       并显式带 {@code reasoning_effort} —— 不带的话 MiniMax 默认按
 *       {@code max} 思考，正文必然被挤掉；</li>
 *   <li><b>provider 层</b>：响应里没有正文 = <b>失败</b>，不是「成功但内容是 null」。
 *       否则降级链（DeepSeek）根本不会被触发；</li>
 *   <li><b>网关层</b>：provider 报成功但正文为空 → 当成失败换下一个 provider，
 *       并且<b>不许写进缓存</b> —— 写进去的话，一小时内每次同样提问都拿到那份空内容。</li>
 * </ol>
 *
 * <p><b>本测试用 JDK 自带的 {@code com.sun.net.httpserver.HttpServer} 起本地随机端口</b>
 * （不连外网、不花钱），并把<b>真实发出去的请求体</b>记下来 ——
 * 断言落在「厂商真的收到了什么」上，而不是落在 mock 的调用次数上。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("LLM 返回空正文不许被当成成功 单测")
class LlmEmptyContentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * M3.1-Flash 在 {@code max_tokens} 被思考吃光时的真实响应形状：
     * <b>没有 content 字段</b>（不是空串，是整个字段不存在）。
     */
    private static final String RESPONSE_WITHOUT_CONTENT = """
            {"id":"chatcmpl-x","model":"MiniMax-M3.1-Flash","object":"chat.completion",
             "choices":[{"index":0,"finish_reason":"length",
                         "message":{"role":"assistant","reasoning_content":"思考被 max_tokens 截断了"}}],
             "usage":{"prompt_tokens":229,"completion_tokens":2048,"total_tokens":2277}}
            """;

    private static final String RESPONSE_WITH_CONTENT = """
            {"id":"chatcmpl-y","model":"MiniMax-M3.1-Flash","object":"chat.completion",
             "choices":[{"index":0,"finish_reason":"stop",
                         "message":{"role":"assistant","content":"昨日共 6 笔流水。"}}],
             "usage":{"prompt_tokens":229,"completion_tokens":52,"total_tokens":281}}
            """;

    private HttpServer server;

    /** 真实发出去的请求体 —— 断言落在它身上，而不是落在 mock 上。 */
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    /**
     * 起一个本地随机端口的假厂商：回固定响应体，并把请求体记下来。
     */
    private void startFakeVendor(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(request, StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /**
     * 真 provider + 真 HTTP 客户端 + 真解析 ——
     * 只有 {@code @Value} 注入的字段用反射塞值（纯单测里它们是 null / 0）。
     *
     * <p>{@code minMaxTokens} 与 {@code reasoningEffort} 的<b>默认值</b>
     * （4096 / medium）写在 {@code @Value} 的默认值里，
     * 由「四份 yml 逐行一致」那条健康检查（26.11）守住；
     * 本测试守的是「provider 真的把这两个值用在了请求上」。</p>
     */
    private MiniMaxProvider realProvider() {
        MiniMaxProvider provider = new MiniMaxProvider(new HttpLlmClient());
        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "baseUrl", baseUrl());
        ReflectionTestUtils.setField(provider, "modelName", "MiniMax-M3.1-Flash");
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "minMaxTokens", 4096);
        ReflectionTestUtils.setField(provider, "reasoningEffort", "medium");
        return provider;
    }

    private JsonNode sentBody() throws Exception {
        return MAPPER.readTree(lastRequestBody.get());
    }

    // ==================== provider 层 ====================

    @Test
    @DisplayName("厂商返回了没有正文的响应 → 算失败，不是「成功但内容是 null」")
    void provider_responseWithoutContent_isFailure() throws IOException {
        startFakeVendor(RESPONSE_WITHOUT_CONTENT);

        ChatResponse resp = realProvider().chat(List.of(ChatMessage.user("写日报")),
                ChatModelProvider.ChatOptions.builder().maxTokens(600).build());

        // RED 的要害：以前这里返回 success=true、content=null ——
        // 降级链因此根本不会触发，null 一路漏到工作流层才炸。
        assertThat(resp.getSuccess()).isFalse();
        assertThat(resp.getErrorMessage()).contains("正文");
        // 失败原因里要带上 finish_reason —— 「被 max_tokens 截断」和「厂商抽风」是两回事
        assertThat(resp.getErrorMessage()).contains("length");
    }

    @Test
    @DisplayName("调用方只给 600 token → 请求体里的 max_tokens 仍要给思考留出空间")
    void provider_leavesRoomForThinking() throws Exception {
        startFakeVendor(RESPONSE_WITH_CONTENT);

        realProvider().chat(List.of(ChatMessage.user("写日报")),
                ChatModelProvider.ChatOptions.builder().maxTokens(600).build());

        int maxTokens = sentBody().path("max_tokens").asInt();
        // 思考 token 计入 max_tokens —— 600 会被思考吃光，正文一个字都剩不下
        assertThat(maxTokens).isGreaterThanOrEqualTo(4096);
    }

    @Test
    @DisplayName("请求体必须带 reasoning_effort —— 不带的话 MiniMax 默认按 max 思考，正文必然被挤掉")
    void provider_sendsReasoningEffort() throws Exception {
        startFakeVendor(RESPONSE_WITH_CONTENT);

        realProvider().chat(List.of(ChatMessage.user("写日报")),
                ChatModelProvider.ChatOptions.builder().maxTokens(600).build());

        assertThat(sentBody().path("reasoning_effort").asText()).isEqualTo("medium");
    }

    // ==================== 网关层 ====================

    @Mock
    private ChatModelProvider primary;
    @Mock
    private ChatModelProvider backup;
    @Mock
    private TokenMeter tokenMeter;
    @Mock
    private LlmCache cache;

    private void enable(ChatModelProvider p, int priority, String model) {
        when(p.enabled()).thenReturn(true);
        when(p.priority()).thenReturn(priority);
        when(p.model()).thenReturn(model);
    }

    /** provider「成功」但正文是空的 —— 这正是 2026-11-05 线上遇到的那个形状。 */
    private ChatResponse successButBlank() {
        return ChatResponse.builder()
                .model("MiniMax-M3.1-Flash")
                .success(true)
                .content(null)
                .tokenInput(229)
                .tokenOutput(2048)
                .build();
    }

    @Test
    @DisplayName("provider 报成功但正文为空 → 当成失败，换下一个 provider（降级链真的接上了）")
    void gateway_blankContent_fallsBackToNextProvider() {
        enable(primary, 5, "MiniMax-M3.1-Flash");
        enable(backup, 10, "deepseek-chat");
        when(primary.chat(anyList(), any())).thenReturn(successButBlank());
        when(backup.chat(anyList(), any())).thenReturn(ChatResponse.builder()
                .content("备模型的答案").success(true).tokenInput(10).tokenOutput(5).build());

        LlmGateway gateway = new LlmGateway(List.of(primary, backup), tokenMeter, cache);

        ChatResponse resp = gateway.chat(List.of(ChatMessage.user("hi")), null, null);

        assertThat(resp.getContent()).isEqualTo("备模型的答案");
        verify(primary).chat(anyList(), any());
        verify(backup).chat(anyList(), any());
    }

    @Test
    @DisplayName("空正文的响应不许写进缓存 —— 写进去的话一小时内每次都拿到那份空内容")
    void gateway_blankContent_isNotCached() {
        enable(primary, 5, "MiniMax-M3.1-Flash");
        enable(backup, 10, "deepseek-chat");
        when(primary.chat(anyList(), any())).thenReturn(successButBlank());
        when(backup.chat(anyList(), any())).thenReturn(ChatResponse.builder()
                .content("备模型的答案").success(true).build());

        LlmGateway gateway = new LlmGateway(List.of(primary, backup), tokenMeter, cache);

        gateway.chat(List.of(ChatMessage.user("hi")), null, null);

        // 备模型的正常响应**该**被缓存 —— 这条守的是「空正文那份不许进去」。
        // 所以不能断言「一次都没 put」，得看 put 进去的那份是不是空正文。
        ArgumentCaptor<ChatResponse> captor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(cache).put(any(), captor.capture(), anyDouble());
        assertThat(captor.getValue().getContent()).isNotBlank();
    }
}
