package com.ruoyi.opc.common.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcNacosStartupCheckerTest {

    private MockEnvironment env;
    private OpcNacosStartupChecker checker;

    @BeforeEach
    void setUp() {
        env = new MockEnvironment();
        checker = new OpcNacosStartupChecker();
    }

    @AfterEach
    void tearDown() {
        // 关闭任何占用端口
    }

    @Test
    void non_prod_profile_skips_check() {
        env.setActiveProfiles("dev");
        checker.setEnvironment(env);
        // 配置未设置也不应抛错（非 prod 直接跳过）
        assertDoesNotThrow(() -> checker.run(() -> java.util.Collections.emptySet()));
    }

    @Test
    void prod_profile_with_unreachable_nacos_fails() throws Exception {
        int unused = findFreePort();
        env.setActiveProfiles("prod");
        env.setProperty("spring.cloud.nacos.discovery.server-addr", "127.0.0.1:" + unused);
        env.setProperty("spring.cloud.nacos.config.server-addr", "127.0.0.1:" + unused);
        checker.setEnvironment(env);
        // 反射把 @Value 字段注进去
        setField(checker, "discoveryAddr", "127.0.0.1:" + unused);
        setField(checker, "configAddr", "127.0.0.1:" + unused);
        setField(checker, "enabled", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> checker.run(() -> java.util.Collections.emptySet()));
        assertTrue(ex.getMessage().contains("[FATAL]"));
    }

    @Test
    void prod_profile_with_empty_addr_fails() {
        env.setActiveProfiles("prod");
        checker.setEnvironment(env);
        setField(checker, "discoveryAddr", "");
        setField(checker, "configAddr", "");
        setField(checker, "enabled", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> checker.run(() -> java.util.Collections.emptySet()));
        assertTrue(ex.getMessage().contains("未配置"));
    }

    @Test
    void reachable_returns_true_when_endpoint_2xx() throws Exception {
        // 启动一个返回 200 的最小 HTTP server
        try (var server = new com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0)) {
            server.createContext("/nacos/v1/cs/health", exchange -> {
                byte[] body = "UP".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            int port = server.getAddress().getPort();
            setField(checker, "discoveryAddr", "127.0.0.1:" + port);
            assertTrue(checker.isReachable());
        }
    }

    @Test
    void reachable_returns_false_on_io_error() throws Exception {
        int unused = findFreePort();
        setField(checker, "discoveryAddr", "127.0.0.1:" + unused);
        assertFalse(checker.isReachable());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}