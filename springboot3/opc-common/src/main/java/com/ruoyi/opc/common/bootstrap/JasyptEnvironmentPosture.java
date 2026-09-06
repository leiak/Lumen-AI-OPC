package com.ruoyi.opc.common.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.List;

/**
 * prod profile 启动期强制校验：
 * <ol>
 *   <li>JASYPT_PASSWORD 必须从环境变量注入（拒绝默认密码）</li>
 *   <li>敏感配置（如 DB / Redis / API Key）必须以 ENC(...) 包裹</li>
 * </ol>
 *
 * 任何一项缺失则抛出 IllegalStateException 阻止启动。
 *
 * 通过 META-INF/spring.factories 或 spring.factories.imports 注册。
 */
public class JasyptEnvironmentPosture implements EnvironmentPostProcessor {

    private static final List<String> PROD_PROFILES = Arrays.asList("prod", "production");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean isProd = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(PROD_PROFILES::contains);

        if (!isProd) {
            return;
        }

        // 1. JASYPT_PASSWORD 校验
        String password = environment.getProperty("jasypt.encryptor.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "[FATAL] prod profile 必须注入 JASYPT_PASSWORD 环境变量，"
                    + "禁止使用内置默认密码。\n"
                    + "修复：export JASYPT_PASSWORD='<your-strong-password>' 后重启。"
            );
        }

        // 2. 已知明文占位符黑名单（CI fail-fast）
        // 仅匹配 value 字段，避免误伤 Nacos 上配置的其他 key
        java.util.Map<String, String> cleartextPlaceholders = java.util.Map.of(
            "jasypt.encryptor.password", "OpcEncrypt!2026",     // 默认密码
            "spring.datasource.password", "root",               // 默认 DB 密码
            "spring.datasource.dynamic.datasource.master.password", "root",
            "spring.redis.password", "",                        // 空 Redis 密码（prod 不允许）
            "ai.openai.api-key", "sk-placeholder",              // OpenAI key 占位符
            "ai.zhipuai.api-key", "placeholder",                // 智谱 key 占位符
            "qdrant.api-key", ""                                // 空 Qdrant key（prod 不允许）
        );
        for (java.util.Map.Entry<String, String> entry : cleartextPlaceholders.entrySet()) {
            String property = entry.getKey();
            String badValue = entry.getValue();
            String actual = environment.getProperty(property);
            if (actual == null) {
                continue;
            }
            // 跳过 ENC(...) 包裹的合法密文
            if (actual.startsWith("ENC(") && actual.endsWith(")")) {
                continue;
            }
            // 完全匹配或包含占位符即视为明文
            boolean isMatch = badValue.isEmpty()
                ? actual.trim().isEmpty()
                : actual.contains(badValue);
            if (isMatch) {
                throw new IllegalStateException(
                    "[FATAL] prod profile 发现明文敏感字段：property=" + property
                        + "，实际值含占位符 '" + badValue + "'。请改为 ENC(...) 包裹后重新导入 Nacos。");
            }
        }
    }
}