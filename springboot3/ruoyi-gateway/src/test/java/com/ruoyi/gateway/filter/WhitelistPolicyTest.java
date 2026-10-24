package com.ruoyi.gateway.filter;

import com.ruoyi.gateway.config.properties.IgnoreWhiteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 白名单**策略**测试 —— 拿真实下发的白名单配置跑, 不自己造配置。
 *
 * <p>{@link AuthFilterTest} 测的是 filter 的逻辑 (方法限定怎么解析)。
 * 本测试测的是另一件事: <b>仓库里那两份白名单配置本身</b>是不是真的把
 * 匿名写操作挡住了。
 *
 * <p>为什么必须单独测: 白名单是配置, 不是代码。逻辑改对了, 配置写错了
 * 一样会漏 —— 而配置正是这次收紧的全部内容。2026-10-23 实测过: 匿名
 * {@code DELETE /opc/erp/product/{id}} 穿过网关直达 service, 只因 id 不存在
 * 才被业务层拦下。
 *
 * <p>被测的两份配置 (必须一致, 见 {@code deploy/nacos/apply-whitelist.sh}
 * 的超集检查):
 * <ul>
 *   <li>{@code ruoyi-gateway/src/main/resources/application.yml} —— jar 内 fallback</li>
 *   <li>{@code deploy/nacos/whitelist-opc-invite.yml} —— 推 Nacos 的规范片段</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class WhitelistPolicyTest {

    private AuthFilter filter;
    private IgnoreWhiteProperties ignoreWhite;

    @Mock
    private com.ruoyi.common.redis.service.RedisService redisService;

    @BeforeEach
    void setUp() {
        filter = new AuthFilter();
        ignoreWhite = new IgnoreWhiteProperties();
        ignoreWhite.setWhites(jarFallbackWhites());
        ReflectionTestUtils.setField(filter, "ignoreWhite", ignoreWhite);
        ReflectionTestUtils.setField(filter, "redisService", redisService);
    }

    // ==================== 1. 两份白名单必须一致 ====================

    @Test
    @DisplayName("jar 内 fallback 与 Nacos 规范片段的白名单条目完全一致 (改一处必须改两处)")
    void jarFallbackAndNacosSnippet_areIdentical() {
        List<String> jar = jarFallbackWhites();
        List<String> snippet = nacosSnippetWhites();
        assertFalse(snippet.isEmpty(), "读不到 deploy/nacos/whitelist-opc-invite.yml");
        assertEquals(jar, snippet,
                "两份白名单不一致 —— Nacos 优先级高于 jar 内 application.yml, "
                        + "不一致 = 线上行为取决于哪份生效, 不可预期");
    }

    // ==================== 2. 公开读端点仍然匿名可达 ====================

    @Test
    @DisplayName("公开读端点仍然匿名可达 (收紧不能把公网入口一起关掉)")
    void publicReads_remainAnonymous() {
        for (String path : List.of(
                "/opc/erp/product/list",
                "/opc/erp/product/123",
                "/opc/erp/inventory/low-stock",
                "/opc/content/script/list",
                "/opc/content/script/dashboard",
                "/opc/content/script/5",
                "/opc/content/platform-account/list",
                "/opc/hr/dashboard",
                "/opc/hr/job/list",
                "/opc/hr/job/5",
                "/opc/community/module/list",
                "/opc/community/module/5",
                "/opc/community/comment",
                "/opc/community/rating",
                "/opc/marketplace/category/tree",
                "/opc/marketplace/category/list",
                "/opc/marketplace/category/7",
                "/opc/marketplace/tag/list",
                "/opc/marketplace/publication/list",
                "/opc/marketplace/publication/search",
                "/opc/marketplace/publication/123",
                "/opc/marketplace/review/list/123",
                "/opc/marketplace/author/list",
                "/opc/marketplace/author/9",
                "/opc/user/invitations/ABC234XY",
                "/auth/login"))
        {
            assertAnonymousAllowed(HttpMethod.GET, path,
                    "公开读端点不该被收紧掉: " + path);
        }
    }

    @Test
    @DisplayName("POST /opc/user/invitations/accept 仍然匿名可达 (有意公开的写端点, 全表唯一)")
    void invitationAccept_remainsAnonymous() {
        assertAnonymousAllowed(HttpMethod.POST, "/opc/user/invitations/accept");
    }

    // ==================== 3. 匿名写端点必须被挡住 ====================

    @Test
    @DisplayName("匿名写端点全部被挡 (收紧前这 23 个对匿名开放)")
    void anonymousWrites_areBlocked() {
        // 这些端点 2026-10-23 实测能穿过网关直达 service。
        // 覆盖 ERP / Content / HR / Community / Marketplace / 邀请 六个前缀。
        List<String[]> writes = List.of(
                // ERP product: 创建 / 改 / 删 / 自动分类
                new String[]{"POST", "/opc/erp/product"},
                new String[]{"PUT", "/opc/erp/product/900103"},
                new String[]{"DELETE", "/opc/erp/product/900103"},
                new String[]{"POST", "/opc/erp/product/auto-category"},
                // Content script: 创建 / 改 / 删 / 重生成 / 精修 / 就绪 / 改编
                new String[]{"POST", "/opc/content/script"},
                new String[]{"PUT", "/opc/content/script/3"},
                new String[]{"DELETE", "/opc/content/script/3"},
                new String[]{"POST", "/opc/content/script/3/generate"},
                new String[]{"POST", "/opc/content/script/3/refine"},
                new String[]{"POST", "/opc/content/script/3/ready"},
                new String[]{"POST", "/opc/content/script/adapt"},
                new String[]{"DELETE", "/opc/content/platform-account/5"},
                // HR job: 创建 / 改 / 删 / 上线 / 关闭 / 暂停 / LLM 生成
                new String[]{"POST", "/opc/hr/job"},
                new String[]{"PUT", "/opc/hr/job/5"},
                new String[]{"DELETE", "/opc/hr/job/5"},
                new String[]{"POST", "/opc/hr/job/5/publish"},
                new String[]{"POST", "/opc/hr/job/5/close"},
                new String[]{"POST", "/opc/hr/job/5/pause"},
                new String[]{"POST", "/opc/hr/job/generate-llm"},
                // Community: 建模块 / 装模块 / 评论 / 评分 / 刷新评分
                new String[]{"POST", "/opc/community/module"},
                new String[]{"POST", "/opc/community/module/5/install"},
                new String[]{"POST", "/opc/community/comment"},
                new String[]{"POST", "/opc/community/rating"},
                new String[]{"POST", "/opc/community/rating/5/refresh"},
                // Marketplace: 建商品 / 改商品 / 评价 / 安装
                new String[]{"POST", "/opc/marketplace/publication"},
                new String[]{"PUT", "/opc/marketplace/publication/123"},
                new String[]{"POST", "/opc/marketplace/review"},
                new String[]{"PUT", "/opc/marketplace/review/5"},
                new String[]{"DELETE", "/opc/marketplace/review/5"},
                new String[]{"POST", "/opc/marketplace/install/1"},
                // 邀请: generate 必须登录 (W48.5)
                new String[]{"POST", "/opc/user/invitations/generate"});

        assertTrue(writes.size() >= 23,
                "本测试的写端点清单不该少于 23 条 (收紧前实测的匿名可写端点数), 现在: " + writes.size());

        for (String[] w : writes) {
            assertAnonymousBlocked(HttpMethod.valueOf(w[0]), w[1]);
        }
    }

    // ==================== 4. 语义边界 ====================

    @Test
    @DisplayName("GET:/ 前缀不会误伤同路径的其它 method, POST:/ 前缀同理")
    void methodScopedEntries_doNotBleedIntoOtherMethods() {
        // 同一条白名单, 换 method 就该放行 —— 说明前缀是按 method 精确判定的,
        // 而不是"这条路径整体放行"。
        assertAnonymousAllowed(HttpMethod.GET, "/opc/erp/product/list");
        assertAnonymousBlocked(HttpMethod.POST, "/opc/erp/product/list");
        assertAnonymousBlocked(HttpMethod.DELETE, "/opc/erp/product/list");

        assertAnonymousAllowed(HttpMethod.POST, "/opc/user/invitations/accept");
        assertAnonymousBlocked(HttpMethod.GET, "/opc/user/invitations/accept");
    }

    @Test
    @DisplayName("marketplace 的 {id:[0-9]+} 正则仍然生效: 数字 id 放行, 'me' 不放行")
    void numericIdRegex_stillApplies() {
        assertAnonymousAllowed(HttpMethod.GET, "/opc/marketplace/publication/123");
        assertAnonymousAllowed(HttpMethod.GET, "/opc/marketplace/category/7");
        // 'me' 不是数字 → 不匹配 → 需登录
        assertAnonymousBlocked(HttpMethod.GET, "/opc/marketplace/author/me");
        assertAnonymousBlocked(HttpMethod.GET, "/opc/marketplace/publication/my/list");
    }

    @Test
    @DisplayName("邀请码正则条目: 8 位 base32 放行, generate 不放行")
    void invitationCodeRegex_stillApplies() {
        assertAnonymousAllowed(HttpMethod.GET, "/opc/user/invitations/ABC234XY");
        // 含 I/L/O/0/1 的不是合法 base32 (会被正则挡掉) → 需登录
        assertAnonymousBlocked(HttpMethod.GET, "/opc/user/invitations/ABC0L1XY");
    }

    // ==================== 测试辅助 ====================

    /**
     * 断言这个匿名请求被**放行** (白名单命中) —— 请求会走到下游 chain。
     */
    private void assertAnonymousAllowed(HttpMethod method, String path) {
        assertAnonymousAllowed(method, path, null);
    }

    /**
     * 断言这个匿名请求被**放行** (白名单命中) —— 请求会走到下游 chain。
     */
    private void assertAnonymousAllowed(HttpMethod method, String path, String why) {
        ServerWebExchange exchange = exchange(method, path);
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        String msg = why != null ? why
                : (method + " " + path + " 应当在白名单里, 不该被网关拦下");
        assertEquals(null, exchange.getResponse().getStatusCode(), msg);
    }

    /**
     * 断言这个匿名请求被**拦下** (白名单没命中) —— 返回 401, 不到达下游。
     *
     * <p>⚠️ RuoYi 的业务错误是 HTTP 200 + body 里带 code, 不是 HTTP 4xx
     * (见 {@link AuthFilterTest#assertUnauthorized}), 所以这里查 body.code。
     */
    private void assertAnonymousBlocked(HttpMethod method, String path) {
        ServerWebExchange exchange = exchange(method, path);
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode(),
                method + " " + path + " 被拦时 HTTP 状态应是 200 (RuoYi 约定), code 在 body 里");
        String body = responseBody(exchange);
        assertTrue(body.contains("\"code\":401"),
                method + " " + path + " 必须被网关挡成 401, 实际响应体: " + body);
    }

    private ServerWebExchange exchange(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
    }

    private GatewayFilterChain chainMock() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        return chain;
    }

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

    /** jar 内 fallback 的白名单 (classpath 上的 application.yml) */
    private List<String> jarFallbackWhites() {
        return readWhites(readResource("/application.yml"));
    }

    /** deploy/nacos/whitelist-opc-invite.yml 的白名单 */
    private List<String> nacosSnippetWhites() {
        Path p = findSnippet();
        assertNotNull(p, "找不到 deploy/nacos/whitelist-opc-invite.yml (从 " + System.getProperty("user.dir") + " 往上找)");
        try {
            return readWhites(Files.readString(p, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("读 snippet 失败: " + p, e);
        }
    }

    /**
     * 从当前目录往上找 deploy/nacos/whitelist-opc-invite.yml。
     * surefire 的工作目录是模块目录 (springboot3/ruoyi-gateway), 但从仓库根
     * 跑 mvn 时是仓库根 —— 两种都支持, 不写死相对路径。
     */
    private Path findSnippet() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path p = dir.resolve("deploy/nacos/whitelist-opc-invite.yml");
            if (Files.isRegularFile(p)) {
                return p;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private String readResource(String name) {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            assertNotNull(in, "classpath 上找不到 " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读 " + name + " 失败", e);
        }
    }

    /**
     * 从 YAML 文本里取出 security.ignore.whites。
     *
     * <p>不引 PyYAML 依赖 (网关测试模块没有), 所以按行取: 缩进 6 空格的
     * {@code - xxx} 且位于 security.ignore.whites 块内。
     */
    private List<String> readWhites(String yaml) {
        List<String> out = new ArrayList<>();
        boolean inWhites = false;
        boolean sawSecurity = false;
        for (String raw : yaml.split("\n")) {
            String line = raw.replace("\r", "");
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!line.startsWith(" ")) {
                // 顶层键
                sawSecurity = trimmed.startsWith("security:");
                inWhites = false;
                continue;
            }
            if (!sawSecurity) {
                continue;
            }
            if (trimmed.equals("whites:")) {
                inWhites = true;
                continue;
            }
            if (trimmed.endsWith(":") && !trimmed.startsWith("- ")) {
                // 兄弟键 (如 whitesRemove) → 退出 whites 块
                inWhites = false;
                continue;
            }
            if (inWhites && trimmed.startsWith("- ")) {
                out.add(unquote(trimmed.substring(2).trim()));
            }
        }
        return out;
    }

    private String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"")
                || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
