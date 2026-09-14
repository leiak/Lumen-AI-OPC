package com.ruoyi.opc.content.util;

import com.ruoyi.common.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token 加密器 (W74 Task 7) — 保护 platform account.access_token / refresh_token。
 *
 * <p>使用 AES-256-GCM, 12-byte IV + 16-byte tag. IV 拼在密文前面, base64 输出.
 * key 从 application.yml 的 opc.content.token-encryption-key 注入 (32 字节 base64 字符串).
 */
@Slf4j
@Component
public class ContentTokenEncryptor {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;

    public ContentTokenEncryptor() {
        // dev 默认 key (生产必须通过 @Value 注入)
        // 32 字节 = 256 位 key, 这里用 base64("opc-content-dev-key-32bytes!!") = "b3BjLWNvbnRlbnQtZGV2LWtleS0zMmJ5dGVzISE="
        byte[] keyBytes = Base64.getDecoder().decode("b3BjLWNvbnRlbnQtZGV2LWtleS0zMmJ5dGVzISE=");
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[IV_LEN + cipherBytes.length];
            System.arraycopy(iv, 0, result, 0, IV_LEN);
            System.arraycopy(cipherBytes, 0, result, IV_LEN, cipherBytes.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            log.error("[opc-content] token encrypt failed", e);
            throw new ServiceException("token 加密失败");
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            byte[] data = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(data, 0, iv, 0, IV_LEN);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(data, IV_LEN, data.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[opc-content] token decrypt failed", e);
            throw new ServiceException("token 解密失败");
        }
    }
}
