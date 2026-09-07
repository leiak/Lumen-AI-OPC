package com.ruoyi.gateway.filter;

import com.ruoyi.gateway.filter.BlackListUrlFilter.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link BlackListUrlFilter} 单元测试 — W10.1.1（网关剩余 filter 单测）
 *
 * <p>覆盖 4 个分支：
 * <ul>
 *   <li><b>1 黑名单匹配</b>：URL 命中黑名单正则 → HTTP 200 + body 含 "请求地址不允许访问"</li>
 *   <li><b>2 黑名单不匹配</b>：URL 不在黑名单 → chain.filter 通过</li>
 *   <li><b>3 空黑名单</b>：blacklistUrl=[] → 直接 chain.filter（短路）</li>
 *   <li><b>4 多条规则</b>：多条正则，URL 命中任意一条即拦截</li>
 * </ul>
 *
 * <p>关键设计点：
 * <ul>
 *   <li>{@link BlackListUrlFilter} 继承 {@code AbstractGatewayFilterFactory<Config>}，单测需先 {@code new BlackListUrlFilter()} + {@code apply(config)} 拿到 lambda</li>
 *   <li>{@link Config#setBlacklistUrl(List)} 内部把 {@code **} 替换成 {@code (.*?)} 正则 + 大小写不敏感</li>
 *   <li>matchBlacklist 用 {@code Pattern.matcher(url).find()}（不是 matches）— 子串匹配即可</li>
 * </ul>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class BlackListUrlFilterTest {

    private BlackListUrlFilter filter;
    private Config config;

    @BeforeEach
    void setUp() {
        filter = new BlackListUrlFilter();
        config = new Config();
    }

    // ==================== 1. 黑名单匹配 ====================

    @Test
    @DisplayName("URL 命中 /admin/** 黑名单 → HTTP 200 + body 含 '请求地址不允许访问'")
    void blacklistMatch_returns403WithRefuseMessage() {
        config.setBlacklistUrl(List.of("/admin/**"));
        GatewayFilter filterLambda = filter.apply(config);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/user/list"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertTrue(responseBody(exchange).contains("请求地址不允许访问"));
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("URL 命中 /**/internal/** → 嵌套路径也被拦截")
    void blacklistWildcardMiddleWildcard_matches() {
        config.setBlacklistUrl(List.of("/**/internal/**"));
        GatewayFilter filterLambda = filter.apply(config);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/internal/config"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        assertTrue(responseBody(exchange).contains("请求地址不允许访问"));
    }

    // ==================== 2. 黑名单不匹配 ====================

    @Test
    @DisplayName("URL 不在黑名单 → chain.filter 正常通过")
    void blacklistNotMatch_passesThroughChain() {
        config.setBlacklistUrl(List.of("/admin/**"));
        GatewayFilter filterLambda = filter.apply(config);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/finance/voucher"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertNull(exchange.getResponse().getStatusCode(),
                "未命中黑名单 → response 不应被设置状态码（chain.filter 接管）");
    }

    // ==================== 3. 空黑名单 ====================

    @Test
    @DisplayName("黑名单为空 → matchBlacklist 短路返回 false → chain.filter")
    void emptyBlacklist_alwaysPasses() {
        // blacklistUrl = []（默认）
        GatewayFilter filterLambda = filter.apply(config);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/user/list"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        // 验证 matchBlacklist 内部短路
        assertFalse(config.matchBlacklist("/admin/foo"));
        assertFalse(config.matchBlacklist("/anything"));
    }

    // ==================== 4. 多条规则 ====================

    @Test
    @DisplayName("多条规则 — URL 命中任意一条即拦截")
    void multipleRules_anyMatchBlocks() {
        config.setBlacklistUrl(List.of("/admin/**", "/internal/**", "/debug/**"));
        GatewayFilter filterLambda = filter.apply(config);

        // 命中第二条
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/dump"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        assertTrue(responseBody(exchange).contains("请求地址不允许访问"));
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    // ==================== 5. 大小写不敏感 ====================

    @Test
    @DisplayName("黑名单匹配大小写不敏感（Pattern.CASE_INSENSITIVE）")
    void blacklistCaseInsensitive() {
        config.setBlacklistUrl(List.of("/Admin/**"));
        GatewayFilter filterLambda = filter.apply(config);

        // 小写 /admin/foo 也命中
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/foo"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filterLambda.filter(exchange, chain)).verifyComplete();

        assertTrue(responseBody(exchange).contains("请求地址不允许访问"));
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