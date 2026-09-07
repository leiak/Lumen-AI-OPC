package com.ruoyi.gateway.filter;

import com.ruoyi.common.core.exception.CaptchaException;
import com.ruoyi.gateway.config.properties.CaptchaProperties;
import com.ruoyi.gateway.service.ValidateCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link ValidateCodeFilter} 单元测试 — W10.1.2（网关剩余 filter 单测）
 *
 * <p>覆盖 5 个分支：
 * <ul>
 *   <li><b>1 非登录/注册 URL</b>：URL 不是 {@code /auth/login} 或 {@code /auth/register} → chain.filter 跳过</li>
 *   <li><b>2 验证码关闭</b>：{@code captchaProperties.enabled=false} → chain.filter 跳过</li>
 *   <li><b>3 登录 URL + 验证码开 + 合法 code + uuid</b> → chain.filter 通过</li>
 *   <li><b>4 登录 URL + 验证码开 + checkCaptcha 抛 CaptchaException</b> → 200 + body 含错误信息</li>
 *   <li><b>5 登录 URL + 验证码开 + body 不是 JSON</b>（JSON.parseObject 抛异常） → 500 错误信息</li>
 * </ul>
 *
 * <p>关键设计点：
 * <ul>
 *   <li>{@link ValidateCodeFilter} 用 {@code @Autowired} 注入 → 单测用 {@link ReflectionTestUtils#setField} 注入 mock</li>
 *   <li>{@link CaptchaProperties} 默认 {@code enabled=null}（不是 false）→ 必须显式 {@code setEnabled(false)} 才跳过</li>
 *   <li>验证逻辑：{@link ValidateCodeService#checkCaptcha(String, String)} 失败抛 {@link CaptchaException}</li>
 *   <li>任何 {@link Exception} 都被 {@code catch (Exception e)} 捕获，写入 response body</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ValidateCodeFilterTest {

    @Mock
    private ValidateCodeService validateCodeService;

    private ValidateCodeFilter filter;
    private CaptchaProperties captchaProperties;

    @BeforeEach
    void setUp() {
        filter = new ValidateCodeFilter();
        captchaProperties = new CaptchaProperties();
        ReflectionTestUtils.setField(filter, "validateCodeService", validateCodeService);
        ReflectionTestUtils.setField(filter, "captchaProperties", captchaProperties);
    }

    // ==================== 1. 非登录/注册 URL ====================

    @Test
    @DisplayName("URL=/opc/finance/voucher（非 login/register）→ 跳过校验，chain.filter 透传")
    void nonLoginUrl_skipsValidation() {
        captchaProperties.setEnabled(true);  // 即使开启验证码，非 login URL 也跳过
        GatewayFilter filterLambda = filter.apply(new Object());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"foo\":\"bar\"}"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        verifyNoInteractions(validateCodeService);  // ← 关键：未调 checkCaptcha
    }

    @Test
    @DisplayName("URL=/auth/logout（大小写敏感，匹配 VALIDATE_URL 不含）→ 跳过")
    void logoutUrl_skipsValidation() {
        captchaProperties.setEnabled(true);
        GatewayFilter filterLambda = filter.apply(new Object());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/logout"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        verifyNoInteractions(validateCodeService);
    }

    // ==================== 2. 验证码关闭 ====================

    @Test
    @DisplayName("captchaProperties.enabled=false → 即使是 login URL 也跳过")
    void captchaDisabled_skipsValidation() {
        captchaProperties.setEnabled(false);
        GatewayFilter filterLambda = filter.apply(new Object());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"username\":\"a\",\"password\":\"b\"}"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        verifyNoInteractions(validateCodeService);
    }

    // ==================== 3. 合法验证码 ====================

    @Test
    @DisplayName("login URL + 验证码开 + 合法 code + uuid → checkCaptcha 被调 + chain.filter 通过")
    void validCaptcha_passesThrough() throws CaptchaException {
        captchaProperties.setEnabled(true);
        GatewayFilter filterLambda = filter.apply(new Object());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"1234\",\"uuid\":\"abc\"}"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        verify(validateCodeService).checkCaptcha("1234", "abc");
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("register URL + 验证码开 + 合法 code + uuid → 同样通过")
    void registerUrl_alsoValidates() throws CaptchaException {
        captchaProperties.setEnabled(true);
        GatewayFilter filterLambda = filter.apply(new Object());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"5678\",\"uuid\":\"def\"}"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        verify(validateCodeService).checkCaptcha("5678", "def");
        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== 4. 验证码错误 ====================

    @Test
    @DisplayName("checkCaptcha 抛 CaptchaException → HTTP 200 + body 含异常消息")
    void invalidCaptcha_returnsErrorMessage() throws CaptchaException {
        captchaProperties.setEnabled(true);
        doThrow(new CaptchaException("验证码错误"))
                .when(validateCodeService).checkCaptcha(anyString(), anyString());
        GatewayFilter filterLambda = filter.apply(new Object());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"wrong\",\"uuid\":\"abc\"}"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        assertTrue(responseBody(exchange).contains("验证码错误"));
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    // ==================== 5. body 非 JSON ====================

    @Test
    @DisplayName("body 不是 JSON（JSON.parseObject 抛异常）→ 200 + body 含异常消息")
    void nonJsonBody_returnsErrorMessage() throws CaptchaException {
        captchaProperties.setEnabled(true);
        GatewayFilter filterLambda = filter.apply(new Object());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("not-json"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        String body = responseBody(exchange);
        assertNotNull(body, "异常时应写 response body");
        // JSON parse 异常消息内容不一定固定，只验证非空 + 没通过 chain
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verifyNoInteractions(validateCodeService);  // 解析失败前不会调 checkCaptcha
    }

    // ==================== 测试辅助 ====================

    private GatewayFilterChain chainMock() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        return chain;
    }

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