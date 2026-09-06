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
        assertTrue(ex.getMessage().contains("内置默认密码"));
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