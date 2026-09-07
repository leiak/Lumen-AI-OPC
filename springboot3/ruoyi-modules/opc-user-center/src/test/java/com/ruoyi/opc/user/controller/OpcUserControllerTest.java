package com.ruoyi.opc.user.controller;

import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.service.IOpcUserProfileService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcUserController} 单元测试 — W3.3
 *
 * <p>注：原计划 W3.3 是 {@code OpcUserProfileController}，但该 controller 不存在 — 实际承载
 * profile / company endpoint 的是 {@code OpcUserController}（{@code /opc/user/**} 路径下）。本测试
 * 类覆盖其 7 个 endpoint：
 * <ul>
 *   <li>GET  /profile    — myProfile (auto-create empty profile if null)</li>
 *   <li>POST /profile    — saveProfile (override userId from SecurityUtils)</li>
 *   <li>GET  /companies  — myCompanies</li>
 *   <li>POST /company    — createCompany (override ownerUserId from SecurityUtils)</li>
 *   <li>GET  /company/{id} — getCompany</li>
 *   <li>PUT  /company    — updateCompany (rows>0 → true)</li>
 *   <li>GET  /home       — home (聚合 profile + companies)</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@link SecurityUtils#getUserId} 是 static (Long) — 与 W2.6 / W2.7 同模式</li>
 *   <li>Controller 在调 service 前必须 override userId/ownerUserId — 防止客户端伪造身份</li>
 *   <li>{@code myProfile} 在 service 返回 null 时自动创建空 profile（带 userId）</li>
 *   <li>{@code updateCompany} 把 {@code rows>0} 转 Boolean（{@code success(true/false)}，HTTP 仍 200）</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcUserControllerTest {

    @Mock
    private IOpcUserProfileService userProfileService;

    @InjectMocks
    private OpcUserController controller;

    private MockedStatic<SecurityUtils> securityMock;

    private static final Long USER_ID = 2002L;
    private static final Long COMPANY_ID = 1001L;
    private static final Long NEW_COMPANY_ID = 9999L;

    @BeforeEach
    void setupSecurityMock() {
        securityMock = mockStatic(SecurityUtils.class);
        securityMock.when(SecurityUtils::getUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void teardownSecurityMock() {
        if (securityMock != null) securityMock.close();
    }

    private OpcUserProfile sampleProfile() {
        OpcUserProfile p = new OpcUserProfile();
        p.setId(1L);
        p.setUserId(USER_ID);
        p.setRealName("张三");
        p.setMobile("13800001111");
        p.setIndustry("科技");
        p.setInvitationCode("ABC234XY");
        p.setStatus("ACTIVE");
        p.setVerified(0);
        p.setCreateTime(new Date());
        return p;
    }

    private OpcCompanyProfile sampleCompany() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setId(500L);
        c.setCompanyCode("C1700000000123");
        c.setOwnerUserId(USER_ID);
        c.setCompanyName("张三工作室");
        c.setIndustryCode("I51");
        c.setIndustryName("软件和信息技术服务业");
        c.setRegisteredCapital(new BigDecimal("100000.00"));
        c.setScale("SMALL");
        c.setVerified(0);
        c.setStatus("NORMAL");
        return c;
    }

    // ==================== GET /profile ====================

    @Test
    @DisplayName("myProfile — SecurityUtils.getUserId + service.getByUserId，返回 success(profile)")
    void myProfile_returnsProfileFromService() {
        OpcUserProfile p = sampleProfile();
        when(userProfileService.getByUserId(USER_ID)).thenReturn(p);

        AjaxResult result = controller.myProfile();

        assertEquals(200, result.get("code"));
        assertSame(p, result.get("data"));
        verify(userProfileService).getByUserId(USER_ID);
        securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce());
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("myProfile — service 返回 null → 自动创建空 profile（userId 已 set），不抛")
    void myProfile_nullReturnsEmptyProfileWithUserId() {
        when(userProfileService.getByUserId(USER_ID)).thenReturn(null);

        AjaxResult result = controller.myProfile();

        assertEquals(200, result.get("code"));
        OpcUserProfile data = (OpcUserProfile) result.get("data");
        assertNotNull(data, "空 profile 不应为 null");
        assertEquals(USER_ID, data.getUserId(),
                "空 profile 应携带当前 userId（前端用）");
        assertNull(data.getRealName(), "空 profile 不应携带真实姓名");
        verify(userProfileService).getByUserId(USER_ID);
        verifyNoMoreInteractions(userProfileService);
    }

    // ==================== POST /profile ====================

    @Test
    @DisplayName("saveProfile — 必须 override userId（防止客户端伪造身份），调 service.createOrUpdate")
    void saveProfile_overridesUserIdAndCallsCreateOrUpdate() {
        OpcUserProfile incoming = new OpcUserProfile();
        incoming.setUserId(9999L);  // 客户端伪造的 userId
        incoming.setRealName("李四");
        incoming.setMobile("13900002222");

        controller.saveProfile(incoming);

        assertEquals(USER_ID, incoming.getUserId(),
                "Controller 必须用 SecurityUtils 的 userId 覆盖客户端值");
        verify(userProfileService).createOrUpdate(incoming);
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("saveProfile — 返回 success(profile)，profile 是更新后的对象")
    void saveProfile_returnsUpdatedProfile() {
        OpcUserProfile incoming = new OpcUserProfile();
        incoming.setRealName("李四");

        AjaxResult result = controller.saveProfile(incoming);

        assertEquals(200, result.get("code"));
        assertSame(incoming, result.get("data"),
                "Controller 应返回修改后的 profile 对象（含正确的 userId）");
        assertEquals(USER_ID, ((OpcUserProfile) result.get("data")).getUserId());
        verify(userProfileService).createOrUpdate(incoming);
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("saveProfile — service 抛 OpcException（userId null 等）→ 透传")
    void saveProfile_serviceThrowsPropagates() {
        OpcUserProfile incoming = new OpcUserProfile();
        when(userProfileService.createOrUpdate(any()))
                .thenThrow(new OpcException("userId 不能为空"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.saveProfile(incoming));
        assertTrue(ex.getMessage().contains("userId"));
        verifyNoMoreInteractions(userProfileService);
    }

    // ==================== GET /companies ====================

    @Test
    @DisplayName("myCompanies — SecurityUtils.getUserId + service.listCompaniesByOwner，返回 success(List)")
    void myCompanies_returnsList() {
        List<OpcCompanyProfile> list = Arrays.asList(sampleCompany(), sampleCompany());
        when(userProfileService.listCompaniesByOwner(USER_ID)).thenReturn(list);

        AjaxResult result = controller.myCompanies();

        assertEquals(200, result.get("code"));
        assertSame(list, result.get("data"));
        assertEquals(2, ((List<?>) result.get("data")).size());
        verify(userProfileService).listCompaniesByOwner(USER_ID);
        securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce());
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("myCompanies — service 返回空 list（用户未创建公司）→ success([])")
    void myCompanies_emptyList() {
        when(userProfileService.listCompaniesByOwner(USER_ID)).thenReturn(new ArrayList<>());

        AjaxResult result = controller.myCompanies();

        assertEquals(200, result.get("code"));
        assertEquals(0, ((List<?>) result.get("data")).size());
        verifyNoMoreInteractions(userProfileService);
    }

    // ==================== POST /company ====================

    @Test
    @DisplayName("createCompany — 必须 override ownerUserId（防止越权创建他人公司）")
    void createCompany_overridesOwnerUserId() {
        OpcCompanyProfile incoming = new OpcCompanyProfile();
        incoming.setOwnerUserId(9999L);  // 客户端伪造
        incoming.setCompanyName("李四工作室");
        when(userProfileService.createCompany(any())).thenReturn(NEW_COMPANY_ID);

        controller.createCompany(incoming);

        assertEquals(USER_ID, incoming.getOwnerUserId(),
                "Controller 必须用 SecurityUtils 的 userId 作为 ownerUserId");
        verify(userProfileService).createCompany(incoming);
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("createCompany — 返回 success({companyId})")
    void createCompany_returnsCompanyIdInMap() {
        OpcCompanyProfile incoming = new OpcCompanyProfile();
        incoming.setCompanyName("新公司");
        when(userProfileService.createCompany(any())).thenReturn(NEW_COMPANY_ID);

        AjaxResult result = controller.createCompany(incoming);

        assertEquals(200, result.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNotNull(data);
        assertEquals(NEW_COMPANY_ID, data.get("companyId"));
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("createCompany — service 抛 OpcException（ownerUserId null）→ 透传")
    void createCompany_serviceThrowsPropagates() {
        OpcCompanyProfile incoming = new OpcCompanyProfile();
        when(userProfileService.createCompany(any()))
                .thenThrow(new OpcException("ownerUserId 不能为空"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.createCompany(incoming));
        assertTrue(ex.getMessage().contains("ownerUserId"));
        verifyNoMoreInteractions(userProfileService);
    }

    // ==================== GET /company/{id} ====================

    @Test
    @DisplayName("getCompany — 透传 id 到 service.getCompany，返回 success(Company)")
    void getCompany_returnsCompany() {
        OpcCompanyProfile c = sampleCompany();
        when(userProfileService.getCompany(COMPANY_ID)).thenReturn(c);

        AjaxResult result = controller.getCompany(COMPANY_ID);

        assertEquals(200, result.get("code"));
        assertSame(c, result.get("data"));
        verify(userProfileService).getCompany(COMPANY_ID);
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("getCompany — service 返回 null → success(null)，不抛")
    void getCompany_notFoundReturnsNull() {
        when(userProfileService.getCompany(9999L)).thenReturn(null);

        AjaxResult result = controller.getCompany(9999L);

        assertEquals(200, result.get("code"));
        assertNull(result.get("data"));
        verifyNoMoreInteractions(userProfileService);
    }

    // ==================== PUT /company ====================

    @Test
    @DisplayName("updateCompany — service.update 返回 1 → success(true)")
    void updateCompany_rowsGreaterThanZeroReturnsTrue() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setId(COMPANY_ID);
        when(userProfileService.updateCompany(c)).thenReturn(1);

        AjaxResult result = controller.updateCompany(c);

        assertEquals(200, result.get("code"));
        assertEquals(Boolean.TRUE, result.get("data"),
                "rows=1 应映射为 success(true)");
        verify(userProfileService).updateCompany(c);
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("updateCompany — service.update 返回 0 → success(false)，HTTP 仍 200")
    void updateCompany_rowsZeroReturnsFalse() {
        OpcCompanyProfile c = new OpcCompanyProfile();
        c.setId(COMPANY_ID);
        when(userProfileService.updateCompany(c)).thenReturn(0);

        AjaxResult result = controller.updateCompany(c);

        assertEquals(200, result.get("code"),
                "rows=0 不应抛 HTTP 500，应返回 success(false) 让前端判断");
        assertEquals(Boolean.FALSE, result.get("data"));
        verifyNoMoreInteractions(userProfileService);
    }

    // ==================== GET /home ====================

    @Test
    @DisplayName("home — 聚合 profile + companies 到 data map（含 profile 和 companies 两个 key）")
    void home_aggregatesProfileAndCompanies() {
        OpcUserProfile p = sampleProfile();
        List<OpcCompanyProfile> companies = Arrays.asList(sampleCompany());
        when(userProfileService.getByUserId(USER_ID)).thenReturn(p);
        when(userProfileService.listCompaniesByOwner(USER_ID)).thenReturn(companies);

        AjaxResult result = controller.home();

        assertEquals(200, result.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNotNull(data);
        assertSame(p, data.get("profile"));
        assertSame(companies, data.get("companies"));
        verify(userProfileService).getByUserId(USER_ID);
        verify(userProfileService).listCompaniesByOwner(USER_ID);
        securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce());
        verifyNoMoreInteractions(userProfileService);
    }

    @Test
    @DisplayName("home — profile 为 null 时透传（前端处理未创建场景）")
    void home_nullProfilePassesThrough() {
        when(userProfileService.getByUserId(USER_ID)).thenReturn(null);
        when(userProfileService.listCompaniesByOwner(USER_ID)).thenReturn(new ArrayList<>());

        AjaxResult result = controller.home();

        assertEquals(200, result.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNull(data.get("profile"),
                "service 返回 null → controller 透传 null（不像 myProfile 自动建空）");
        assertNotNull(data.get("companies"));
        verifyNoMoreInteractions(userProfileService);
    }
}