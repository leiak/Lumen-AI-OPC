package com.ruoyi.opc.common.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * prod profile 启动期对 Nacos 做最终 ping 校验，确保应用只在 Nacos 可达时才对外提供服务。
 *
 * <p>运行顺序：{@link Ordered#HIGHEST_PRECEDENCE} + 1，先于所有业务 Runner。
 *
 * <p>触发条件：
 * <ul>
 *   <li>profile 包含 prod</li>
 *   <li>{@code spring.cloud.nacos.discovery.enabled=true}（默认）</li>
 *   <li>未设置 {@code opc.nacos.startup-check.enabled=false}（默认 true）</li>
 * </ul>
 *
 * <p>失败行为：抛 {@link IllegalStateException} 让 Spring Boot 退出（非 0 退出码）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "spring.cloud.nacos.discovery.enabled", havingValue = "true", matchIfMissing = true)
public class OpcNacosStartupChecker implements ApplicationRunner, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(OpcNacosStartupChecker.class);
    private static final List<String> PROD_PROFILES = Arrays.asList("prod", "production");
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_RETRIES = 3;

    private Environment environment;
    private boolean prodProfile;

    @Value("${opc.nacos.startup-check.enabled:true}")
    private boolean enabled;

    @Value("${spring.cloud.nacos.discovery.server-addr:}")
    private String discoveryAddr;

    @Value("${spring.cloud.nacos.config.server-addr:}")
    private String configAddr;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.prodProfile = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(PROD_PROFILES::contains);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || !prodProfile) {
            return;
        }

        log.info("[OPC-NACOS-CHECK] prod profile 检测到，开始对 Nacos 做最终 ping 校验...");
        String fallbackTarget = configAddr.isBlank() ? discoveryAddr : configAddr;
        if (fallbackTarget.isBlank()) {
            throw new IllegalStateException(
                "[FATAL] prod profile 未配置 spring.cloud.nacos.config.server-addr / discovery.server-addr，"
                    + "无法启动。检查 bootstrap.yml。");
        }

        // 同时校验 config 与 discovery 两个地址（如不同）
        String configEndpoint = buildHealthUrl(configAddr.isBlank() ? fallbackTarget : configAddr);
        checkWithRetries(configEndpoint, "config");
        if (!discoveryAddr.isBlank() && !discoveryAddr.equals(configAddr)) {
            String discoveryEndpoint = buildHealthUrl(discoveryAddr);
            checkWithRetries(discoveryEndpoint, "discovery");
        }

        log.info("[OPC-NACOS-CHECK] Nacos 可达，应用继续启动。");
    }

    /**
     * 把 nacos 的 host:port 转成 http://host:port/nacos/v1/cs/health 形式做 HTTP ping。
     * 该端点由 Nacos 服务端暴露，无需鉴权。
     */
    private String buildHealthUrl(String addr) {
        if (!addr.startsWith("http://") && !addr.startsWith("https://")) {
            addr = "http://" + addr;
        }
        return URI.create(addr + "/nacos/v1/cs/health").toString();
    }

    /**
     * 三次重试，每次 3s 超时。
     */
    private void checkWithRetries(String url, String label) {
        java.io.IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code >= 200 && code < 300) {
                    log.info("[OPC-NACOS-CHECK] {} endpoint OK (status={}) url={}", label, code, url);
                    return;
                }
                throw new java.io.IOException("HTTP " + code);
            } catch (java.io.IOException e) {
                lastError = e;
                log.warn("[OPC-NACOS-CHECK] {} endpoint 失败 ({}/{}): {}", label, attempt, MAX_RETRIES, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("[FATAL] Nacos " + label + " 检查被中断", ie);
                }
            }
        }
        throw new IllegalStateException(
            "[FATAL] Nacos " + label + " endpoint 不可达（重试 " + MAX_RETRIES + " 次仍失败）：" + url
                + " 原因：" + (lastError == null ? "unknown" : lastError.getMessage())
                + "\n排查：1) 检查网络 2) 检查 Nacos 服务状态 3) 核对 namespaceId");
    }

    /** 供健康检查/运维探活复用。 */
    public boolean isReachable() {
        if (discoveryAddr.isBlank()) {
            return false;
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(buildHealthUrl(discoveryAddr)).toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 300;
        } catch (IOException e) {
            return false;
        }
    }
}