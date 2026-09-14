package com.ruoyi.opc.content.util;

import com.ruoyi.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ContentTokenEncryptor 单测 (W74 Task 9 — 8 cases)。
 *
 * <p>覆盖: AES-256-GCM round-trip + 32 字节 key 校验 + IV 随机性 + 篡改检测 + UTF-8 (中文) + null 入参。
 *
 * @author OAC
 */
@DisplayName("ContentTokenEncryptor 单测 (8 cases)")
class ContentTokenEncryptorTest {

    /** 32 字节 (AES-256) 的 base64 编码 key — 32 个 'X' */
    private static final String VALID_KEY_BASE64 = "WFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFg=";

    /** 16 字节 (AES-128) 的 base64 编码 key — 不合法 */
    private static final String INVALID_KEY_BASE64 = "WFhYWFhYWFhYWFhYWFhYWA==";

    private ContentTokenEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new ContentTokenEncryptor();
        ReflectionTestUtils.setField(encryptor, "keyBase64", VALID_KEY_BASE64);
        ReflectionTestUtils.invokeMethod(encryptor, "init");
    }

    /** Test 1 */
    @Test
    @DisplayName("encrypt → decrypt round-trip 还原原文")
    void encrypt_decrypt_roundTrip() {
        String plain = "test_access_token_abc123_xyz";

        String cipher = encryptor.encrypt(plain);
        String decrypted = encryptor.decrypt(cipher);

        assertThat(decrypted).isEqualTo(plain);
        assertThat(cipher).isNotEqualTo(plain);
    }

    /** Test 2 */
    @Test
    @DisplayName("encrypt - 同明文两次加密输出不同(IV 随机)")
    void encrypt_randomIv_eachTimeDifferent() {
        String plain = "same_plain_text";

        String c1 = encryptor.encrypt(plain);
        String c2 = encryptor.encrypt(plain);

        assertThat(c1).isNotEqualTo(c2);
        // 但两者都能解密回原文
        assertThat(encryptor.decrypt(c1)).isEqualTo(plain);
        assertThat(encryptor.decrypt(c2)).isEqualTo(plain);
    }

    /** Test 3 */
    @Test
    @DisplayName("encrypt - UTF-8 多字符正确加解密")
    void encrypt_utf8_chinese() {
        String plain = "抖音 access_token 包含中文 _abc 测试 123";

        String cipher = encryptor.encrypt(plain);
        String decrypted = encryptor.decrypt(cipher);

        assertThat(decrypted).isEqualTo(plain);
    }

    /** Test 4 */
    @Test
    @DisplayName("decrypt - 篡改密文抛 ServiceException(GCM tag 校验失败)")
    void decrypt_tampered_throws() {
        String cipher = encryptor.encrypt("original");
        // 篡改密文最后 1 个字符(改 base64 末尾 — 大概率破坏 GCM tag)
        String tampered = cipher.substring(0, cipher.length() - 1) +
                (cipher.charAt(cipher.length() - 1) == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("解密失败");
    }

    /** Test 5 */
    @Test
    @DisplayName("init - key 不是 32 字节抛 ServiceException")
    void init_invalidKeyLength_throws() {
        ContentTokenEncryptor bad = new ContentTokenEncryptor();
        ReflectionTestUtils.setField(bad, "keyBase64", INVALID_KEY_BASE64);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(bad, "init"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("32 字节");
    }

    /** Test 6 */
    @Test
    @DisplayName("encrypt - null 入参返回 null,不抛错")
    void encrypt_null_returnsNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    /** Test 7 */
    @Test
    @DisplayName("encrypt - 空字符串可以加密解密(往返空)")
    void encrypt_emptyString() {
        String cipher = encryptor.encrypt("");

        assertThat(cipher).isNotNull().isNotEmpty();
        assertThat(encryptor.decrypt(cipher)).isEqualTo("");
    }

    /** Test 8 */
    @Test
    @DisplayName("encrypt - 输出是 base64,长度大于明文(IV 12 字节 + ciphertext + tag 16 字节,base64 编码)")
    void encrypt_outputIsBase64() {
        String plain = "x";
        String cipher = encryptor.encrypt(plain);

        // 12(IV) + 1(plain) + 16(tag) = 29 字节 → base64 ≈ 40 字符
        assertThat(cipher.length()).isGreaterThan(plain.length());
        // 能被 base64 解码
        byte[] decoded = Base64.getDecoder().decode(cipher);
        assertThat(decoded).hasSize(29);
        // 前 12 字节是 IV
        byte[] iv = new byte[12];
        System.arraycopy(decoded, 0, iv, 0, 12);
        assertThat(iv).isNotEqualTo(new byte[12]); // IV 非全 0
    }
}