package com.ruoyi.opc.user.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.user.domain.OpcInvitation;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.mapper.OpcInvitationMapper;
import com.ruoyi.opc.user.mapper.OpcUserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcInvitationServiceImpl} 单元测试 — W2.2
 *
 * <p>覆盖 4 个公共方法的全部关键分支：
 * <ol>
 *   <li>{@code generate} — null userId / 上限 50 / 正常生成 / 重试 5 次仍冲突</li>
 *   <li>{@code listByInviter} — mapper 透传</li>
 *   <li>{@code getPublicByCode} — 不存在 / 已失效 / 已过期 / 已用完 / 正常（inviter profile 缺失）</li>
 *   <li>{@code accept} — null code / null invitee / 不存在 / 失效 / 过期 / 已用完 / 自己邀请自己 /
 *       新建 invitee profile / 已存在但 inviterId 为空 / 邀请人无公司 / 邀请人无钱包</li>
 * </ol>
 *
 * <p>不依赖 Spring 上下文。{@link JdbcTemplate} 通过 {@link ReflectionTestUtils} 注入（service 用
 * {@code @Autowired} 而非构造器注入，无法走 {@code @InjectMocks}）。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcInvitationServiceImplTest {

    @Mock
    private OpcInvitationMapper invitationMapper;

    @Mock
    private OpcUserProfileMapper userProfileMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OpcInvitationServiceImpl invitationService;

    private static final Long INVITER_ID = 1001L;
    private static final Long INVITEE_ID = 2002L;
    private static final String CODE = "ABC234XY";
    private static final String MOBILE = "13800001111";
    private static final Long COMPANY_ID = 5001L;

    @BeforeEach
    void injectJdbcTemplate() {
        // service 用 @Autowired（不是构造器）注入 jdbcTemplate，@InjectMocks 不会处理
        ReflectionTestUtils.setField(invitationService, "jdbcTemplate", jdbcTemplate);
    }

    private OpcInvitation activeInvite() {
        OpcInvitation inv = new OpcInvitation();
        inv.setId(1L);
        inv.setInviteCode(CODE);
        inv.setInviterId(INVITER_ID);
        inv.setMaxUses(1);
        inv.setUsedCount(0);
        inv.setExpireTime(new Date(System.currentTimeMillis() + 86_400_000L)); // +1 天
        inv.setStatus("ACTIVE");
        inv.setCreateTime(new Date());
        return inv;
    }

    private OpcUserProfile profileWithInviter(Long userId, Long inviterId) {
        OpcUserProfile p = new OpcUserProfile();
        p.setId(10L);
        p.setUserId(userId);
        p.setUserType("ENTREPRENEUR");
        p.setRealName("张三");
        p.setInviterId(inviterId);
        p.setStatus("ACTIVE");
        p.setVerified(0);
        return p;
    }

    // ==================== generate ====================

    @Test
    @DisplayName("generate — userId 为 null → 抛 OpcException")
    void generate_nullUserId_throws() {
        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.generate(null));
        assertTrue(ex.getMessage().contains("userId"));
    }

    @Test
    @DisplayName("generate — ACTIVE 数量 >= 50 → 抛「最多持有 50 个有效邀请码」")
    void generate_exceedsLimit_throws() {
        when(invitationMapper.countActiveByInviter(INVITER_ID)).thenReturn(50);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.generate(INVITER_ID));
        assertTrue(ex.getMessage().contains("50"));
        verify(invitationMapper, never()).insert(any(OpcInvitation.class));
    }

    @Test
    @DisplayName("generate — 正常路径：ACTIVE<50 + 第 1 个 code 无冲突 → 写入并返回 ACTIVE 邀请码")
    void generate_success() {
        when(invitationMapper.countActiveByInviter(INVITER_ID)).thenReturn(3);
        // 任何 code 都返回 null（无冲突）→ 第一次循环就 break
        when(invitationMapper.selectByCode(anyString())).thenReturn(null);

        OpcInvitation inv = invitationService.generate(INVITER_ID);

        assertNotNull(inv);
        assertEquals("ACTIVE", inv.getStatus());
        assertEquals(INVITER_ID, inv.getInviterId());
        assertEquals(Integer.valueOf(1), inv.getMaxUses());
        assertEquals(Integer.valueOf(0), inv.getUsedCount());
        assertNotNull(inv.getInviteCode());
        assertTrue(inv.getInviteCode().length() >= 6, "邀请码长度应足够");
        assertNotNull(inv.getExpireTime());
        // 有效期应在 now + 89d ~ 91d 之间（允许 90d）
        long deltaMs = inv.getExpireTime().getTime() - System.currentTimeMillis();
        assertTrue(deltaMs > 89L * 86_400_000L && deltaMs < 91L * 86_400_000L,
                "expireTime 应 ≈ now+90d，实际差值=" + deltaMs + "ms");

        ArgumentCaptor<OpcInvitation> captor = ArgumentCaptor.forClass(OpcInvitation.class);
        verify(invitationMapper).insert(captor.capture());
        assertEquals(String.valueOf(INVITER_ID), captor.getValue().getCreateBy());
    }

    @Test
    @DisplayName("generate — 5 次 code 都冲突 → 抛「生成邀请码失败，请重试」")
    void generate_allFiveRetriesConflict_throws() {
        when(invitationMapper.countActiveByInviter(INVITER_ID)).thenReturn(0);
        OpcInvitation existing = activeInvite();
        when(invitationMapper.selectByCode(anyString())).thenReturn(existing);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.generate(INVITER_ID));
        assertTrue(ex.getMessage().contains("生成邀请码失败"),
                "应报「生成邀请码失败」，实际: " + ex.getMessage());
        // selectByCode 恰好被调 5 次
        verify(invitationMapper, times(5)).selectByCode(anyString());
        verify(invitationMapper, never()).insert(any(OpcInvitation.class));
    }

    // ==================== listByInviter ====================

    @Test
    @DisplayName("listByInviter — mapper 透传，返回原列表")
    void listByInviter_passthrough() {
        List<OpcInvitation> list = new ArrayList<>();
        list.add(activeInvite());
        when(invitationMapper.selectByInviter(INVITER_ID)).thenReturn(list);

        List<OpcInvitation> result = invitationService.listByInviter(INVITER_ID);

        assertSame(list, result);
        verify(invitationMapper).selectByInviter(INVITER_ID);
    }

    // ==================== getPublicByCode ====================

    @Test
    @DisplayName("getPublicByCode — code 不存在 → 抛「邀请码不存在」")
    void getPublicByCode_notFound_throws() {
        when(invitationMapper.selectByCode("NOPE")).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.getPublicByCode("NOPE"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("getPublicByCode — status 非 ACTIVE → 抛「邀请码已失效」")
    void getPublicByCode_inactive_throws() {
        OpcInvitation inv = activeInvite();
        inv.setStatus("USED");
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.getPublicByCode(CODE));
        assertTrue(ex.getMessage().contains("失效"));
    }

    @Test
    @DisplayName("getPublicByCode — 已过期 → 抛「邀请码已过期」")
    void getPublicByCode_expired_throws() {
        OpcInvitation inv = activeInvite();
        inv.setExpireTime(new Date(System.currentTimeMillis() - 86_400_000L)); // 昨天
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.getPublicByCode(CODE));
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("getPublicByCode — usedCount >= maxUses → 抛「邀请码已被使用」")
    void getPublicByCode_maxUsesReached_throws() {
        OpcInvitation inv = activeInvite();
        inv.setUsedCount(1);
        inv.setMaxUses(1);
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.getPublicByCode(CODE));
        assertTrue(ex.getMessage().contains("已被使用"));
    }

    @Test
    @DisplayName("getPublicByCode — 正常：返回 inviteCode + expireTime + inviter 公开字段")
    void getPublicByCode_success() {
        when(invitationMapper.selectByCode(CODE)).thenReturn(activeInvite());
        when(userProfileMapper.selectByUserId(INVITER_ID))
                .thenReturn(profileWithInviter(INVITER_ID, null));

        Map<String, Object> result = invitationService.getPublicByCode(CODE);

        assertEquals(CODE, result.get("inviteCode"));
        assertNotNull(result.get("expireTime"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inviter = (Map<String, Object>) result.get("inviter");
        assertNotNull(inviter);
        assertEquals(INVITER_ID, inviter.get("userId"));
        assertEquals("张三", inviter.get("realName"));
    }

    @Test
    @DisplayName("getPublicByCode — inviter profile 缺失 → inviter 字段为 null，不抛异常")
    void getPublicByCode_inviterMissing_returnsNullInviter() {
        when(invitationMapper.selectByCode(CODE)).thenReturn(activeInvite());
        when(userProfileMapper.selectByUserId(INVITER_ID)).thenReturn(null);

        Map<String, Object> result = invitationService.getPublicByCode(CODE);

        assertEquals(CODE, result.get("inviteCode"));
        assertNull(result.get("inviter"), "inviter profile 缺失时 inviter 字段应为 null");
    }

    // ==================== accept ====================

    @Test
    @DisplayName("accept — code 为 null/空 → 抛「邀请码不能为空」")
    void accept_nullOrEmptyCode_throws() {
        assertThrows(OpcException.class,
                () -> invitationService.accept(null, INVITEE_ID, MOBILE));
        assertThrows(OpcException.class,
                () -> invitationService.accept("", INVITEE_ID, MOBILE));
    }

    @Test
    @DisplayName("accept — inviteeId 为 null → 抛「被邀请人 ID 不能为空」")
    void accept_nullInviteeId_throws() {
        assertThrows(OpcException.class,
                () -> invitationService.accept(CODE, null, MOBILE));
    }

    @Test
    @DisplayName("accept — 邀请码不存在 → 抛「邀请码不存在」")
    void accept_notFound_throws() {
        when(invitationMapper.selectByCode(CODE)).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.accept(CODE, INVITEE_ID, MOBILE));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("accept — status 非 ACTIVE → 抛「邀请码已失效」")
    void accept_inactive_throws() {
        OpcInvitation inv = activeInvite();
        inv.setStatus("DISABLED");
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.accept(CODE, INVITEE_ID, MOBILE));
        assertTrue(ex.getMessage().contains("失效"));
    }

    @Test
    @DisplayName("accept — 邀请码已过期 → 抛「邀请码已过期」")
    void accept_expired_throws() {
        OpcInvitation inv = activeInvite();
        inv.setExpireTime(new Date(System.currentTimeMillis() - 1000L));
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.accept(CODE, INVITEE_ID, MOBILE));
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("accept — usedCount >= maxUses → 抛「邀请码已被使用」")
    void accept_maxUsesReached_throws() {
        OpcInvitation inv = activeInvite();
        inv.setUsedCount(1);
        inv.setMaxUses(1);
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.accept(CODE, INVITEE_ID, MOBILE));
        assertTrue(ex.getMessage().contains("已被使用"));
    }

    @Test
    @DisplayName("accept — 自己邀请自己 → 抛「不能接受自己的邀请」")
    void accept_selfInvite_throws() {
        OpcInvitation inv = activeInvite();
        inv.setInviterId(INVITEE_ID); // inviter == invitee
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcException ex = assertThrows(OpcException.class,
                () -> invitationService.accept(CODE, INVITEE_ID, MOBILE));
        assertTrue(ex.getMessage().contains("不能接受自己"));
    }

    @Test
    @DisplayName("accept — 成功：新建 invitee profile + 邀请人有公司 + 邀请人有 wallet")
    void accept_success_newInviteeProfile_withCompanyAndWallet() {
        OpcInvitation inv = activeInvite();
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);
        // 被邀请人 profile 不存在
        when(userProfileMapper.selectByUserId(INVITEE_ID)).thenReturn(null);
        // 邀请人有一家默认公司
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(INVITER_ID)))
                .thenReturn(COMPANY_ID);
        // jdbcTemplate.update 默认返回 0（mockito stub），需要显式 stub
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = invitationService.accept(CODE, INVITEE_ID, MOBILE);

        assertEquals(INVITER_ID, result.get("inviterId"));
        assertEquals(COMPANY_ID, result.get("companyId"));
        assertEquals(0, ((java.math.BigDecimal) result.get("rewardAmount")).compareTo(
                new java.math.BigDecimal("50.00")));

        // 1) invitee profile 被新建（INSERT）
        ArgumentCaptor<OpcUserProfile> profileCaptor = ArgumentCaptor.forClass(OpcUserProfile.class);
        verify(userProfileMapper).insert(profileCaptor.capture());
        OpcUserProfile created = profileCaptor.getValue();
        assertEquals(INVITEE_ID, created.getUserId());
        assertEquals(INVITER_ID, created.getInviterId());
        assertEquals("ENTREPRENEUR", created.getUserType());
        assertEquals("ACTIVE", created.getStatus());

        // 2) company_member INSERT IGNORE 被调一次
        // 3) opc_transaction INSERT...SELECT + opc_wallet UPDATE 各一次
        //    → 总共 3 次 jdbcTemplate.update
        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
        // findFirstCompanyByOwner 被调一次
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Long.class), eq(INVITER_ID));

        // 4) invite.bindInvitee 被调，usedCount 增加到 1，status 变 USED
        ArgumentCaptor<OpcInvitation> invCaptor = ArgumentCaptor.forClass(OpcInvitation.class);
        verify(invitationMapper).bindInvitee(invCaptor.capture());
        OpcInvitation bound = invCaptor.getValue();
        assertEquals(INVITEE_ID, bound.getInviteeId());
        assertEquals(MOBILE, bound.getInviteeMobile());
        assertEquals(Integer.valueOf(1), bound.getUsedCount());
        assertEquals("USED", bound.getStatus());
    }

    @Test
    @DisplayName("accept — 成功：invitee profile 已存在且 inviterId 为空 → UPDATE 而非 INSERT")
    void accept_success_existingInviteeProfile_noInviterYet() {
        OpcInvitation inv = activeInvite();
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);

        OpcUserProfile existing = profileWithInviter(INVITEE_ID, null); // inviterId == null
        when(userProfileMapper.selectByUserId(INVITEE_ID)).thenReturn(existing);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenThrow(new EmptyResultDataAccessException(1)); // 邀请人没公司
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = invitationService.accept(CODE, INVITEE_ID, MOBILE);

        // invitee profile 被 UPDATE（不是 INSERT）
        verify(userProfileMapper, never()).insert(any(OpcUserProfile.class));
        ArgumentCaptor<OpcUserProfile> updateCaptor = ArgumentCaptor.forClass(OpcUserProfile.class);
        verify(userProfileMapper).update(updateCaptor.capture());
        assertEquals(INVITER_ID, updateCaptor.getValue().getInviterId());

        // 邀请人无公司 → companyId = null，jdbcTemplate.update 一次都不该调
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        assertNull(result.get("companyId"));
        assertEquals(INVITER_ID, result.get("inviterId"));
    }

    @Test
    @DisplayName("accept — 邀请人无公司 → 跳过 company_member + reward，只 bind 邀请码")
    void accept_inviterNoCompany_skipsReward() {
        OpcInvitation inv = activeInvite();
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);
        when(userProfileMapper.selectByUserId(INVITEE_ID)).thenReturn(null);
        // findFirstCompanyByOwner 抛 EmptyResultDataAccessException → 内部 catch → 返回 null
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenThrow(new EmptyResultDataAccessException(1));

        Map<String, Object> result = invitationService.accept(CODE, INVITEE_ID, MOBILE);

        // companyId=null → 不应有任何 jdbcTemplate.update
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        assertNull(result.get("companyId"));

        // invitee profile 仍被新建
        verify(userProfileMapper).insert(any(OpcUserProfile.class));
        // invite 仍被 bind
        verify(invitationMapper).bindInvitee(any(OpcInvitation.class));
    }

    @Test
    @DisplayName("accept — 邀请人有公司但 wallet 不存在 → tx INSERT...SELECT 影响 0 行，不抛异常")
    void accept_inviterHasCompanyNoWallet_noThrow() {
        OpcInvitation inv = activeInvite();
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);
        when(userProfileMapper.selectByUserId(INVITEE_ID)).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenReturn(COMPANY_ID);
        // company_member 插入成功 (1)，tx INSERT...SELECT 影响 0 行（无钱包），
        // wallet UPDATE 影响 0 行
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1)   // company_member
                .thenReturn(0)   // tx INSERT...SELECT
                .thenReturn(0);  // wallet UPDATE

        // 不应抛异常（实现 try/catch Exception + warn log）
        Map<String, Object> result = assertDoesNotThrow(() ->
                invitationService.accept(CODE, INVITEE_ID, MOBILE));

        assertEquals(COMPANY_ID, result.get("companyId"));
        // 三个 jdbcTemplate.update 都被调
        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("accept — maxUses>1（如 5）但只用了 1 次 → status 保持 ACTIVE")
    void accept_multiUse_inviteStillActive() {
        OpcInvitation inv = activeInvite();
        inv.setMaxUses(5);
        inv.setUsedCount(0);
        when(invitationMapper.selectByCode(CODE)).thenReturn(inv);
        when(userProfileMapper.selectByUserId(INVITEE_ID)).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenThrow(new EmptyResultDataAccessException(1));

        invitationService.accept(CODE, INVITEE_ID, MOBILE);

        ArgumentCaptor<OpcInvitation> captor = ArgumentCaptor.forClass(OpcInvitation.class);
        verify(invitationMapper).bindInvitee(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getUsedCount());
        assertEquals("ACTIVE", captor.getValue().getStatus(),
                "usedCount(1) < maxUses(5) → status 应保持 ACTIVE");
    }
}
