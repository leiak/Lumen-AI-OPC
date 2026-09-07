package com.ruoyi.opc.user.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.mapper.OpcCompanyProfileMapper;
import com.ruoyi.opc.user.mapper.OpcUserProfileMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link OpcUserProfileServiceImpl} 单元测试 — W3
 *
 * <p>覆盖 8 个 public 方法：
 * <ul>
 *   <li>User Profile：getByUserId / getByInvitationCode / createOrUpdate (状态机) / update</li>
 *   <li>Company Profile：listCompaniesByOwner / createCompany (字段初始化) / updateCompany / getCompany</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@code @RequiredArgsConstructor} + 2 个 {@code @Mock} mapper → Mockito 自动选 constructor injection</li>
 *   <li>{@code thenAnswer + AtomicLong} 模拟 MyBatis {@code useGeneratedKeys="true" keyProperty="id"}</li>
 *   <li>{@code ArgumentCaptor<OpcUserProfile>} / {@code ArgumentCaptor<OpcCompanyProfile>} 验证字段初始化</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcUserProfileServiceImplTest {

    @Mock
    private OpcUserProfileMapper userMapper;

    @Mock
    private OpcCompanyProfileMapper companyMapper;

    @InjectMocks
    private OpcUserProfileServiceImpl service;

    private static final AtomicLong NEXT_USER_ID = new AtomicLong(100L);
    private static final AtomicLong NEXT_COMPANY_ID = new AtomicLong(500L);

    private static final Long USER_ID = 2002L;
    private static final Long EXISTING_USER_ID = 2001L;
    private static final Long OWNER_USER_ID = 2002L;
    private static final String INVITE_CODE = "ABC234XY";
    private static final String COMPANY_CODE = "C1700000000123";

    // ==================== User Profile: 4 个 public 方法 ====================

    // ---------- getByUserId ----------

    @Test
    @DisplayName("getByUserId — 透传 userMapper.selectByUserId(userId)")
    void getByUserId_returnsProfile() {
        OpcUserProfile p = new OpcUserProfile();
        p.setId(1L);
        p.setUserId(USER_ID);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(p);

        OpcUserProfile result = service.getByUserId(USER_ID);

        assertSame(p, result);
        verify(userMapper).selectByUserId(USER_ID);
    }

    @Test
    @DisplayName("getByUserId — userMapper 返回 null → service 透传 null，不抛")
    void getByUserId_returnsNull() {
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);

        assertNull(service.getByUserId(USER_ID));
    }

    // ---------- getByInvitationCode ----------

    @Test
    @DisplayName("getByInvitationCode — 透传 userMapper.selectByInvitationCode(code)")
    void getByInvitationCode_returnsProfile() {
        OpcUserProfile p = new OpcUserProfile();
        p.setUserId(USER_ID);
        p.setInvitationCode(INVITE_CODE);
        when(userMapper.selectByInvitationCode(INVITE_CODE)).thenReturn(p);

        OpcUserProfile result = service.getByInvitationCode(INVITE_CODE);

        assertSame(p, result);
        verify(userMapper).selectByInvitationCode(INVITE_CODE);
    }

    @Test
    @DisplayName("getByInvitationCode — userMapper 返回 null → service 透传 null")
    void getByInvitationCode_returnsNull() {
        when(userMapper.selectByInvitationCode("NOPE")).thenReturn(null);

        assertNull(service.getByInvitationCode("NOPE"));
    }

    // ---------- createOrUpdate (state machine) ----------

    @Test
    @DisplayName("createOrUpdate — 新用户(userMapper 返回 null) → 插入：自动生成 invitationCode + status=ACTIVE + verified=0 + createBy=userId")
    void createOrUpdate_newProfile_inserts() {
        OpcUserProfile p = new OpcUserProfile();
        p.setUserId(USER_ID);
        p.setRealName("张三");
        // 不设置 invitationCode → 应自动生成
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);
        // 模拟 MyBatis useGeneratedKeys
        when(userMapper.insert(any(OpcUserProfile.class))).thenAnswer(inv -> {
            OpcUserProfile arg = inv.getArgument(0);
            arg.setId(NEXT_USER_ID.getAndIncrement());
            return 1;
        });

        Long resultId = service.createOrUpdate(p);

        ArgumentCaptor<OpcUserProfile> captor = ArgumentCaptor.forClass(OpcUserProfile.class);
        verify(userMapper).insert(captor.capture());
        OpcUserProfile inserted = captor.getValue();
        assertNotNull(inserted.getInvitationCode(), "invitationCode 应自动生成");
        assertEquals(8, inserted.getInvitationCode().length(), "邀请码长度应为 8");
        assertEquals("ACTIVE", inserted.getStatus(), "新用户 status 应为 ACTIVE");
        assertEquals(0, inserted.getVerified(), "新用户 verified 应为 0");
        assertEquals(String.valueOf(USER_ID), inserted.getCreateBy(),
                "createBy 应为当前 userId（无登录态时降级）");
        assertNotNull(resultId, "应返回新生成的 id");
        verify(userMapper, never()).update(any(OpcUserProfile.class));
    }

    @Test
    @DisplayName("createOrUpdate — 新用户但 invitationCode 已提供 → 保留原值，不自动生成")
    void createOrUpdate_newProfile_keepsProvidedCode() {
        OpcUserProfile p = new OpcUserProfile();
        p.setUserId(USER_ID);
        p.setInvitationCode("MYCODE99");  // 已提供
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);
        when(userMapper.insert(any(OpcUserProfile.class))).thenAnswer(inv -> {
            OpcUserProfile arg = inv.getArgument(0);
            arg.setId(NEXT_USER_ID.getAndIncrement());
            return 1;
        });

        service.createOrUpdate(p);

        ArgumentCaptor<OpcUserProfile> captor = ArgumentCaptor.forClass(OpcUserProfile.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("MYCODE99", captor.getValue().getInvitationCode(),
                "已提供 invitationCode 时不应覆盖");
    }

    @Test
    @DisplayName("createOrUpdate — 新用户 invitationCode=\"\"（空字符串）→ 视为未提供，自动生成（W5.1 mutation fix M6）")
    void createOrUpdate_newProfileEmptyInvitationCode_autoGenerates() {
        OpcUserProfile p = new OpcUserProfile();
        p.setUserId(USER_ID);
        p.setInvitationCode("");  // 空字符串（与 null 语义等价 — 应触发自动生成）
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);
        when(userMapper.insert(any(OpcUserProfile.class))).thenAnswer(inv -> {
            OpcUserProfile arg = inv.getArgument(0);
            arg.setId(NEXT_USER_ID.getAndIncrement());
            return 1;
        });

        service.createOrUpdate(p);

        ArgumentCaptor<OpcUserProfile> captor = ArgumentCaptor.forClass(OpcUserProfile.class);
        verify(userMapper).insert(captor.capture());
        OpcUserProfile inserted = captor.getValue();
        assertNotNull(inserted.getInvitationCode(), "空字符串 invitationCode 应触发自动生成");
        assertEquals(8, inserted.getInvitationCode().length(),
                "自动生成的 invitationCode 长度应为 8（与 null 等价行为）");
        assertNotEquals("", inserted.getInvitationCode(),
                "inserted invitationCode 不应仍是空字符串");
    }

    @Test
    @DisplayName("createOrUpdate — 已存在用户 → 更新：设置 id=exist.id + updateBy=userId，不改 status/verified")
    void createOrUpdate_existingProfile_updates() {
        OpcUserProfile exist = new OpcUserProfile();
        exist.setId(99L);
        exist.setUserId(EXISTING_USER_ID);
        exist.setStatus("ACTIVE");
        exist.setVerified(1);
        exist.setInvitationCode("OLDCODE1");

        OpcUserProfile incoming = new OpcUserProfile();
        incoming.setUserId(EXISTING_USER_ID);
        incoming.setRealName("李四更新");

        when(userMapper.selectByUserId(EXISTING_USER_ID)).thenReturn(exist);
        when(userMapper.update(any(OpcUserProfile.class))).thenReturn(1);

        Long resultId = service.createOrUpdate(incoming);

        ArgumentCaptor<OpcUserProfile> captor = ArgumentCaptor.forClass(OpcUserProfile.class);
        verify(userMapper).update(captor.capture());
        OpcUserProfile updated = captor.getValue();
        assertEquals(99L, updated.getId(), "更新时应保留原 id");
        assertEquals(String.valueOf(EXISTING_USER_ID), updated.getUpdateBy());
        assertEquals(99L, resultId, "应返回原 id");
        verify(userMapper, never()).insert(any(OpcUserProfile.class));
    }

    @Test
    @DisplayName("createOrUpdate — userId=null → OpcException，不调 mapper")
    void createOrUpdate_userIdNull_throws() {
        OpcUserProfile p = new OpcUserProfile();
        p.setUserId(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> service.createOrUpdate(p));
        assertTrue(ex.getMessage().contains("userId"));
        verify(userMapper, never()).selectByUserId(any());
        verify(userMapper, never()).insert(any());
        verify(userMapper, never()).update(any());
    }

    // ---------- update ----------

    @Test
    @DisplayName("update — 透传 userMapper.update，rows 直接返回")
    void update_returnsRows() {
        OpcUserProfile p = new OpcUserProfile();
        p.setId(99L);
        when(userMapper.update(p)).thenReturn(1);

        int rows = service.update(p);

        assertEquals(1, rows);
        verify(userMapper).update(p);
    }

    // ==================== Company Profile: 4 个 public 方法 ====================

    // ---------- listCompaniesByOwner ----------

    @Test
    @DisplayName("listCompaniesByOwner — 透传 companyMapper.selectByOwner(ownerId)")
    void listCompaniesByOwner_returnsList() {
        OpcCompanyProfile c1 = new OpcCompanyProfile();
        OpcCompanyProfile c2 = new OpcCompanyProfile();
        List<OpcCompanyProfile> list = Arrays.asList(c1, c2);
        when(companyMapper.selectByOwner(OWNER_USER_ID)).thenReturn(list);

        List<OpcCompanyProfile> result = service.listCompaniesByOwner(OWNER_USER_ID);

        assertSame(list, result);
        assertEquals(2, result.size());
        verify(companyMapper).selectByOwner(OWNER_USER_ID);
    }

    @Test
    @DisplayName("listCompaniesByOwner — 返回空列表（用户尚未创建公司）")
    void listCompaniesByOwner_emptyList() {
        when(companyMapper.selectByOwner(OWNER_USER_ID)).thenReturn(new ArrayList<>());

        List<OpcCompanyProfile> result = service.listCompaniesByOwner(OWNER_USER_ID);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ---------- createCompany (字段初始化) ----------

    @Test
    @DisplayName("createCompany — 新建公司：companyCode 自动生成(C 前缀) + status=NORMAL + verified=0 + scale=SMALL + createBy=ownerUserId")
    void createCompany_autoFillsFields() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setOwnerUserId(OWNER_USER_ID);
        c.setCompanyName("张三工作室");
        // 不设置 companyCode / scale → 应自动填充
        when(companyMapper.insert(any(OpcCompanyProfile.class))).thenAnswer(inv -> {
            OpcCompanyProfile arg = inv.getArgument(0);
            arg.setId(NEXT_COMPANY_ID.getAndIncrement());
            return 1;
        });

        Long resultId = service.createCompany(c);

        ArgumentCaptor<OpcCompanyProfile> captor = ArgumentCaptor.forClass(OpcCompanyProfile.class);
        verify(companyMapper).insert(captor.capture());
        OpcCompanyProfile inserted = captor.getValue();
        assertNotNull(inserted.getCompanyCode(), "companyCode 应自动生成");
        assertTrue(inserted.getCompanyCode().startsWith("C"),
                "companyCode 应以 C 开头，实际: " + inserted.getCompanyCode());
        assertEquals("NORMAL", inserted.getStatus());
        assertEquals(0, inserted.getVerified());
        assertEquals("SMALL", inserted.getScale(), "scale=null 时应默认 SMALL");
        assertEquals(String.valueOf(OWNER_USER_ID), inserted.getCreateBy());
        assertNotNull(resultId);
    }

    @Test
    @DisplayName("createCompany — companyCode 已提供 → 保留，不覆盖")
    void createCompany_keepsProvidedCompanyCode() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setOwnerUserId(OWNER_USER_ID);
        c.setCompanyCode(COMPANY_CODE);  // 已提供
        when(companyMapper.insert(any(OpcCompanyProfile.class))).thenAnswer(inv -> {
            OpcCompanyProfile arg = inv.getArgument(0);
            arg.setId(NEXT_COMPANY_ID.getAndIncrement());
            return 1;
        });

        service.createCompany(c);

        ArgumentCaptor<OpcCompanyProfile> captor = ArgumentCaptor.forClass(OpcCompanyProfile.class);
        verify(companyMapper).insert(captor.capture());
        assertEquals(COMPANY_CODE, captor.getValue().getCompanyCode(),
                "已提供 companyCode 时不应覆盖");
    }

    @Test
    @DisplayName("createCompany — scale 已提供 → 保留，不覆盖为 SMALL")
    void createCompany_keepsProvidedScale() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setOwnerUserId(OWNER_USER_ID);
        c.setScale("MEDIUM");  // 已提供
        when(companyMapper.insert(any(OpcCompanyProfile.class))).thenAnswer(inv -> {
            OpcCompanyProfile arg = inv.getArgument(0);
            arg.setId(NEXT_COMPANY_ID.getAndIncrement());
            return 1;
        });

        service.createCompany(c);

        ArgumentCaptor<OpcCompanyProfile> captor = ArgumentCaptor.forClass(OpcCompanyProfile.class);
        verify(companyMapper).insert(captor.capture());
        assertEquals("MEDIUM", captor.getValue().getScale(),
                "已提供 scale 时不应覆盖为 SMALL");
    }

    @Test
    @DisplayName("createCompany — ownerUserId=null → OpcException，不调 mapper")
    void createCompany_ownerUserIdNull_throws() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setOwnerUserId(null);
        c.setCompanyName("无名公司");

        OpcException ex = assertThrows(OpcException.class,
                () -> service.createCompany(c));
        assertTrue(ex.getMessage().contains("ownerUserId"));
        verify(companyMapper, never()).insert(any());
    }

    // ---------- updateCompany ----------

    @Test
    @DisplayName("updateCompany — 透传 companyMapper.update，rows 直接返回")
    void updateCompany_returnsRows() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setId(99L);
        when(companyMapper.update(c)).thenReturn(1);

        int rows = service.updateCompany(c);

        assertEquals(1, rows);
        verify(companyMapper).update(c);
    }

    // ---------- getCompany ----------

    @Test
    @DisplayName("getCompany — 透传 companyMapper.selectById(id)")
    void getCompany_returnsCompany() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setId(500L);
        c.setCompanyName("测试公司");
        when(companyMapper.selectById(500L)).thenReturn(c);

        OpcCompanyProfile result = service.getCompany(500L);

        assertSame(c, result);
        verify(companyMapper).selectById(500L);
    }

    @Test
    @DisplayName("getCompany — companyMapper 返回 null → service 透传 null")
    void getCompany_returnsNull() {
        when(companyMapper.selectById(999L)).thenReturn(null);

        assertNull(service.getCompany(999L));
    }
}