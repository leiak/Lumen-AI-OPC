package com.ruoyi.opc.content.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.content.domain.OpcContentPlatformAccount;
import com.ruoyi.opc.content.enums.ContentPlatform;
import com.ruoyi.opc.content.mapper.OpcContentPlatformAccountMapper;
import com.ruoyi.opc.content.service.platform.OAuthStateCodec;
import com.ruoyi.opc.content.service.platform.PlatformClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcContentPlatformAccountServiceImpl 单测 (W74 Task 9 — 10 cases)。
 *
 * <p>覆盖: buildAuthorizeUrl + handleCallback + detail + list + delete + refreshToken。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcContentPlatformAccountServiceImpl 单测 (10 cases)")
class OpcContentPlatformAccountServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;

    @Mock
    private OpcContentPlatformAccountMapper accountMapper;

    @Mock
    private PlatformClient platformClient;

    /** 真的编解码 state —— 这里要验的是「伪造的 state 进不来」, 不是 mock 出来的返回。 */
    @Spy
    private OAuthStateCodec stateCodec = new OAuthStateCodec("unit-test-secret", 600L);

    @InjectMocks
    private OpcContentPlatformAccountServiceImpl accountService;

    /** Test 1 */
    @Test
    @DisplayName("buildAuthorizeUrl - 返回合法 URL,含 client_key 与 state 参数")
    void buildAuthorizeUrl_success() {
        String url = accountService.buildAuthorizeUrl(COMPANY_ID);

        assertThat(url).startsWith("https://open-sandbox.douyin.com/oauth/authorize/");
        assertThat(url).contains("client_key=PLACEHOLDER_CLIENT_KEY");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=video.create,video.upload,user_info");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("state=");
        assertThat(url).contains("state".substring(0, 0)); // trivial sanity
    }

    /** Test 2 */
    @Test
    @DisplayName("buildAuthorizeUrl - 缺 companyId 抛 ServiceException")
    void buildAuthorizeUrl_blankCompanyId_throws() {
        assertThatThrownBy(() -> accountService.buildAuthorizeUrl(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
    }

    /** Test 3 */
    @Test
    @DisplayName("handleCallback - 缺 code 抛 ServiceException")
    void handleCallback_blankCode_throws() {
        assertThatThrownBy(() -> accountService.handleCallback("", "state"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("code");
    }

    /** Test 4 */
    @Test
    @DisplayName("handleCallback - 缺 state 抛 ServiceException")
    void handleCallback_blankState_throws() {
        assertThatThrownBy(() -> accountService.handleCallback("code", ""))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("state");
    }

    /** Test 5 */
    @Test
    @DisplayName("handleCallback - 合法 state(签名有效) → 走 exchangeCode 流程")
    void handleCallback_validState_callsExchangeCode() {
        // state = base64url(1:nonce:issuedAt) + "." + HMAC 签名 — COMPANY_ID=1
        String state = stateCodec.encode(1L);
        long now = Instant.now().getEpochSecond();
        when(platformClient.exchangeCode("code_x")).thenReturn(new PlatformClient.OAuthToken(
                "a", "r", now + 100, now + 200, "open_xyz", "video.create", "用户"));
        when(platformClient.platformName()).thenReturn("DOUYIN");
        when(accountMapper.selectByOpenId("open_xyz", "DOUYIN", 1L)).thenReturn(null);

        accountService.handleCallback("code_x", state);

        ArgumentCaptor<OpcContentPlatformAccount> captor = ArgumentCaptor.forClass(OpcContentPlatformAccount.class);
        verify(accountMapper).insert(captor.capture());
        OpcContentPlatformAccount inserted = captor.getValue();
        assertThat(inserted.getOpenId()).isEqualTo("open_xyz");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getPlatform()).isEqualTo(ContentPlatform.DOUYIN.getCode());
        assertThat(inserted.getAccessTokenEnc()).isEqualTo("a");
    }

    /** Test 6 */
    @Test
    @DisplayName("handleCallback - openId 已存在 → 走 updateTokens,不 insert")
    void handleCallback_existingOpenId_update() {
        String state = stateCodec.encode(1L);
        long now = Instant.now().getEpochSecond();
        when(platformClient.exchangeCode("code_y")).thenReturn(new PlatformClient.OAuthToken(
                "new_a", "new_r", now + 100, now + 200, "open_existing", "video.create", "用户"));
        when(platformClient.platformName()).thenReturn("DOUYIN");
        OpcContentPlatformAccount existing = OpcContentPlatformAccount.builder()
                .id(ACCOUNT_ID).companyId(1L).openId("open_existing")
                .platform("DOUYIN").status("ACTIVE").build();
        when(accountMapper.selectByOpenId("open_existing", "DOUYIN", 1L)).thenReturn(existing);

        accountService.handleCallback("code_y", state);

        verify(accountMapper).updateTokens(eq(ACCOUNT_ID), eq(1L),
                eq("new_a"), eq("new_r"), any(), any());
        verify(accountMapper, never()).insert(any(OpcContentPlatformAccount.class));
    }

    /** Test 6b —— 3b 回归护栏 */
    @Test
    @DisplayName("handleCallback - 伪造的 state(裸 base64) → 拒绝, 连 exchangeCode 都不调")
    void handleCallback_forgedState_isRejected() {
        // 老格式: base64(2:nonce) —— 编码不是签名, 谁都能把公司号改成 2
        String forged = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2:abc123nonce".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> accountService.handleCallback("code_z", forged))
                .isInstanceOf(ServiceException.class);

        verify(platformClient, never()).exchangeCode(any());
        verify(accountMapper, never()).insert(any(OpcContentPlatformAccount.class));
    }

    /** Test 6c —— 3b 回归护栏 */
    @Test
    @DisplayName("handleCallback - 改了载荷(换成别家公司)的 state → 拒绝")
    void handleCallback_tamperedState_isRejected() {
        String valid = stateCodec.encode(1L);
        String body = valid.substring(0, valid.indexOf('.'));
        String signature = valid.substring(valid.indexOf('.') + 1);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(body),
                java.nio.charset.StandardCharsets.UTF_8);
        String forgedBody = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("99" + payload.substring(1))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> accountService.handleCallback("code_z", forgedBody + "." + signature))
                .isInstanceOf(ServiceException.class);

        verify(platformClient, never()).exchangeCode(any());
        verify(accountMapper, never()).insert(any(OpcContentPlatformAccount.class));
    }

    /** Test 7 */
    @Test
    @DisplayName("listByCompany - 返回公司下全部账号")
    void listByCompany_returnsAllAccounts() {
        OpcContentPlatformAccount a = OpcContentPlatformAccount.builder().id(1L).openId("o1").build();
        when(accountMapper.selectListByCompany(COMPANY_ID)).thenReturn(List.of(a));

        List<OpcContentPlatformAccount> result = accountService.listByCompany(COMPANY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOpenId()).isEqualTo("o1");
    }

    /** Test 8 */
    @Test
    @DisplayName("listByCompany - 缺 companyId 抛 ServiceException")
    void listByCompany_blankCompanyId_throws() {
        assertThatThrownBy(() -> accountService.listByCompany(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
    }

    /** Test 9 */
    @Test
    @DisplayName("delete - ACTIVE 账号软删,status 置 REVOKED")
    void delete_activeAccount_succeeds() {
        OpcContentPlatformAccount existing = OpcContentPlatformAccount.builder()
                .id(ACCOUNT_ID).companyId(COMPANY_ID).status("ACTIVE").build();
        when(accountMapper.selectById(ACCOUNT_ID, COMPANY_ID)).thenReturn(existing);
        when(accountMapper.softDeleteById(ACCOUNT_ID, COMPANY_ID)).thenReturn(1);

        int rows = accountService.delete(ACCOUNT_ID, COMPANY_ID);

        assertThat(rows).isEqualTo(1);
        verify(accountMapper).softDeleteById(ACCOUNT_ID, COMPANY_ID);
    }

    /** Test 10 */
    @Test
    @DisplayName("refreshToken - ACTIVE + 有 refresh_token → 成功换新 token")
    void refreshToken_success() {
        OpcContentPlatformAccount existing = OpcContentPlatformAccount.builder()
                .id(ACCOUNT_ID).companyId(COMPANY_ID)
                .status("ACTIVE").openId("open_1")
                .refreshTokenEnc("old_refresh").build();
        when(accountMapper.selectById(ACCOUNT_ID, COMPANY_ID)).thenReturn(existing);
        long now = Instant.now().getEpochSecond();
        when(platformClient.refreshToken("old_refresh")).thenReturn(new PlatformClient.OAuthToken(
                "new_access", "new_refresh", now + 7200L, now + 30L * 86400L,
                "open_1", "video.create", "用户"));

        accountService.refreshToken(ACCOUNT_ID, COMPANY_ID);

        verify(accountMapper).updateTokens(eq(ACCOUNT_ID), eq(COMPANY_ID),
                eq("new_access"), eq("new_refresh"), any(), any());
    }
}