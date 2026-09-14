package com.ruoyi.opc.content.config;

import com.ruoyi.opc.content.service.platform.DouyinClient;
import com.ruoyi.opc.content.service.platform.MockPlatformClient;
import com.ruoyi.opc.content.service.platform.PlatformClient;
import com.ruoyi.opc.content.util.ContentTokenEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 平台客户端配置 (W74 Task 7)。
 *
 * <p>通过 {@code opc.content.platform.mock} 控制:
 * <ul>
 *   <li>true (default) → MockPlatformClient (dev/test, sandbox 不可达自动降级)</li>
 *   <li>false → DouyinClient (sandbox 真实 HTTP 调用)</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DouyinProperties.class)
public class PlatformConfig {

    @Bean
    @ConditionalOnProperty(prefix = "opc.content.platform", name = "mock", havingValue = "true", matchIfMissing = true)
    public PlatformClient mockPlatformClient() {
        log.warn("[opc-content] 启用 MockPlatformClient (sandbox 不可用时降级), 生产需设 opc.content.platform.mock=false");
        return new MockPlatformClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "opc.content.platform", name = "mock", havingValue = "false")
    @ConditionalOnMissingBean(PlatformClient.class)
    public PlatformClient douyinClient(DouyinProperties props, ContentTokenEncryptor encryptor) {
        log.info("[opc-content] 启用 DouyinClient 真实 HTTP, apiBase={}", props.getApiBase());
        // 传入 mock 实例作为降级目标 (即使 mock=false, 网络失败时仍降级)
        return new DouyinClient(props, encryptor, new MockPlatformClient());
    }
}
