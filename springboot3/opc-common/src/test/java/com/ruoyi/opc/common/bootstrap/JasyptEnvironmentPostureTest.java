package com.ruoyi.opc.common.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JasyptEnvironmentPostureTest {

    private final JasyptEnvironmentPosture processor = new JasyptEnvironmentPosture();

    @Test
    void dev_profile_skips_strict_check() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("jasypt.encryptor.password", "OpcEncrypt!2026");
        env.setProperty("spring.datasource.password", "root");
        assertDoesNotThrow(() -> processor.postProcessEnvironment(env, null));
    }

    @Test
    void prod_profile_without_jasypt_password_fails() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> processor.postProcessEnvironment(env, null));
        assertTrue(ex.getMessage().contains("JASYPT_PASSWORD"));
    }

    @Test
    void prod_profile_with_default_password_fails() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("jasypt.encryptor.password", "OpcEncrypt!2026");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> processor.postProcessEnvironment(env, null));
        // "OpcEncrypt!2026" 非空，所以第 1 步（JASYPT_PASSWORD 必须注入）是通过的；
        // 真正拦下它的是第 2 步的 cleartextPlaceholders 黑名单，报的是「明文敏感字段」。
        // 「内置默认密码」那句只出现在第 1 步的报错里 —— 原断言认错了分支，
        // 于是永远对不上。该测试此前从未执行过。
        assertTrue(ex.getMessage().contains("明文敏感字段"), ex.getMessage());
        assertTrue(ex.getMessage().contains("jasypt.encryptor.password"),
            "报错应指名是哪个属性触发的：" + ex.getMessage());
    }

    @Test
    void prod_profile_with_cleartext_db_password_fails() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("jasypt.encryptor.password", "StrongProdPwd!@#$");
        env.setProperty("spring.datasource.password", "root");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> processor.postProcessEnvironment(env, null));
        assertTrue(ex.getMessage().contains("明文敏感字段"));
    }

    @Test
    void prod_profile_all_clear_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("jasypt.encryptor.password", "StrongProdPwd!@#$");
        env.setProperty("spring.datasource.password", "ENC(abc123)");
        assertDoesNotThrow(() -> processor.postProcessEnvironment(env, null));
    }
}