package com.ruoyi.opc.content.service.platform;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

/**
 * Mock 平台客户端 (W74 Task 7) — dev/test 环境兜底。
 *
 * <p>由 {@link com.ruoyi.opc.content.config.PlatformConfig} 通过
 * {@code opc.content.platform.mock=true} 注入。prod 必须设 false 启用真实 DouyinClient。
 *
 * <p>所有方法返回 mock 数据 + log.warn, 业务可正常跑通。
 */
@Slf4j
public class MockPlatformClient implements PlatformClient {

    @Override
    public String platformName() {
        return "DOUYIN";
    }

    @Override
    public OAuthToken exchangeCode(String code) {
        log.warn("[opc-content-mock] exchangeCode code={}", code == null ? "null" : code.substring(0, Math.min(8, code.length())));
        long now = Instant.now().getEpochSecond();
        return new OAuthToken(
                "mock_access_" + UUID.randomUUID(),
                "mock_refresh_" + UUID.randomUUID(),
                now + 7200L,            // 2h
                now + 30L * 86400L,     // 30d
                "mock_open_id_" + UUID.randomUUID(),
                "video.create,video.upload,user_info",
                "Mock测试用户");
    }

    @Override
    public OAuthToken refreshToken(String refreshToken) {
        log.warn("[opc-content-mock] refreshToken");
        long now = Instant.now().getEpochSecond();
        return new OAuthToken(
                "mock_access_" + UUID.randomUUID(),
                "mock_refresh_" + UUID.randomUUID(),
                now + 7200L,
                now + 30L * 86400L,
                "mock_open_id",
                "video.create,video.upload,user_info",
                "Mock测试用户");
    }

    @Override
    public String uploadVideo(String accessToken, byte[] videoBytes, String filename) {
        log.warn("[opc-content-mock] uploadVideo filename={} size={}", filename, videoBytes == null ? 0 : videoBytes.length);
        return "mock_video_id_" + UUID.randomUUID();
    }

    @Override
    public PublishResult createVideo(String accessToken, String videoId, String title, String[] tags) {
        log.warn("[opc-content-mock] createVideo videoId={} title={}", videoId, title);
        String postId = "mock_post_" + UUID.randomUUID();
        return new PublishResult(postId, "https://mock.douyin.com/video/" + postId);
    }
}
