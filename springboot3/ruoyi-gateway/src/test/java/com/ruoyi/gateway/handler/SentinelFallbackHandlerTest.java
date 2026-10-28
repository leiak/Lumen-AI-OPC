package com.ruoyi.gateway.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.ruoyi.gateway.config.GatewayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SentinelFallbackHandler} 单元测试
 *
 * <p><b>这条测试是为了钉住一个曾经撒过谎的行为</b>：
 * 限流命中时，网关以前返回的是 <b>HTTP 200</b> + {@code {"code":500,"msg":"请求超过最大数，请稍候再试"}}。
 * 被限流的请求和成功的请求在 HTTP 层面一模一样 ——
 * 客户端拿 200 当成功、监控里一个 429 都看不到、
 * 「限流到底生效没有」只能靠翻 INFO 日志猜。
 *
 * <p>现在必须返回 <b>429 Too Many Requests</b>，body 的 code 也跟着是 429。
 *
 * @author OAC
 */
class SentinelFallbackHandlerTest {

    private SentinelFallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SentinelFallbackHandler();
    }

    // ==================== 1. 限流命中 → 429 ====================

    @Test
    @DisplayName("限流命中 → HTTP 429（不是 200）+ body 里说清是限流")
    void blockException_returnsHttp429() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/llm/models"));
        FlowException ex = new FlowException("opc-ai-core", new FlowRule());

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode(),
                "被限流的请求必须返回 429 —— 返回 200 等于告诉客户端「成功了」");
    }

    @Test
    @DisplayName("限流命中 → body 是 RuoYi 信封 {code,msg,data}，code=429")
    void blockException_bodyUsesRuoYiEnvelope() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/llm/models"));
        FlowException ex = new FlowException("opc-ai-core", new FlowRule());

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        String body = responseBody(exchange);
        assertTrue(body.contains("\"code\":429"),
                "body 里的 code 应与 HTTP 状态码一致（429），实际 body: " + body);
        assertTrue(body.contains("请求超过最大数"),
                "body 应说明是被限流了，实际 body: " + body);
        assertTrue(body.contains("\"msg\""),
                "body 应沿用 RuoYi 的 msg 字段（前端按 code/msg 读），实际 body: " + body);
    }

    // ==================== 2. 不是限流异常 → 不许插手 ====================

    @Test
    @DisplayName("非限流异常 → 返回 Mono.error(ex)，不写 response")
    void nonBlockException_returnsMonoError() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/foo"));
        Throwable ex = new RuntimeException("普通异常，不是限流");

        StepVerifier.create(handler.handle(exchange, ex))
                .expectErrorMatches(t -> t == ex)
                .verify();
    }

    // ==================== 3. response 已提交 ====================

    @Test
    @DisplayName("Response 已提交 → 返回 Mono.error(ex)，不再写 response")
    void committedResponse_returnsMonoError() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/committed"));
        exchange.getResponse().setComplete().block();
        assertTrue(exchange.getResponse().isCommitted(),
                "sanity: setComplete() 后 isCommitted 必须为 true");

        FlowException ex = new FlowException("opc-ai-core", new FlowRule());

        StepVerifier.create(handler.handle(exchange, ex))
                .expectErrorMatches(t -> t == ex)
                .verify();
    }

    // ==================== 4. 优先级契约 ====================

    @Test
    @DisplayName("限流 handler 的优先级最高 — 必须抢在 Sentinel 自带 handler 前面")
    void fallbackHandlerIsRegisteredWithHighestPrecedence() {
        // bean 本身在 GatewayConfig 里注册，@Order 标在 @Bean 方法上，
        // 所以这里反射 GatewayConfig 上的 @Bean 方法
        try {
            var method = GatewayConfig.class.getMethod("sentinelGatewayExceptionHandler");
            Order order = method.getAnnotation(Order.class);
            assertNotNull(order, "sentinelGatewayExceptionHandler 必须标注 @Order");
            assertEquals(org.springframework.core.Ordered.HIGHEST_PRECEDENCE, order.value(),
                    "限流 handler 必须是最高优先级 —— 否则 Sentinel 自带的 handler 会先处理，"
                            + "RuoYi 的 body 信封就用不上了");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("GatewayConfig 必须保留 sentinelGatewayExceptionHandler 这个 bean", e);
        }
    }

    @Test
    @DisplayName("BlockException.isBlockException 能认出限流异常（前置条件自检）")
    void flowExceptionIsRecognisedAsBlockException() {
        FlowException ex = new FlowException("opc-ai-core", new FlowRule());
        assertTrue(BlockException.isBlockException(ex),
                "sanity: FlowException 必须被 Sentinel 认成限流异常，否则 handler 不会接管");
    }

    // ==================== 测试辅助 ====================

    private String responseBody(ServerWebExchange exchange) {
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        return DataBufferUtils.join(response.getBody())
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .block();
    }
}
