package com.ruoyi.opc.content.service.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 平台客户端 Stub — W74 Task 4 占位实现,真实 PlatformClient(DouyinClient / MockPlatformClient)在 Task 7。
 *
 * <p>Task 4 阶段: 让 ServiceImpl 可注入并调用,全部方法返回 mock 数据。
 *
 * @author OAC
 */
@Slf4j
@Component
public class StubPlatformClient implements PlatformClient {

    @Override
    public String platformName() {
        return "STUB";
    }

    @Override
    public OAuthToken exchangeCode(String code) {
        log.warn("[opc-content] StubPlatformClient.exchangeCode 暂用占位 codeLen={} (Task 7 接入)",
                code == null ? 0 : code.length());
        // TODO Task 7: DouyinClient.exchangeCode → POST /oauth/access_token/
        return new OAuthToken("stub_access_" + UUID.randomUUID(),
                              "stub_refresh_" + UUID.randomUUID(),
                              Instant.now().getEpochSecond() + 3600 * 24 * 30,
                              "stub_open_id",
                              "video.create,video.upload");
    }

    @Override
    public OAuthToken refreshToken(String refreshToken) {
        log.warn("[opc-content] StubPlatformClient.refreshToken 暂用占位 (Task 7 接入)");
        // TODO Task 7: DouyinClient.refreshToken
        return new OAuthToken("stub_access_" + UUID.randomUUID(),
                              refreshToken,
                              Instant.now().getEpochSecond() + 3600 * 24 * 30,
                              "stub_open_id",
                              "video.create,video.upload");
    }

    @Override
    public String uploadVideo(String accessToken, byte[] videoBytes, String filename) {
        log.warn("[opc-content] StubPlatformClient.uploadVideo 暂用占位 filename={} (Task 7 接入)", filename);
        // TODO Task 7: DouyinClient.uploadVideo → POST /video/upload/
        return "stub_video_" + UUID.randomUUID();
    }

    @Override
    public PublishResult createVideo(String accessToken, String videoId, String title, String[] tags) {
        log.warn("[opc-content] StubPlatformClient.createVideo 暂用占位 videoId={} (Task 7 接入)", videoId);
        // TODO Task 7: DouyinClient.createVideo → POST /video/create/
        return new PublishResult("stub_post_" + UUID.randomUUID(),
                                 "https://stub.douyin.com/video/" + videoId);
    }
}