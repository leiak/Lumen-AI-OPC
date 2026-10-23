package com.ruoyi.opc.common.crypto;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JasyptCipherToolTest {

    private static final String PASSWORD = "OpcEncrypt!2026";
    private static final String PLAINTEXT = "my-secret-db-password";

    @Test
    void encrypt_then_decrypt_roundtrip() {
        StandardPBEStringEncryptor enc = JasyptCipherTool.buildEncryptor(PASSWORD);
        String cipher = enc.encrypt(PLAINTEXT);

        assertNotEquals(PLAINTEXT, cipher, "密文必须不等于明文");
        assertTrue(cipher.length() > 16, "密文长度合理");

        String decrypted = enc.decrypt(cipher);
        assertEquals(PLAINTEXT, decrypted);
    }

    @Test
    void stripEnc_removes_wrapper() {
        String raw = JasyptCipherTool.buildEncryptor(PASSWORD).encrypt(PLAINTEXT);
        String wrapped = "ENC(" + raw + ")";

        assertEquals(raw, JasyptCipherTool.stripEnc(wrapped));
        assertEquals(raw, JasyptCipherTool.stripEnc(raw));
        assertEquals("", JasyptCipherTool.stripEnc(null));
    }

    @Test
    void different_password_produces_different_cipher() {
        StandardPBEStringEncryptor a = JasyptCipherTool.buildEncryptor(PASSWORD);
        StandardPBEStringEncryptor b = JasyptCipherTool.buildEncryptor("other-password");
        String ca = a.encrypt(PLAINTEXT);
        String cb = b.encrypt(PLAINTEXT);

        // 要比的是**密文**，不是解密结果。两个密文用各自的密码解回来本来就该是
        // 同一段明文 —— 原断言写成 assertNotEquals(a.decrypt(ca), b.decrypt(cb))，
        // 等于断言「同一段明文解密后不相等」，逻辑是反的。该测试此前从未执行过
        // （opc-common 的测试被编译不过的 OpcNacosStartupCheckerTest 整体挡住），
        // 所以这个反向断言一直没暴露。
        assertNotEquals(ca, cb, "不同密码产生的密文必须不同");
        assertEquals(PLAINTEXT, a.decrypt(ca), "A 密码应能解回原文");
        assertEquals(PLAINTEXT, b.decrypt(cb), "B 密码应能解回原文");
    }
}