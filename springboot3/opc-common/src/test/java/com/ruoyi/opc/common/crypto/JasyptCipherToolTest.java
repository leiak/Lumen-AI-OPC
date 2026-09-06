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
        // PBE 输出随机化：相同密码相同明文也可能不同，但密码不同一定不同
        assertNotEquals(a.decrypt(ca), b.decrypt(cb));
    }
}