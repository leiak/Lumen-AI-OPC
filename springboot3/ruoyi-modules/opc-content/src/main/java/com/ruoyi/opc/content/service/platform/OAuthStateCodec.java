package com.ruoyi.opc.content.service.platform;

import com.ruoyi.common.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 抖音 OAuth state 编解码 —— 3b 回归护栏
 *
 * <p><b>为什么 state 必须签名</b>：state 是回调时唯一能带回「这次授权属于哪家公司」
 * 的载体。回调是浏览器顶层跳转过去的，服务端拿不到登录态（token 在前端 localStorage 里，
 * 导航请求不会带 Authorization 头），所以 state 里的 companyId 就是落库时用的 companyId。
 * 以前 state 是 {@code base64(companyId + ":" + nonce)} —— base64 是<b>编码</b>不是<b>签名</b>，
 * 任何人改一改就能把公司号换成别人的，于是「把抖音账号绑到别家公司」是一条现成的路。</p>
 *
 * <p>现在 state = {@code base64url(companyId:nonce:issuedAt) + "." + base64url(HMAC-SHA256(载荷))}。
 * 回调时先验签（常量时间比较）再解载荷，并检查时效 —— 伪造的、篡改的、过期的 state 一律拒绝。</p>
 *
 * <p><b>密钥</b>：{@code opc.content.oauth.state-secret}。没配就在启动时随机生成一把
 * （state 依然不可伪造，代价是重启后未用完的 state 作废、多实例之间不通用 ——
 * 多实例部署必须配固定密钥）。</p>
 *
 * <p><b>已知边界</b>：state 在有效期内可以被重放一次（标准解法是把 nonce 存服务端做一次性，
 * 需要一张表或 Redis）。绑定的损害面是「多绑一个抖音账号到本公司」，不是越权读数据。</p>
 *
 * @author OAC
 */
@Slf4j
@Component
public class OAuthStateCodec
{
    /** 载荷字段分隔符。 */
    private static final String FIELD_SEPARATOR = ":";

    /** 载荷与签名之间的分隔符。 */
    private static final char SIGNATURE_SEPARATOR = '.';

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 默认时效: 10 分钟。授权走一圈通常 1-2 分钟, 留够余量即可。 */
    private static final long DEFAULT_TTL_SECONDS = 600L;

    private final byte[] key;

    private final long ttlSeconds;

    public OAuthStateCodec(@Value("${opc.content.oauth.state-secret:}") String configuredSecret,
                           @Value("${opc.content.oauth.state-ttl-seconds:600}") long ttlSeconds)
    {
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        if (configuredSecret != null && !configuredSecret.isBlank())
        {
            this.key = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        else
        {
            SecureRandom random = new SecureRandom();
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            this.key = generated;
            log.warn("[OAuth state] 未配置 opc.content.oauth.state-secret, 本次启动使用随机密钥 —— "
                    + "state 仍不可伪造, 但重启后未用完的 state 会作废; 多实例部署必须配置固定密钥");
        }
    }

    /**
     * 生成 state。
     *
     * @param companyId 本次授权所属公司（调用方应传服务端判定的作用域, 不传客户端原值）
     * @return {@code base64url(载荷).base64url(签名)}
     */
    public String encode(Long companyId)
    {
        if (companyId == null)
        {
            throw new ServiceException("companyId 不能为空");
        }
        String payload = companyId
                + FIELD_SEPARATOR + UUID.randomUUID().toString().replace("-", "")
                + FIELD_SEPARATOR + System.currentTimeMillis();
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + SIGNATURE_SEPARATOR + sign(body);
    }

    /**
     * 校验并解析 state → companyId。
     *
     * @param state 回调带回的 state
     * @return 签名有效且未过期的 companyId
     * @throws ServiceException state 格式非法 / 签名不匹配 / 已过期
     */
    public Long decode(String state)
    {
        if (state == null || state.isBlank())
        {
            throw new ServiceException("state 不能为空", 400);
        }
        int idx = state.indexOf(SIGNATURE_SEPARATOR);
        if (idx <= 0 || idx == state.length() - 1)
        {
            throw new ServiceException("state 格式非法", 403);
        }
        String body = state.substring(0, idx);
        String signature = state.substring(idx + 1);

        // 验签 —— 常量时间比较, 别用 String.equals
        byte[] expected = Base64.getUrlDecoder().decode(sign(body));
        byte[] actual;
        try
        {
            actual = Base64.getUrlDecoder().decode(signature);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("state 校验失败", 403);
        }
        if (!MessageDigest.isEqual(expected, actual))
        {
            throw new ServiceException("state 校验失败", 403);
        }

        String payload;
        try
        {
            payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("state 格式非法", 403);
        }
        String[] fields = payload.split(FIELD_SEPARATOR);
        if (fields.length != 3)
        {
            throw new ServiceException("state 格式非法", 403);
        }
        long issuedAt;
        long companyId;
        try
        {
            companyId = Long.parseLong(fields[0]);
            issuedAt = Long.parseLong(fields[2]);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("state 格式非法", 403);
        }
        long ageSeconds = (System.currentTimeMillis() - issuedAt) / 1000L;
        if (ageSeconds > ttlSeconds)
        {
            throw new ServiceException("state 已过期, 请重新发起授权", 403);
        }
        return companyId;
    }

    /** 对载荷签名（返回 base64url）。 */
    private String sign(String body)
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        }
        catch (Exception e)
        {
            // 签名失败 = state 发不出去, 不能降级成「不签名」
            throw new ServiceException("state 签名失败: " + e.getMessage());
        }
    }
}
