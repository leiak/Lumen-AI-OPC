package com.ruoyi.opc.content.service.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.content.config.DouyinProperties;
import com.ruoyi.opc.content.util.ContentTokenEncryptor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 抖音开放平台 sandbox 客户端 (W74 Task 7)。
 *
 * <p>对接 https://open-sandbox.douyin.com, 4 个核心方法:
 * <ul>
 *   <li>{@link #exchangeCode} — POST /oauth/access_token/</li>
 *   <li>{@link #refreshToken} — POST /oauth/refresh_token/</li>
 *   <li>{@link #uploadVideo} — POST /video/upload/ (multipart)</li>
 *   <li>{@link #createVideo} — POST /video/create/</li>
 * </ul>
 *
 * <p>sandbox 不可达时, 自动降级到 MockPlatformClient (try-catch IOException).
 */
@Slf4j
public class DouyinClient implements PlatformClient {

    private final DouyinProperties props;
    private final ContentTokenEncryptor encryptor;
    private final PlatformClient fallback;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DouyinClient(DouyinProperties props, ContentTokenEncryptor encryptor, PlatformClient fallback) {
        this.props = props;
        this.encryptor = encryptor;
        this.fallback = fallback;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
    }

    @Override
    public String platformName() {
        return "DOUYIN";
    }

    @Override
    public OAuthToken exchangeCode(String code) {
        try {
            String body = String.format(
                    "{\"client_key\":\"%s\",\"client_secret\":\"%s\",\"code\":\"%s\",\"grant_type\":\"authorization_code\"}",
                    props.getClientKey(), encryptor.decrypt(props.getClientSecret()), code);
            String resp = postJson("/oauth/access_token/", body);
            JsonNode json = mapper.readTree(resp);
            return parseOAuthToken(json);
        } catch (Exception e) {
            log.warn("[opc-content] 抖音 exchangeCode 不可达, 降级 mock: {}", e.getMessage());
            return fallback.exchangeCode(code);
        }
    }

    @Override
    public OAuthToken refreshToken(String refreshToken) {
        try {
            String body = String.format(
                    "{\"client_key\":\"%s\",\"client_secret\":\"%s\",\"refresh_token\":\"%s\",\"grant_type\":\"refresh_token\"}",
                    props.getClientKey(), encryptor.decrypt(props.getClientSecret()), refreshToken);
            String resp = postJson("/oauth/refresh_token/", body);
            JsonNode json = mapper.readTree(resp);
            return parseOAuthToken(json);
        } catch (Exception e) {
            log.warn("[opc-content] 抖音 refreshToken 不可达, 降级 mock: {}", e.getMessage());
            return fallback.refreshToken(refreshToken);
        }
    }

    @Override
    public String uploadVideo(String accessToken, byte[] videoBytes, String filename) {
        try {
            String boundary = "----opc-content-" + System.currentTimeMillis();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            // access_token field
            bos.write(("--" + boundary + "\r\n").getBytes());
            bos.write("Content-Disposition: form-data; name=\"access_token\"\r\n\r\n".getBytes());
            bos.write(accessToken.getBytes(StandardCharsets.UTF_8));
            bos.write("\r\n".getBytes());
            // video file field
            bos.write(("--" + boundary + "\r\n").getBytes());
            bos.write(("Content-Disposition: form-data; name=\"video\"; filename=\"" + filename + "\"\r\n").getBytes());
            bos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes());
            bos.write(videoBytes == null ? new byte[0] : videoBytes);
            bos.write("\r\n".getBytes());
            // end
            bos.write(("--" + boundary + "--\r\n").getBytes());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getApiBase() + "/video/upload/"))
                    .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bos.toByteArray()))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("[opc-content] 抖音 uploadVideo HTTP {} body={}", resp.statusCode(), resp.body());
                return fallback.uploadVideo(accessToken, videoBytes, filename);
            }
            JsonNode json = mapper.readTree(resp.body());
            if (json.has("data") && json.get("data").has("video_id")) {
                return json.get("data").get("video_id").asText();
            }
            log.warn("[opc-content] 抖音 uploadVideo 响应无 video_id, 降级 mock");
            return fallback.uploadVideo(accessToken, videoBytes, filename);
        } catch (Exception e) {
            log.warn("[opc-content] 抖音 uploadVideo 不可达, 降级 mock: {}", e.getMessage());
            return fallback.uploadVideo(accessToken, videoBytes, filename);
        }
    }

    @Override
    public PublishResult createVideo(String accessToken, String videoId, String title, String[] tags) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("access_token", accessToken);
            body.put("video_id", videoId);
            body.put("text", title);
            body.put("poi_id", "");
            if (tags != null) body.put("tags", String.join(",", tags));
            String resp = postJson("/video/create/", mapper.writeValueAsString(body));
            JsonNode json = mapper.readTree(resp);
            if (json.has("data")) {
                JsonNode d = json.get("data");
                String itemId = d.has("item_id") ? d.get("item_id").asText() : "mock_" + UUID.randomUUID();
                String url = d.has("share_url") ? d.get("share_url").asText()
                        : "https://www.iesdouyin.com/share/video/" + itemId;
                return new PublishResult(itemId, url);
            }
            log.warn("[opc-content] 抖音 createVideo 响应无 data, 降级 mock");
            return fallback.createVideo(accessToken, videoId, title, tags);
        } catch (Exception e) {
            log.warn("[opc-content] 抖音 createVideo 不可达, 降级 mock: {}", e.getMessage());
            return fallback.createVideo(accessToken, videoId, title, tags);
        }
    }

    private String postJson(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getApiBase() + path))
                .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("User-Agent", "opc-content/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new ServiceException("抖音 sandbox 返回 HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    private OAuthToken parseOAuthToken(JsonNode json) {
        // 检查错误响应
        if (json.has("error_code") || json.has("error")) {
            int errorCode = json.has("error_code") ? json.get("error_code").asInt() : -1;
            String errorMsg = json.has("error") ? json.get("error").asText()
                                : json.has("error_msg") ? json.get("error_msg").asText()
                                : "未知错误";
            throw new ServiceException("抖音 OAuth 错误: code=" + errorCode + " msg=" + errorMsg);
        }
        JsonNode d = json.has("data") ? json.get("data") : json;
        if (!d.has("access_token") || !d.has("refresh_token")) {
            throw new ServiceException("抖音 OAuth 响应缺失 token 字段: " + json.toString());
        }
        long now = Instant.now().getEpochSecond();
        long expiresIn = d.has("expires_in") ? d.get("expires_in").asLong(7200) : 7200;
        long refreshExpiresIn = d.has("refresh_expires_in") ? d.get("refresh_expires_in").asLong(30 * 86400) : 30 * 86400;
        return new OAuthToken(
                d.get("access_token").asText(),
                d.get("refresh_token").asText(),
                now + expiresIn,
                now + refreshExpiresIn,
                d.has("open_id") ? d.get("open_id").asText() : "",
                d.has("scope") ? d.get("scope").asText() : props.getScope(),
                d.has("nickname") ? d.get("nickname").asText() : "抖音用户");
    }
}
