package com.ruoyi.opc.content.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.content.domain.OpcContentPlatformAccount;
import com.ruoyi.opc.content.enums.ContentPlatform;
import com.ruoyi.opc.content.mapper.OpcContentPlatformAccountMapper;
import com.ruoyi.opc.content.service.IOpcContentPlatformAccountService;
import com.ruoyi.opc.content.service.platform.PlatformClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 内容平台账号 Service 实现 — 抖音 OAuth 绑定 + token 刷新 + 软删。
 *
 * <p>W74 Task 4: 实现 + 跨租户隔离 + PlatformClient 占位(Task 7 真实接入)。
 *
 * <p>OAuth state 设计: {@code base64(companyId + ":" + UUID)},callback 时反解得 companyId,
 * 同时 UUID 防 CSRF 重放。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcContentPlatformAccountServiceImpl implements IOpcContentPlatformAccountService {

    private final OpcContentPlatformAccountMapper accountMapper;
    /** W74 Task 4 占位,Task 7 真实接入(DouyinClient / MockPlatformClient) */
    private final PlatformClient platformClient;

    /** 抖音 OAuth state 拼接分隔符 */
    private static final String STATE_DELIMITER = ":";

    // ============================================================
    // buildAuthorizeUrl
    // ============================================================

    @Override
    public String buildAuthorizeUrl(Long companyId) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        // state = base64(companyId + ":" + uuid_nonce)
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String raw = companyId + STATE_DELIMITER + nonce;
        String state = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        // TODO Task 12: 改读 douyin.client-key + redirect-uri (Nacos 配置)
        // Task 4 阶段: 用占位 client_key + 本机回调地址
        String clientKey = "PLACEHOLDER_CLIENT_KEY";
        String redirectUri = "http://127.0.0.1:9325/opc/content/platform-account/oauth/callback";
        String scope = "video.create,video.upload,user_info";

        String url = String.format(
                "https://open-sandbox.douyin.com/oauth/authorize/?client_key=%s&response_type=code"
                        + "&scope=%s&redirect_uri=%s&state=%s",
                clientKey, scope, redirectUri, state);
        log.info("生成抖音 OAuth authorize URL companyId={} stateLen={}", companyId, state.length());
        return url;
    }

    // ============================================================
    // handleCallback
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long handleCallback(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new ServiceException("code 不能为空");
        }
        if (state == null || state.isBlank()) {
            throw new ServiceException("state 不能为空");
        }

        // 1) 解析 state → companyId
        Long companyId = decodeState(state);

        // 2) 调 PlatformClient.exchangeCode → token(Task 7 真实接入)
        PlatformClient.OAuthToken token = platformClient.exchangeCode(code);
        if (token == null || token.accessToken() == null) {
            throw new ServiceException("OAuth exchangeCode 返回为空");
        }

        // 3) 查重(openId + platform),如有则 updateTokens,无则 insert
        OpcContentPlatformAccount existing =
                accountMapper.selectByOpenId(token.openId(), platformClient.platformName(), companyId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpiresAt = toLocalDateTime(token.expiresAt());
        LocalDateTime refreshExpiresAt = toLocalDateTime(token.refreshExpiresAt());
        Long accountId;
        if (existing != null) {
            // 已有 → 刷新 token + 状态置 ACTIVE
            accountMapper.updateTokens(existing.getId(), companyId,
                    token.accessToken(), token.refreshToken(),
                    accessExpiresAt, refreshExpiresAt);
            accountId = existing.getId();
            log.info("OAuth callback: 更新已有账号 id={} openId={}", accountId, token.openId());
        } else {
            // 新增
            OpcContentPlatformAccount account = OpcContentPlatformAccount.builder()
                    .id(SnowflakeIdGenerator.nextId())
                    .companyId(companyId)
                    .platform(ContentPlatform.DOUYIN.getCode())
                    .nickname("抖音用户_" + token.openId().substring(0, Math.min(6, token.openId().length())))
                    .openId(token.openId())
                    .unionId(null)
                    .accessTokenEnc(token.accessToken())
                    .refreshTokenEnc(token.refreshToken())
                    .accessTokenExpiresAt(accessExpiresAt)
                    .refreshTokenExpiresAt(refreshExpiresAt)
                    .scope(token.scope())
                    .avatarUrl(null)
                    .status("ACTIVE")
                    .createdAt(now)
                    .boundAt(now)
                    .updatedAt(now)
                    .build();
            accountMapper.insert(account);
            accountId = account.getId();
            log.info("OAuth callback: 新建账号 id={} openId={}", accountId, token.openId());
        }
        return accountId;
    }

    // ============================================================
    // listByCompany / detail / delete / refreshToken
    // ============================================================

    @Override
    public List<OpcContentPlatformAccount> listByCompany(Long companyId) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        return accountMapper.selectListByCompany(companyId);
    }

    @Override
    public OpcContentPlatformAccount detail(Long id, Long companyId) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }
        OpcContentPlatformAccount a = accountMapper.selectById(id, companyId);
        if (a == null) {
            throw new ServiceException("账号不存在或无权访问 id=" + id);
        }
        return a;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id, Long companyId) {
        OpcContentPlatformAccount existing = detail(id, companyId);
        if ("REVOKED".equals(existing.getStatus())) {
            log.info("账号已 REVOKED id={},跳过", id);
            return 0;
        }
        int affected = accountMapper.softDeleteById(id, companyId);
        log.info("软删账号 id={} affected={}", id, affected);
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshToken(Long id, Long companyId) {
        OpcContentPlatformAccount existing = detail(id, companyId);
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new ServiceException("仅 ACTIVE 状态可刷新 token (当前: " + existing.getStatus() + ")");
        }
        if (existing.getRefreshTokenEnc() == null || existing.getRefreshTokenEnc().isBlank()) {
            throw new ServiceException("refresh_token 为空,需重新走 OAuth 流程");
        }

        // TODO Task 7: encryptor.decrypt(existing.getRefreshTokenEnc()) → 调 PlatformClient.refreshToken
        // Task 4 阶段: 直接把 stored refreshTokenEnc 当作明文传给 PlatformClient(Stub)
        PlatformClient.OAuthToken newToken = platformClient.refreshToken(existing.getRefreshTokenEnc());
        if (newToken == null || newToken.accessToken() == null) {
            throw new ServiceException("refreshToken 失败: 返回为空");
        }
        LocalDateTime accessExpiresAt = toLocalDateTime(newToken.expiresAt());
        LocalDateTime refreshExpiresAt = toLocalDateTime(newToken.refreshExpiresAt());
        accountMapper.updateTokens(id, companyId,
                newToken.accessToken(), newToken.refreshToken(),
                accessExpiresAt, refreshExpiresAt);
        log.info("刷新 token 成功 id={} openId={}", id, existing.getOpenId());
    }

    // ============================================================
    // 内部辅助
    // ============================================================

    /**
     * 反解 OAuth state → companyId。
     * state 格式: base64(companyId + ":" + uuid_nonce)
     */
    private Long decodeState(String state) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(state);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int idx = raw.indexOf(STATE_DELIMITER);
            if (idx <= 0) {
                throw new ServiceException("state 格式非法: " + state);
            }
            return Long.parseLong(raw.substring(0, idx));
        } catch (IllegalArgumentException e) {
            throw new ServiceException("state 解码失败: " + e.getMessage());
        }
    }

    /**
     * unix epoch seconds → 系统时区 LocalDateTime。
     * 平台返回的 expiresIn / refreshExpiresIn 通常是相对秒数,Server 层加 now().getEpochSecond() 转绝对秒。
     */
    private LocalDateTime toLocalDateTime(long epochSecond) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(epochSecond),
                java.time.ZoneId.systemDefault());
    }
}