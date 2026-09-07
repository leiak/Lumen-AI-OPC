package com.ruoyi.gateway.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GatewayExceptionHandler} 单元测试 — W9.3（网关集成测试）
 *
 * <p>覆盖 4 个分支 + 1 个 annotation 契约：
 * <ul>
 *   <li><b>1 NotFoundException</b>：下游服务不存在 → JSON body 含 "服务未找到"</li>
 *   <li><b>2 ResponseStatusException</b>：HTTP 4xx/5xx 异常 → JSON body 含 ex.getMessage()</li>
 *   <li><b>3 其他异常</b>：任意 RuntimeException → JSON body 含 "内部服务器错误"</li>
 *   <li><b>4 Response 已提交</b>：isCommitted=true → 返回 Mono.error(ex)（无法再写 response）</li>
 *   <li><b>5 @Order(-1)</b>：handler 优先级 -1（高于 @ControllerAdvice 默认），确保先于其他 handler 拦截</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@link MockServerWebExchange} + {@link MockServerHttpResponse} 模拟 WebFlux 异常流</li>
 *   <li>验证 handler 把异常映射成 R 结构 JSON body（{@code {"code":500, "msg":"..."}}）</li>
 *   <li>通过反射验证 {@code @Order(-1)} 契约（gateway 必须最先处理异常，避免 NPE 等破坏响应）</li>
 * </ul>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class GatewayExceptionHandlerTest {

    private GatewayExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayExceptionHandler();
    }

    // ==================== 1. NotFoundException ====================

    @Test
    @DisplayName("NotFoundException → JSON body 含 '服务未找到'")
    void notFoundException_returnsServiceNotFoundMessage() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/opc/foo"));
        Throwable ex = new NotFoundException("no such service");

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        String body = responseBody(exchange);
        assertTrue(body.contains("服务未找到"),
                "NotFoundException 应被映射为 '服务未找到'，实际 body: " + body);
        assertTrue(body.contains("\"code\":500"),
                "R.FAIL 默认 code=500，实际 body: " + body);
    }

    // ==================== 2. ResponseStatusException ====================

    @Test
    @DisplayName("ResponseStatusException → JSON body 含 ex.getMessage()")
    void responseStatusException_returnsOriginalMessage() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/opc/bar"));
        Throwable ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "下游暂时不可用");

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        String body = responseBody(exchange);
        assertTrue(body.contains("下游暂时不可用"),
                "ResponseStatusException 应透传原 message，实际 body: " + body);
    }

    @Test
    @DisplayName("ResponseStatusException(404, 'NOT FOUND') → body 含原 message")
    void responseStatusException404_messagePropagates() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/missing"));
        Throwable ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "resource gone");

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertTrue(responseBody(exchange).contains("resource gone"));
    }

    // ==================== 3. 其他异常 ====================

    @Test
    @DisplayName("RuntimeException（非 NotFound/ResponseStatus）→ JSON body 含 '内部服务器错误'")
    void otherException_returnsInternalServerErrorMessage() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/opc/baz"));
        Throwable ex = new RuntimeException("NullPointerException at line 42");

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        String body = responseBody(exchange);
        assertTrue(body.contains("内部服务器错误"),
                "未分类异常应映射为 '内部服务器错误'，原 message 不应泄露（防信息泄漏），实际 body: " + body);
        assertFalse(body.contains("NullPointerException"),
                "原 exception message 不应直接泄露给客户端");
    }

    @Test
    @DisplayName("IllegalStateException → 同样走 '内部服务器错误' 分支")
    void illegalStateException_alsoMapsToInternalError() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/opc/qux"));
        Throwable ex = new IllegalStateException("state machine invalid");

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertTrue(responseBody(exchange).contains("内部服务器错误"));
    }

    // ==================== 4. Response 已提交 ====================

    @Test
    @DisplayName("Response 已提交（isCommitted=true）→ 返回 Mono.error(ex)，不再尝试写 response")
    void committedResponse_returnsMonoError() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/opc/committed"));
        // 让 MockServerHttpResponse 实际标记为已提交（写一个空 DataBuffer 后 setComplete）
        exchange.getResponse().setComplete().block();
        assertTrue(exchange.getResponse().isCommitted(),
                "sanity: setComplete() 后 isCommitted 必须为 true");

        Throwable ex = new RuntimeException("already flushed");

        // 关键断言：handler 必须返回 Mono.error(ex) 而不是写入新 response
        StepVerifier.create(handler.handle(exchange, ex))
                .expectErrorMatches(throwable -> throwable == ex)
                .verify();
    }

    // ==================== 5. @Order(-1) 契约 ====================

    @Test
    @DisplayName("@Order(-1) 优先级 — 反射验证 handler 优先级最高")
    void handlerOrder_isMinusOne() throws Exception {
        org.springframework.core.annotation.Order orderAnnotation =
                GatewayExceptionHandler.class.getAnnotation(org.springframework.core.annotation.Order.class);
        assertNotNull(orderAnnotation, "GatewayExceptionHandler 必须标注 @Order");
        assertEquals(-1, orderAnnotation.value(),
                "handler 优先级必须为 -1 — 高于其他 @ControllerAdvice（默认 Ordered.LOWEST_PRECEDENCE）");
    }

    @Test
    @DisplayName("implements ErrorWebExceptionHandler — 反射验证契约")
    void implementsErrorWebExceptionHandler() {
        assertTrue(org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler.class
                        .isAssignableFrom(GatewayExceptionHandler.class),
                "GatewayExceptionHandler 必须实现 ErrorWebExceptionHandler — Spring Boot 才能注册为 gateway 全局异常处理器");
    }

    // ==================== 测试辅助 ====================

    /**
     * 从 MockServerHttpResponse 读取写入的字节并转字符串。
     */
    private String responseBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getResponse().getBody())
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .block();
    }
}