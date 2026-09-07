package com.ruoyi.gateway.filter;

import com.ruoyi.gateway.config.properties.XssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link XssFilter} 单元测试 — W10.1.3（网关剩余 filter 单测）
 *
 * <p>覆盖 7 个分支：
 * <ul>
 *   <li><b>1 xss.enabled=false</b> → chain.filter 透传</li>
 *   <li><b>2 GET 方法</b> → chain.filter 透传（只过滤 POST/PUT 等带 body 的）</li>
 *   <li><b>3 DELETE 方法</b> → chain.filter 透传</li>
 *   <li><b>4 非 JSON Content-Type</b>（如 application/x-www-form-urlencoded）→ chain.filter 透传</li>
 *   <li><b>5 排除 URL</b>（{@code xss.excludeUrls} 匹配）→ chain.filter 透传</li>
 *   <li><b>6 POST + JSON + 含 {@code <script>}</b> → body 被 HTML 过滤 + content-length 移除 + transfer-encoding=chunked</li>
 *   <li><b>7 POST + JSON + 无害 body</b> → body 不变（HTMLFilter 是无操作）</li>
 * </ul>
 *
 * <p>关键设计点：
 * <ul>
 *   <li>{@link XssFilter} 标注 {@code @ConditionalOnProperty(value = "security.xss.enabled", havingValue = "true")} —
 *       测试代码需手动 {@link ReflectionTestUtils#setField} 注入 {@link XssProperties}</li>
 *   <li>{@link XssFilter#getOrder()} 返回 {@code -100}（晚于 {@link AuthFilter} 的 {@code -200}，早于业务 filter）</li>
 *   <li>{@code EscapeUtil.clean} 用 {@code HTMLFilter}（来自 jsoup-like 自定义实现）清除 HTML 标签，保留文本</li>
 *   <li>Content-Length 必须移除（body 被改写后长度对不上）→ 改用 {@code Transfer-Encoding: chunked}</li>
 * </ul>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class XssFilterTest {

    private XssFilter filter;
    private XssProperties xssProperties;

    @BeforeEach
    void setUp() {
        filter = new XssFilter();
        xssProperties = new XssProperties();
        ReflectionTestUtils.setField(filter, "xss", xssProperties);
    }

    // ==================== 1. xss.enabled=false ====================

    @Test
    @DisplayName("xss.enabled=false → chain.filter 透传（不检查 method/content-type）")
    void xssDisabled_alwaysPasses() {
        xssProperties.setEnabled(false);
        // 故意用 POST + JSON + 含 <script> — 但因 disabled，不应过滤
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"summary\":\"<script>alert(1)</script>\"}"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        // body 没被改写（chain.filter 收到原始 exchange，不带 decorator）
        assertNull(chain.captured.getRequest().getHeaders().getFirst(HttpHeaders.TRANSFER_ENCODING),
                "xss 关闭时不应设置 Transfer-Encoding");
    }

    // ==================== 2. GET 方法 ====================

    @Test
    @DisplayName("GET 请求 → chain.filter 透传（GET 不过滤 body）")
    void getRequest_skipsXssFilter() {
        xssProperties.setEnabled(true);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/opc/finance/voucher"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== 3. DELETE 方法 ====================

    @Test
    @DisplayName("DELETE 请求 → chain.filter 透传（DELETE 不过滤 body）")
    void deleteRequest_skipsXssFilter() {
        xssProperties.setEnabled(true);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/opc/finance/voucher/1"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== 4. 非 JSON Content-Type ====================

    @Test
    @DisplayName("Content-Type=application/x-www-form-urlencoded → chain.filter 透传")
    void nonJsonContentType_skipsXssFilter() {
        xssProperties.setEnabled(true);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("summary=test"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Content-Type 完全缺失 → chain.filter 透传（isJsonRequest 返回 false）")
    void noContentType_skipsXssFilter() {
        xssProperties.setEnabled(true);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/finance/voucher")
                        .body("raw text"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== 5. 排除 URL ====================

    @Test
    @DisplayName("URL 命中 excludeUrls → chain.filter 透传（不应用 xss）")
    void excludedUrl_skipsXssFilter() {
        xssProperties.setEnabled(true);
        xssProperties.setExcludeUrls(List.of("/opc/upload/**", "/opc/raw/**"));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/upload/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"<img onerror=alert(1) src=x>\"}"));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== 6. POST + JSON + 含 <script> ====================

    @Test
    @DisplayName("POST + JSON + 含 <script> → body 被 HTML 过滤 + Content-Length 移除 + Transfer-Encoding=chunked")
    void postJsonWithScript_bodyCleanedAndHeadersRewritten() {
        xssProperties.setEnabled(true);
        String originalBody = "{\"summary\":\"<script>alert('xss')</script>\"}";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(originalBody));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // chain.filter 收到的 exchange 应是被 decorator mutate 过的
        verify(chain).filter(any(ServerWebExchange.class));

        // 验证 mutated request 的 body 被清理（读 body 验证 HTML 标签被剥除）
        ServerWebExchange mutated = chain.captured;
        String cleanedBody = readBody(mutated.getRequest().getBody());

        // HTMLFilter 应该把 <script>...</script> 剥掉，保留 alert('xss')
        assertFalse(cleanedBody.contains("<script>"),
                "<script> 标签必须被剥除，实际 body: " + cleanedBody);
        assertFalse(cleanedBody.contains("</script>"),
                "</script> 闭合标签必须被剥除");
        assertTrue(cleanedBody.contains("alert"),
                "标签内的文本应保留（HTMLFilter 不删内容，只剥标签）");

        // 验证 mutated request headers：Content-Length 移除 + Transfer-Encoding=chunked
        HttpHeaders headers = mutated.getRequest().getHeaders();
        assertNull(headers.getFirst(HttpHeaders.CONTENT_LENGTH),
                "Content-Length 必须移除（body 长度变化）");
        assertEquals("chunked", headers.getFirst(HttpHeaders.TRANSFER_ENCODING));
    }

    @Test
    @DisplayName("POST + JSON + 含 <img onerror> → onerror 属性被剥除")
    void postJsonWithImgOnerror_attributeStripped() {
        xssProperties.setEnabled(true);
        String originalBody = "{\"summary\":\"<img src=x onerror=alert(1)>\"}";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(originalBody));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String cleanedBody = readBody(chain.captured.getRequest().getBody());
        assertFalse(cleanedBody.contains("onerror"),
                "onerror 属性必须被剥除（XSS 攻击向量），实际 body: " + cleanedBody);
        assertFalse(cleanedBody.contains("<img"),
                "<img> 标签本身也被剥除");
    }

    // ==================== 7. POST + JSON + 无害 body ====================

    @Test
    @DisplayName("POST + JSON + 无害 body → body 不变（HTMLFilter 是 no-op）")
    void postJsonCleanBody_unchanged() {
        xssProperties.setEnabled(true);
        String originalBody = "{\"summary\":\"正常文本，无 XSS\"}";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(originalBody));
        GatewayFilterChain chain = chainMock();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String cleanedBody = readBody(chain.captured.getRequest().getBody());
        assertEquals(originalBody, cleanedBody,
                "无害 body 不应被改写（除了 decorator mutate 行为）");
    }

    // ==================== 8. getOrder 契约 ====================

    @Test
    @DisplayName("getOrder() 返回 -100（晚于 AuthFilter 的 -200，先于业务 filter）")
    void orderIsMinusHundred() {
        assertEquals(-100, filter.getOrder());
    }

    // ==================== 测试辅助 ====================

    /** 用 captured 字段捕获 chain.filter 收到的 exchange */
    private GatewayFilterChain chainMock() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            chain.captured = inv.getArgument(0);
            return Mono.empty();
        });
        return chain;
    }

    private String readBody(Flux<DataBuffer> body) {
        return DataBufferUtils.join(body)
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .block();
    }
}