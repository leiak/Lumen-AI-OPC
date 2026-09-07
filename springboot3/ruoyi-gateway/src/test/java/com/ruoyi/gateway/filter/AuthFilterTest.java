package com.ruoyi.gateway.filter;

import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.constant.HttpStatus;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.TokenConstants;
import com.ruoyi.common.core.utils.JwtUtils;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.gateway.config.properties.IgnoreWhiteProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link AuthFilter} 单元测试 — W9.2（网关集成测试）
 *
 * <p>覆盖 6 个分支 + 5 个边界：
 * <ul>
 *   <li><b>1 白名单通配</b>：{@code /opc/user/invitations/*} → 不查 token，直接 chain.filter</li>
 *   <li><b>2 双星通配</b>：{@code /opc/**} → 匹配嵌套路径</li>
 *   <li><b>3 不匹配白名单</b>：走 token 校验 → 401（缺 token）</li>
 *   <li><b>4 无 Authorization header</b>：401 + msg="令牌不能为空"</li>
 *   <li><b>5 JWT 解析失败</b>：401 + msg="令牌已过期或验证不正确"</li>
 *   <li><b>6 Redis 登录态过期</b>：401 + msg="登录状态已过期"</li>
 *   <li><b>7 缺 user_id</b>：401 + msg="令牌验证失败"</li>
 *   <li><b>8 缺 username</b>：401 + msg="令牌验证失败"</li>
 *   <li><b>9 合法路径</b>：chain.filter 被调 + user_key/user_id/username 注入 + from-source 剥离</li>
 *   <li><b>10 Bearer 前缀剥离</b>：{@code "Bearer xxx"} → token="xxx"</li>
 *   <li><b>11 无 Bearer 前缀</b>：{@code "xxx"} → 直接透传</li>
 *   <li><b>12 空 token</b>：{@code "Bearer "}（带空格但无 token）→ 401</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@link AuthFilter} 用字段注入（{@code @Autowired}），单测用 {@link ReflectionTestUtils#setField} 注入 mock</li>
 *   <li>{@link JwtUtils} 是静态工具类，用 {@code mockStatic(JwtUtils.class)} + try-with-resources</li>
 *   <li>Reactive 测试用 {@link StepVerifier} 验证 {@code Mono<Void>} 完成</li>
 *   <li>用 {@link MockServerWebExchange} + {@link MockServerHttpRequest} 模拟 WebFlux 请求</li>
 *   <li>用 {@link ArgumentCaptor} 捕获 chain.filter 收到的 mutated exchange，验证 header 注入</li>
 * </ul>
 *
 * <p>本地限制：JDK 8 不能运行 WebFlux（需要 JDK 17），本测试需 CI 跑。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private RedisService redisService;

    private AuthFilter filter;
    private IgnoreWhiteProperties ignoreWhite;

    private static final String TOKEN = "valid.jwt.token";
    private static final String USER_KEY = "user-key-123";
    private static final String USER_ID = "1001";
    private static final String USERNAME = "alice";

    @BeforeEach
    void setUp() {
        filter = new AuthFilter();
        ignoreWhite = new IgnoreWhiteProperties();
        ReflectionTestUtils.setField(filter, "ignoreWhite", ignoreWhite);
        ReflectionTestUtils.setField(filter, "redisService", redisService);
    }

    // ==================== 1. 白名单分支 ====================

    @Test
    @DisplayName("白名单 /opc/user/invitations/* → 直接 chain.filter，不查 token")
    void whitelist_singleWildcard_matchesAndSkipsTokenCheck() {
        ignoreWhite.setWhites(List.of("/opc/user/invitations/*"));
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/user/invitations/ABC234XY", null, null);
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        verifyNoInteractions(redisService);
    }

    @Test
    @DisplayName("白名单 /opc/** 双星通配 → /opc/finance/voucher/123 也通过")
    void whitelist_doubleWildcard_matchesNestedPath() {
        ignoreWhite.setWhites(List.of("/opc/**"));
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher/123", null, null);
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("URL 不匹配白名单 → 走 token 校验分支 → 401（无 token）")
    void nonWhitelistPath_fallsThroughToTokenCheck() {
        ignoreWhite.setWhites(List.of("/opc/user/invitations/*"));
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", null, null);
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(),
                "URL 不在白名单 → 走 token 校验 → 因缺 token 返 401");
        assertTrue(responseBody(exchange).contains("令牌不能为空"));
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    // ==================== 2. 缺 token ====================

    @Test
    @DisplayName("无 Authorization header → HTTP 401 + msg='令牌不能为空'")
    void noToken_returns401WithEmptyTokenMessage() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", null, null);
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(responseBody(exchange).contains("令牌不能为空"));
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verifyNoInteractions(redisService);
    }

    // ==================== 3. JWT 解析失败 ====================

    @Test
    @DisplayName("JwtUtils.parseToken 返回 null → HTTP 401 + msg='令牌已过期或验证不正确'")
    void invalidJwt_returns401WithInvalidTokenMessage() {
        try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
            jwtMock.when(() -> JwtUtils.parseToken("malformed")).thenReturn(null);

            ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", "malformed", null);
            GatewayFilterChain chain = chainMock();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
            assertTrue(responseBody(exchange).contains("令牌已过期或验证不正确"));
            jwtMock.verify(() -> JwtUtils.parseToken("malformed"));
            verifyNoInteractions(redisService);
        }
    }

    // ==================== 4. Redis 登录态过期 ====================

    @Test
    @DisplayName("JWT 合法 + Redis hasKey=false → HTTP 401 + msg='登录状态已过期'")
    void redisMiss_returns401WithLoginExpiredMessage() {
        try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
            Claims claims = mock(Claims.class);
            jwtMock.when(() -> JwtUtils.parseToken(TOKEN)).thenReturn(claims);
            jwtMock.when(() -> JwtUtils.getUserKey(claims)).thenReturn(USER_KEY);
            when(redisService.hasKey(CacheConstants.LOGIN_TOKEN_KEY + USER_KEY)).thenReturn(false);

            ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", TOKEN, null);
            GatewayFilterChain chain = chainMock();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
            assertTrue(responseBody(exchange).contains("登录状态已过期"));
            verify(redisService).hasKey(CacheConstants.LOGIN_TOKEN_KEY + USER_KEY);
            verify(chain, never()).filter(any(ServerWebExchange.class));
        }
    }

    // ==================== 5. 缺 user_id ====================

    @Test
    @DisplayName("JWT 合法 + Redis 命中 + 缺 user_id → HTTP 401 + msg='令牌验证失败'")
    void missingUserId_returns401WithTokenInvalidMessage() {
        try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
            Claims claims = mock(Claims.class);
            jwtMock.when(() -> JwtUtils.parseToken(TOKEN)).thenReturn(claims);
            jwtMock.when(() -> JwtUtils.getUserKey(claims)).thenReturn(USER_KEY);
            jwtMock.when(() -> JwtUtils.getUserId(claims)).thenReturn("");  // 缺 user_id
            when(redisService.hasKey(anyString())).thenReturn(true);

            ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", TOKEN, null);
            GatewayFilterChain chain = chainMock();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
            assertTrue(responseBody(exchange).contains("令牌验证失败"));
            verify(chain, never()).filter(any(ServerWebExchange.class));
        }
    }

    // ==================== 6. 缺 username ====================

    @Test
    @DisplayName("JWT 合法 + Redis 命中 + user_id 存在 + 缺 username → HTTP 401 + msg='令牌验证失败'")
    void missingUsername_returns401WithTokenInvalidMessage() {
        try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
            Claims claims = mock(Claims.class);
            jwtMock.when(() -> JwtUtils.parseToken(TOKEN)).thenReturn(claims);
            jwtMock.when(() -> JwtUtils.getUserKey(claims)).thenReturn(USER_KEY);
            jwtMock.when(() -> JwtUtils.getUserId(claims)).thenReturn(USER_ID);
            jwtMock.when(() -> JwtUtils.getUserName(claims)).thenReturn("");  // 缺 username
            when(redisService.hasKey(anyString())).thenReturn(true);

            ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", TOKEN, null);
            GatewayFilterChain chain = chainMock();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
            assertTrue(responseBody(exchange).contains("令牌验证失败"));
            verify(chain, never()).filter(any(ServerWebExchange.class));
        }
    }

    // ==================== 7. 合法路径 ====================

    @Test
    @DisplayName("合法路径 → chain.filter 被调 + user_key/user_id/username 注入 + from-source 剥离")
    void validPath_injectsHeadersAndStripsFromSource() {
        try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
            Claims claims = mock(Claims.class);
            jwtMock.when(() -> JwtUtils.parseToken(TOKEN)).thenReturn(claims);
            jwtMock.when(() -> JwtUtils.getUserKey(claims)).thenReturn(USER_KEY);
            jwtMock.when(() -> JwtUtils.getUserId(claims)).thenReturn(USER_ID);
            jwtMock.when(() -> JwtUtils.getUserName(claims)).thenReturn(USERNAME);
            when(redisService.hasKey(anyString())).thenReturn(true);

            // 外部请求伪造 from-source: inner（试图绕过内层鉴权）→ AuthFilter 应剥掉
            ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", TOKEN, "inner");
            GatewayFilterChain chain = chainMock();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            ServerWebExchange mutated = captor.getValue();
            HttpHeaders headers = mutated.getRequest().getHeaders();

            // 注入：user_key / user_id / username（addHeader 走 ServletUtils.urlEncode，但 USER_KEY/USER_ID/USERNAME 无需转码字符）
            assertEquals(USER_KEY, headers.getFirst(SecurityConstants.USER_KEY),
                    "user_key 必须从 JWT claims 注入到下游 request header");
            assertEquals(USER_ID, headers.getFirst(SecurityConstants.DETAILS_USER_ID));
            assertEquals(USERNAME, headers.getFirst(SecurityConstants.DETAILS_USERNAME));
            // 剥离：from-source
            assertNull(headers.getFirst(SecurityConstants.FROM_SOURCE),
                    "from-source 必须被剥离 — 即使外部请求伪造 'inner'，gateway 也不允许透传到下游");
            // 原始 Authorization 保留（下游 service 可继续读）
            assertEquals(TOKEN, headers.getFirst(SecurityConstants.AUTHORIZATION_HEADER),
                    "原始 token 保留在 Authorization header（不裁剪 Bearer 前缀再写回 — 设计如此）");
        }
    }

    // ==================== 8. Bearer 前缀剥离 ====================

    @Test
    @DisplayName("Authorization='Bearer xxx' → token 被裁剪为 'xxx' 后传给 JwtUtils.parseToken")
    void bearerPrefix_isStrippedBeforeJwtParse() {
        try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
            Claims claims = mock(Claims.class);
            jwtMock.when(() -> JwtUtils.parseToken(TOKEN)).thenReturn(claims);
            jwtMock.when(() -> JwtUtils.getUserKey(claims)).thenReturn(USER_KEY);
            jwtMock.when(() -> JwtUtils.getUserId(claims)).thenReturn(USER_ID);
            jwtMock.when(() -> JwtUtils.getUserName(claims)).thenReturn(USERNAME);
            when(redisService.hasKey(anyString())).thenReturn(true);

            MockServerHttpRequest.BaseBuilder<?> req = MockServerHttpRequest
                    .get("/opc/finance/voucher")
                    .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + TOKEN);
            ServerWebExchange exchange = MockServerWebExchange.from(req);
            GatewayFilterChain chain = chainMock();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            // 验证 parseToken 收到的是 "valid.jwt.token"（剥离 Bearer 前缀后的值），不是 "Bearer valid.jwt.token"
            jwtMock.verify(() -> JwtUtils.parseToken(TOKEN));
            verify(chain).filter(any(ServerWebExchange.class));
        }
    }

    @Test
    @DisplayName("Authorization='xxx'（无 Bearer 前缀）→ 直接作为 token 透传给 JwtUtils")
    void tokenWithoutBearerPrefix_isUsedAsIs() {
        try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
            Claims claims = mock(Claims.class);
            jwtMock.when(() -> JwtUtils.parseToken(TOKEN)).thenReturn(claims);
            jwtMock.when(() -> JwtUtils.getUserKey(claims)).thenReturn(USER_KEY);
            jwtMock.when(() -> JwtUtils.getUserId(claims)).thenReturn(USER_ID);
            jwtMock.when(() -> JwtUtils.getUserName(claims)).thenReturn(USERNAME);
            when(redisService.hasKey(anyString())).thenReturn(true);

            ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", TOKEN, null);
            GatewayFilterChain chain = chainMock();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            jwtMock.verify(() -> JwtUtils.parseToken(TOKEN));
            verify(chain).filter(any(ServerWebExchange.class));
        }
    }

    @Test
    @DisplayName("Authorization='Bearer '（空 token）→ 401 + msg='令牌不能为空'")
    void bearerWithEmptyToken_returns401() {
        // 仅 "Bearer "（带空格）→ getToken 返回 "" → StringUtils.isEmpty("") → 401
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/finance/voucher")
                        .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(responseBody(exchange).contains("令牌不能为空"));
    }

    // ==================== 测试辅助 ====================

    private ServerWebExchange exchange(HttpMethod method, String path, String token, String fromSource) {
        MockServerHttpRequest.BaseBuilder<?> req = MockServerHttpRequest.method(method, path);
        if (token != null) {
            req.header(SecurityConstants.AUTHORIZATION_HEADER, token);
        }
        if (fromSource != null) {
            req.header(SecurityConstants.FROM_SOURCE, fromSource);
        }
        return MockServerWebExchange.from(req);
    }

    private GatewayFilterChain chainMock() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        return chain;
    }

    /**
     * 让 Mono 同步执行（webFluxResponseWriter 内部 writeWith 已直接 bufferFactory().wrap()，不需 subscribe），
     * 然后从 exchange 的 response body 读出字符串。
     */
    private String responseBody(MockServerWebExchange exchange) {
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