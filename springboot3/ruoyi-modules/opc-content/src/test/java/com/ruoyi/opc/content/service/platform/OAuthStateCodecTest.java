package com.ruoyi.opc.content.service.platform;

import com.ruoyi.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OAuthStateCodec} 单测 —— 3b 回归护栏
 *
 * <p>state 带着「这次授权属于哪家公司」, 而回调那一步服务端拿不到登录态 ——
 * 所以 companyId 只能靠签名保证不可伪造。以前 state 是裸 base64, 改一改就能
 * 把公司号换成别人的。</p>
 *
 * @author OAC
 */
@DisplayName("OAuthStateCodec 单测")
class OAuthStateCodecTest
{
    private static final String SECRET = "unit-test-secret";
    private static final long TTL_SECONDS = 600L;

    private final OAuthStateCodec codec = new OAuthStateCodec(SECRET, TTL_SECONDS);

    /** 用同一把密钥手工签一个 state —— 用来构造「签名对但已过期」这种场景。 */
    private String sign(String payload) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("正常往返: encode 出来的 state 能解回同一个 companyId")
    void roundTrip() {
        String state = codec.encode(7L);

        assertThat(codec.decode(state)).isEqualTo(7L);
    }

    @Test
    @DisplayName("state 带签名, 不是裸 base64")
    void stateIsSigned() {
        String state = codec.encode(7L);

        assertThat(state).contains(".");
        // 去掉签名部分剩下的就是载荷, 里面能看到公司号 —— 载荷是明文的,
        // 但没有签名就伪造不出来
        String body = state.substring(0, state.indexOf('.'));
        String payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        assertThat(payload).startsWith("7:");
    }

    @Test
    @DisplayName("裸 base64 state (老格式) → 拒绝")
    void legacyBase64StateIsRejected() {
        String legacy = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2:abc123".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(legacy))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("state");
    }

    @Test
    @DisplayName("改了载荷(换成别家公司)但沿用旧签名 → 拒绝")
    void tamperedPayloadIsRejected() {
        String valid = codec.encode(7L);
        String body = valid.substring(0, valid.indexOf('.'));
        String signature = valid.substring(valid.indexOf('.') + 1);

        // 把载荷里的公司号从 7 改成 99, 签名保持不变
        String payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        String forgedBody = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("99" + payload.substring(1)).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(forgedBody + "." + signature))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("state 校验失败");
    }

    @Test
    @DisplayName("签名被改 → 拒绝")
    void tamperedSignatureIsRejected() {
        String valid = codec.encode(7L);
        String body = valid.substring(0, valid.indexOf('.'));
        String signature = valid.substring(valid.indexOf('.') + 1);
        // 换掉签名最后一个字符
        char last = signature.charAt(signature.length() - 1);
        String tampered = signature.substring(0, signature.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> codec.decode(body + "." + tampered))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("state 校验失败");
    }

    @Test
    @DisplayName("签名对但已过期 → 拒绝")
    void expiredStateIsRejected() throws Exception {
        long issuedAt = System.currentTimeMillis() - (TTL_SECONDS + 60) * 1000L;
        String state = sign("7:nonce123:" + issuedAt);

        assertThatThrownBy(() -> codec.decode(state))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("过期");
    }

    @Test
    @DisplayName("空 state / 缺 companyId → 拒绝")
    void blankStateAndNullCompanyIdAreRejected() {
        assertThatThrownBy(() -> codec.decode(null)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> codec.decode("")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> codec.encode(null)).isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("没配密钥时也照样能签 —— 随机密钥, 不是明文 state")
    void worksWithoutConfiguredSecret() {
        OAuthStateCodec random = new OAuthStateCodec("", TTL_SECONDS);

        String state = random.encode(7L);

        assertThat(random.decode(state)).isEqualTo(7L);
    }
}
