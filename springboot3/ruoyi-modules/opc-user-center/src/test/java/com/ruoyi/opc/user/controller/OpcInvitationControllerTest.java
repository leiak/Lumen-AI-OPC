package com.ruoyi.opc.user.controller;

import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.user.controller.OpcInvitationController;
import com.ruoyi.opc.user.domain.OpcInvitation;
import com.ruoyi.opc.user.service.IOpcInvitationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcInvitationController} 单元测试 — W2.7
 *
 * <p>覆盖 4 个 endpoint：
 * <ul>
 *   <li>POST /generate — SecurityUtils.getUserId + service.generate</li>
 *   <li>GET / — SecurityUtils.getUserId + service.listByInviter</li>
 *   <li>GET /{code} — 公开（不调 SecurityUtils）+ service.getPublicByCode</li>
 *   <li>POST /accept — SecurityUtils.getUserId + body.get("code"/"mobile") + service.accept</li>
 * </ul>
 *
 * <p>关键模式：{@link SecurityUtils#getUserId} 是 static。本 controller 3 个 endpoint 调它（generate /
 * myInvitations / accept），getPublic 是公开 endpoint 不需要登录。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcInvitationControllerTest {

    @Mock
    private IOpcInvitationService invitationService;

    @InjectMocks
    private OpcInvitationController controller;

    private MockedStatic<SecurityUtils> securityMock;

    private static final Long USER_ID = 2002L;
    private static final Long INVITER_ID = 1001L;
    private static final String CODE = "ABC234XY";
    private static final String MOBILE = "13800001111";

    @BeforeEach
    void setupSecurityMock() {
        securityMock = mockStatic(SecurityUtils.class);
        securityMock.when(SecurityUtils::getUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void teardownSecurityMock() {
        if (securityMock != null) securityMock.close();
    }

    private OpcInvitation activeInvitation() {
        OpcInvitation inv = new OpcInvitation();
        inv.setId(1L);
        inv.setInviteCode(CODE);
        inv.setInviterId(INVITER_ID);
        inv.setMaxUses(1);
        inv.setUsedCount(0);
        inv.setExpireTime(new Date(System.currentTimeMillis() + 86_400_000L));
        inv.setStatus("ACTIVE");
        return inv;
    }

    // ==================== POST /generate ====================

    @Test
    @DisplayName("generate — SecurityUtils.getUserId + service.generate 透传，返回 success(invitation)")
    void generate_returnsFromService() {
        OpcInvitation inv = activeInvitation();
        when(invitationService.generate(USER_ID)).thenReturn(inv);

        AjaxResult result = controller.generate();

        assertEquals(200, result.get("code"));
        assertSame(inv, result.get("data"));
        verify(invitationService).generate(USER_ID);
        securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce());
    }

    @Test
    @DisplayName("generate — service 返回 null（如达到 50 上限）→ success(null)，不抛")
    void generate_serviceReturnsNull() {
        when(invitationService.generate(USER_ID)).thenReturn(null);

        AjaxResult result = controller.generate();

        assertEquals(200, result.get("code"));
        assertNull(result.get("data"));
    }

    @Test
    @DisplayName("generate — service 抛 OpcException（userId null 或已达 50 上限）→ 透传")
    void generate_serviceThrowsPropagates() {
        when(invitationService.generate(USER_ID))
                .thenThrow(new OpcException("最多持有 50 个有效邀请码"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.generate());
        assertTrue(ex.getMessage().contains("50"));
    }

    // ==================== GET / ====================

    @Test
    @DisplayName("myInvitations — SecurityUtils.getUserId + service.listByInviter 透传，返回 success(List)")
    void myInvitations_returnsList() {
        List<OpcInvitation> list = Arrays.asList(activeInvitation(), activeInvitation());
        when(invitationService.listByInviter(USER_ID)).thenReturn(list);

        AjaxResult result = controller.myInvitations();

        assertEquals(200, result.get("code"));
        assertSame(list, result.get("data"));
        verify(invitationService).listByInviter(USER_ID);
        securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce());
    }

    @Test
    @DisplayName("myInvitations — service 返回空 list → success([])")
    void myInvitations_emptyList() {
        when(invitationService.listByInviter(USER_ID)).thenReturn(new ArrayList<>());

        AjaxResult result = controller.myInvitations();

        assertEquals(200, result.get("code"));
        assertEquals(0, ((List<?>) result.get("data")).size());
    }

    // ==================== GET /{code} ====================

    @Test
    @DisplayName("getPublic — 公开 endpoint：不调 SecurityUtils.getUserId，service.getPublicByCode 透传")
    void getPublic_returnsMap() {
        Map<String, Object> mockResult = Map.of(
                "inviteCode", CODE,
                "inviter", Map.of("userId", INVITER_ID, "realName", "张三")
        );
        when(invitationService.getPublicByCode(CODE)).thenReturn(mockResult);

        AjaxResult result = controller.getPublic(CODE);

        assertEquals(200, result.get("code"));
        assertSame(mockResult, result.get("data"));
        verify(invitationService).getPublicByCode(CODE);
        // 公开 endpoint 不应调 SecurityUtils
        securityMock.verify(() -> SecurityUtils.getUserId(), never());
    }

    @Test
    @DisplayName("getPublic — service 抛 OpcException（邀请码不存在/已失效/已过期/已被使用）→ 透传")
    void getPublic_serviceThrowsPropagates() {
        when(invitationService.getPublicByCode("NOPE"))
                .thenThrow(new OpcException("邀请码不存在"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.getPublic("NOPE"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("getPublic — inviter profile 缺失 → service 返回 inviter=null（透传，不抛）")
    void getPublic_inviterProfileMissing() {
        Map<String, Object> mockResult = Map.of(
                "inviteCode", CODE,
                "expireTime", new Date(),
                "inviter", null
        );
        when(invitationService.getPublicByCode(CODE)).thenReturn(mockResult);

        AjaxResult result = controller.getPublic(CODE);

        assertEquals(200, result.get("code"));
        assertEquals(CODE, ((Map<?, ?>) result.get("data")).get("inviteCode"));
        assertNull(((Map<?, ?>) result.get("data")).get("inviter"));
    }

    // ==================== POST /accept ====================

    @Test
    @DisplayName("accept — 正常：body 含 code + mobile → service.accept(code, userId, mobile) → success(Map)")
    void accept_returnsMap() {
        Map<String, Object> mockResult = Map.of(
                "inviterId", INVITER_ID,
                "companyId", 5001L,
                "rewardAmount", new java.math.BigDecimal("50.00")
        );
        when(invitationService.accept(CODE, USER_ID, MOBILE)).thenReturn(mockResult);

        Map<String, String> body = new HashMap<>();
        body.put("code", CODE);
        body.put("mobile", MOBILE);

        AjaxResult result = controller.accept(body);

        assertEquals(200, result.get("code"));
        assertSame(mockResult, result.get("data"));
        verify(invitationService).accept(CODE, USER_ID, MOBILE);
        securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce());
    }

    @Test
    @DisplayName("accept — body 缺 mobile → service.accept(code, userId, null)")
    void accept_bodyMissingMobile() {
        Map<String, Object> mockResult = Map.of("inviterId", INVITER_ID);
        when(invitationService.accept(CODE, USER_ID, null)).thenReturn(mockResult);

        Map<String, String> body = new HashMap<>();
        body.put("code", CODE);
        // 没 put mobile

        AjaxResult result = controller.accept(body);

        assertEquals(200, result.get("code"));
        verify(invitationService).accept(CODE, USER_ID, null);
    }

    @Test
    @DisplayName("accept — body 缺 code → service.accept(null, userId, mobile)，异常由 service 抛")
    void accept_bodyMissingCode() {
        when(invitationService.accept(null, USER_ID, MOBILE))
                .thenThrow(new OpcException("邀请码不能为空"));

        Map<String, String> body = new HashMap<>();
        body.put("mobile", MOBILE);
        // 没 put code

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.accept(body));
        assertTrue(ex.getMessage().contains("邀请码不能为空"));
        verify(invitationService).accept(null, USER_ID, MOBILE);
    }

    @Test
    @DisplayName("accept — body 为空 Map → service.accept(null, userId, null)，异常由 service 抛")
    void accept_emptyBody() {
        when(invitationService.accept(null, USER_ID, null))
                .thenThrow(new OpcException("邀请码不能为空"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.accept(new HashMap<>()));
        assertTrue(ex.getMessage().contains("邀请码不能为空"));
    }

    @Test
    @DisplayName("accept — service 抛 OpcException（不能接受自己邀请/已失效等）→ 透传")
    void accept_serviceThrowsPropagates() {
        when(invitationService.accept(CODE, USER_ID, MOBILE))
                .thenThrow(new OpcException("不能接受自己的邀请"));

        Map<String, String> body = new HashMap<>();
        body.put("code", CODE);
        body.put("mobile", MOBILE);

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.accept(body));
        assertTrue(ex.getMessage().contains("不能接受自己"));
    }

    @Test
    @DisplayName("accept — mobile 字段值空字符串 '' 透传（service 自行处理）")
    void accept_mobileEmptyString() {
        Map<String, Object> mockResult = Map.of("inviterId", INVITER_ID);
        when(invitationService.accept(CODE, USER_ID, "")).thenReturn(mockResult);

        Map<String, String> body = new HashMap<>();
        body.put("code", CODE);
        body.put("mobile", "");

        AjaxResult result = controller.accept(body);

        assertEquals(200, result.get("code"));
        verify(invitationService).accept(CODE, USER_ID, "");
    }
}
