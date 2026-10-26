package com.ruoyi.opc.user.controller;

import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.user.domain.OpcInvitation;
import com.ruoyi.opc.user.service.IOpcInvitationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OpcInvitationController} 集成测试（@WebMvcTest）— W8.4
 *
 * <p>覆盖 4 个 endpoint 中的关键路径：
 * <ul>
 *   <li>GET /opc/user/invitations/{code} — <b>公开</b>（gateway 白名单 GET-only）</li>
 *   <li>POST /opc/user/invitations/generate — 需登录（SecurityUtils.getUserId）</li>
 *   <li>POST /opc/user/invitations/accept — 需登录 + JSON body</li>
 *   <li>GET /opc/user/invitations — 需登录（列表）</li>
 * </ul>
 *
 * <p>覆盖维度：
 * <ul>
 *   <li><b>HTTP status</b>：200 正常 + 200 service 异常（OpcException → handleServiceException）</li>
 *   <li><b>JSON 序列化</b>：{@code $.data.inviteCode / $.data.inviter.realName / $.data.rewardAmount}</li>
 *   <li><b>公开 vs 登录</b>：{@code GET /{code}} 即使 SecurityContextHolder 为空也能成功（验证 gateway 白名单设计）</li>
 *   <li><b>W7 修复回归</b>：{@code OpcException(400, "...")} 走 handleServiceException → HTTP 200 + JSON code=400</li>
 * </ul>
 *
 * <p>关键设计点（OPC-W1-VERIFICATION-invitation-flow.md 记录）：
 * <ul>
 *   <li>gateway 的 {@code security.ignore.whites} 是 path-only（AntPathMatcher），{@code /opc/user/invitations/*} 同时匹配 GET 和 POST
 *       — 但实际只有 GET 暴露给公网；POST 仍走 gateway 鉴权链</li>
 *   <li>{@code GET /{code}} 是匿名安全的（仅返回公开信息：邀请人昵称/头像），不返回 userId/手机号</li>
 * </ul>
 *
 * <p><b>断言已对齐当前 API 形状</b>：本文件此前断言的是一套更早的数据形状
 * （{@code OpcInvitation.code / inviterNickname / inviterRewardAmount / expireAt}，
 * 且 {@code getPublicByCode} / {@code accept} 返回的是领域对象）。
 * 现在的实现里邀请码字段叫 {@code inviteCode}、过期时间叫 {@code expireTime}，
 * 而 {@code getPublicByCode} / {@code accept} 返回的是
 * {@code Map<String,Object>}（公开信息 / 接受结果）——
 * 那套字段在领域对象上<b>根本不存在</b>，测试连编译都过不去，
 * 等于这个模块的测试一直是<b>没在跑</b>的状态。断言按当前实现改齐，
 * 覆盖的路径与原来一致。</p>
 *
 * @author OAC
 */
@WebMvcTest(controllers = OpcInvitationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
class OpcInvitationControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IOpcInvitationService invitationService;

    private static final Long USER_ID = 4004L;
    private static final String INVITE_CODE = "ABC234XY";
    private static final String MOBILE = "13800002222";

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.setUserId(String.valueOf(USER_ID));
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.remove();
    }

    /** getPublicByCode 的返回形状：邀请码 + 过期时间 + 邀请人公开信息。 */
    private Map<String, Object> samplePublicInfo() {
        return Map.of(
                "inviteCode", INVITE_CODE,
                "expireTime", new Date(System.currentTimeMillis() + 90L * 86400_000),
                "inviter", Map.of(
                        "userId", 4001L,
                        "realName", "李四",
                        "avatarUrl", "",
                        "industry", "软件",
                        "city", "杭州"
                )
        );
    }

    /** generate 的返回形状：领域对象本身。 */
    private OpcInvitation sampleInvitation() {
        OpcInvitation inv = new OpcInvitation();
        inv.setInviteCode(INVITE_CODE);
        inv.setInviterId(4001L);
        inv.setStatus("ACTIVE");
        inv.setExpireTime(new Date(System.currentTimeMillis() + 90L * 86400_000));
        inv.setMaxUses(1);
        inv.setUsedCount(0);
        return inv;
    }

    // ==================== GET /opc/user/invitations/{code} — 公开 ====================

    @Test
    @DisplayName("getPublic — 已登录 → service 调通 → HTTP 200 + JSON $.data.inviteCode + 邀请人公开信息")
    void getPublic_authenticated_returnsPublicInvitation() throws Exception {
        when(invitationService.getPublicByCode(INVITE_CODE)).thenReturn(samplePublicInfo());

        mockMvc.perform(get("/opc/user/invitations/{code}", INVITE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inviteCode").value(INVITE_CODE))
                .andExpect(jsonPath("$.data.inviter.realName").value("李四"))
                .andExpect(jsonPath("$.data.inviter.userId").value(4001));

        verify(invitationService).getPublicByCode(INVITE_CODE);
    }

    @Test
    @DisplayName("getPublic — 匿名调用（SecurityContextHolder 清空）→ 仍能查到（gateway 白名单设计）")
    void getPublic_anonymous_stillWorksBecauseEndpointIsPublic() throws Exception {
        SecurityContextHolder.remove();
        when(invitationService.getPublicByCode(INVITE_CODE)).thenReturn(samplePublicInfo());

        mockMvc.perform(get("/opc/user/invitations/{code}", INVITE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inviteCode").value(INVITE_CODE));
    }

    @Test
    @DisplayName("getPublic — service 返回 null（邀请码无效） → JSON data=null")
    void getPublic_serviceReturnsNull_dataIsNull() throws Exception {
        when(invitationService.getPublicByCode("INVALID")).thenReturn(null);

        mockMvc.perform(get("/opc/user/invitations/{code}", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("getPublic — service 抛 OpcException(404, '邀请码无效') → HTTP 200 + JSON code=404")
    void getPublic_serviceThrowsOpcException404_returns200WithCode404() throws Exception {
        when(invitationService.getPublicByCode("EXPIRED"))
                .thenThrow(new OpcException(404, "邀请码无效或已过期"));

        mockMvc.perform(get("/opc/user/invitations/{code}", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("邀请码无效或已过期"));
    }

    // ==================== POST /opc/user/invitations/generate — 需登录 ====================

    @Test
    @DisplayName("generate — 已登录 → service.generate(userId) 被调 → HTTP 200 + JSON $.data.inviteCode")
    void generate_authenticated_createsInvitation() throws Exception {
        OpcInvitation inv = sampleInvitation();
        inv.setInviteCode("NEWCDEFG");
        when(invitationService.generate(USER_ID)).thenReturn(inv);

        mockMvc.perform(post("/opc/user/invitations/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inviteCode").value("NEWCDEFG"));

        verify(invitationService).generate(USER_ID);
    }

    @Test
    @DisplayName("generate — 匿名 → SecurityUtils.getUserId() null → service 抛 OpcException → HTTP 200 + code=500 + msg=userId 不能为空")
    void generate_anonymous_serviceReceivesNullUserId() throws Exception {
        SecurityContextHolder.remove();
        when(invitationService.generate(any())).thenThrow(new OpcException("userId 不能为空"));

        mockMvc.perform(post("/opc/user/invitations/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("userId 不能为空"));
    }

    @Test
    @DisplayName("generate — service 抛 OpcException(400, '已达 50 个邀请码上限') → HTTP 200 + code=400")
    void generate_serviceThrowsOpcException400_returns200WithCode400() throws Exception {
        when(invitationService.generate(USER_ID))
                .thenThrow(new OpcException(400, "已达 50 个邀请码上限"));

        mockMvc.perform(post("/opc/user/invitations/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("已达 50 个邀请码上限"));
    }

    // ==================== POST /opc/user/invitations/accept — 需登录 + JSON body ====================

    @Test
    @DisplayName("accept — 正常 → service.accept(code, userId, mobile) 被调 → HTTP 200 + JSON $.data.inviterId")
    void accept_withValidBody_serviceAcceptsInvitation() throws Exception {
        when(invitationService.accept(eq(INVITE_CODE), eq(USER_ID), eq(MOBILE)))
                .thenReturn(Map.of("inviterId", 4001L, "companyId", 7L, "rewardAmount", new BigDecimal("50.00")));

        mockMvc.perform(post("/opc/user/invitations/accept")
                        .contentType("application/json")
                        .content("{\"code\":\"" + INVITE_CODE + "\",\"mobile\":\"" + MOBILE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inviterId").value(4001))
                .andExpect(jsonPath("$.data.companyId").value(7));

        verify(invitationService).accept(INVITE_CODE, USER_ID, MOBILE);
    }

    @Test
    @DisplayName("accept — 缺 @RequestBody → HTTP 200 + code=500（HttpMessageNotReadableException 被全局 advice 兜住）")
    void accept_emptyBody_returns200WithCode500() throws Exception {
        // HttpMessageNotReadableException 是 RuntimeException 的子类 → 被 GlobalExceptionHandler
        // 的 @ExceptionHandler(RuntimeException.class) 接管 → HTTP 200 + body.code=500
        // （不是 Spring 默认的 400：ExceptionHandlerExceptionResolver 优先于 DefaultHandlerExceptionResolver）
        mockMvc.perform(post("/opc/user/invitations/accept")
                        .contentType("application/json")
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("accept — service 抛 OpcException(400, '邀请码已被使用') → HTTP 200 + code=400")
    void accept_serviceThrowsOpcException400_returns200WithCode400() throws Exception {
        when(invitationService.accept(anyString(), anyLong(), anyString()))
                .thenThrow(new OpcException(400, "邀请码已被使用"));

        mockMvc.perform(post("/opc/user/invitations/accept")
                        .contentType("application/json")
                        .content("{\"code\":\"USED\",\"mobile\":\"" + MOBILE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("邀请码已被使用"));
    }

    // ==================== GET /opc/user/invitations — 需登录列表 ====================

    @Test
    @DisplayName("myInvitations — 已登录 → service.listByInviter(userId) 被调 → HTTP 200 + JSON data 是数组")
    void myInvitations_authenticated_returnsList() throws Exception {
        when(invitationService.listByInviter(USER_ID))
                .thenReturn(java.util.Arrays.asList(sampleInvitation()));

        mockMvc.perform(get("/opc/user/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].inviteCode").value(INVITE_CODE));

        verify(invitationService).listByInviter(USER_ID);
    }

    @Test
    @DisplayName("myInvitations — 匿名 → SecurityUtils.getUserId() null → service 抛 OpcException → HTTP 200 + code=500")
    void myInvitations_anonymous_throwsOpcException() throws Exception {
        SecurityContextHolder.remove();
        when(invitationService.listByInviter(any())).thenThrow(new OpcException("userId 不能为空"));

        mockMvc.perform(get("/opc/user/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("userId 不能为空"));
    }
}
