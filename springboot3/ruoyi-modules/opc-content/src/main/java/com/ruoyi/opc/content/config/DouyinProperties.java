package com.ruoyi.opc.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 抖音开放平台 sandbox 配置 (W74 Task 7)。
 *
 * <p>对应 Nacos 配置项 {@code douyin.*}, 通过 {@code @EnableConfigurationProperties} 激活。
 */
@Data
@ConfigurationProperties(prefix = "douyin")
public class DouyinProperties {
    /** 抖音开放平台 client_key */
    private String clientKey;
    /** client_secret (ENC 加密,运行时 Jasypt 解密) */
    private String clientSecret;
    /** OAuth 回调地址 */
    private String redirectUri;
    /** API base URL, 默认 https://open-sandbox.douyin.com */
    private String apiBase = "https://open-sandbox.douyin.com";
    /** sandbox 模式开关 */
    private boolean sandbox = true;
    /** OAuth scope, 逗号分隔 */
    private String scope = "video.create,video.upload,user_info";
    /** 请求超时 (毫秒), 默认 30000 */
    private int connectTimeoutMs = 30000;
    private int readTimeoutMs = 60000;
}
