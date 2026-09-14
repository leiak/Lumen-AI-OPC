package com.ruoyi.opc.content.service.platform;

/**
 * 内容发布平台客户端抽象(W74 Task 4 占位 — Task 7 完整实现 + DouyinClient + MockPlatformClient)。
 *
 * <p>Task 7 将:
 * <ul>
 *   <li>写 {@code DouyinClient} (JDK 11+ HttpClient 调 open-sandbox.douyin.com,失败自动降级 Mock)</li>
 *   <li>写 {@code MockPlatformClient} (本地/CI 场景)</li>
 *   <li>{@code PlatformConfig} 用 {@code @ConditionalOnProperty} 切换</li>
 *   <li>{@code ContentTokenEncryptor} AES 加密 access_token / refresh_token</li>
 * </ul>
 *
 * @author OAC
 */
public interface PlatformClient {

    /** 平台名(DOUYIN) */
    String platformName();

    /**
     * OAuth code → access_token / refresh_token / open_id / scope
     */
    OAuthToken exchangeCode(String code);

    /**
     * 刷新 access_token(过期前调用,refresh_token 通常 30 天有效)
     */
    OAuthToken refreshToken(String refreshToken);

    /**
     * 上传视频到平台,返回平台侧 video_id
     */
    String uploadVideo(String accessToken, byte[] videoBytes, String filename);

    /**
     * 创建视频发布(标题+标签),返回平台侧 post_id + 链接
     */
    PublishResult createVideo(String accessToken, String videoId, String title, String[] tags);

    /**
     * OAuth token record
     *
     * <ul>
     *   <li>{@code expiresAt} — access_token 过期时间(unix epoch seconds)</li>
     *   <li>{@code refreshExpiresAt} — refresh_token 过期时间(unix epoch seconds,通常 access + 30 天)</li>
     * </ul>
     */
    record OAuthToken(String accessToken, String refreshToken,
                      long expiresAt, long refreshExpiresAt,
                      String openId, String scope) {}

    /**
     * 发布结果 record
     */
    record PublishResult(String externalPostId, String externalUrl) {}
}