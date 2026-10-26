package com.ruoyi.opc.user.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.service.TokenService;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.user.domain.OpcCompanyBrief;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpcCompanySwitchServiceImpl} 单元测试 —— 多公司（多租户）切换
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code listMyCompanies} —— 成员关系口径、当前公司标记、userId 为 null 时空列表</li>
 *   <li>{@code switchCompany} —— 改写登录态、非成员拒绝、无登录上下文拒绝、空目标拒绝</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>成员关系表没有 mapper，service 走 JdbcTemplate → 这里 mock
 *       {@code queryForList(sql, userId)} 的返回值（列名大小写不敏感）</li>
 *   <li>{@code SecurityUtils.getLoginUser()} 是 static → {@code mockStatic}</li>
 *   <li>登录态改写落在 {@code TokenService.setLoginUser} → 用 captor 断言 companyId 变了</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcCompanySwitchServiceImplTest {

    private static final Long USER_ID = 2002L;
    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final Long COMPANY_FOREIGN = 999L;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private OpcCompanySwitchServiceImpl service;

    private MockedStatic<SecurityUtils> securityMock;

    @BeforeEach
    void setUp() {
        securityMock = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) {
            securityMock.close();
        }
    }

    /** 造一行「成员关系」查询结果 */
    private Map<String, Object> memberRow(Long companyId, String companyName, String role) {
        Map<String, Object> row = new HashMap<>();
        row.put("companyId", companyId);
        row.put("companyName", companyName);
        row.put("role", role);
        return row;
    }

    private void givenMemberCompanies(List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(new ArrayList<>(rows));
    }

    private void givenLoginUser(LoginUser loginUser) {
        securityMock.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
    }

    // ==================== listMyCompanies ====================

    @Test
    @DisplayName("listMyCompanies — 按成员关系返回公司，并标出当前所在的那家")
    void listMyCompanies_marksCurrent() {
        givenMemberCompanies(List.of(
                memberRow(COMPANY_A, "示例一人公司", "OWNER"),
                memberRow(COMPANY_B, "明轩电子商务", "STAFF")));

        List<OpcCompanyBrief> list = service.listMyCompanies(USER_ID, COMPANY_B);

        assertEquals(2, list.size());
        assertEquals(COMPANY_A, list.get(0).getId());
        assertEquals("示例一人公司", list.get(0).getCompanyName());
        assertEquals("OWNER", list.get(0).getRole());
        assertFalse(list.get(0).getCurrent(), "不是当前公司就不该标 current");
        assertTrue(list.get(1).getCurrent(), "当前公司要标 current");
    }

    @Test
    @DisplayName("listMyCompanies — userId 为 null → 空列表（不查库）")
    void listMyCompanies_nullUser_returnsEmpty() {
        List<OpcCompanyBrief> list = service.listMyCompanies(null, COMPANY_A);

        assertNotNull(list);
        assertTrue(list.isEmpty());
        verify(jdbcTemplate, never()).queryForList(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("listMyCompanies — 查询用的就是「在职成员 + 公司正常」那套口径")
    void listMyCompanies_usesMemberScopeSql() {
        givenMemberCompanies(List.of(memberRow(COMPANY_A, "示例一人公司", "OWNER")));

        service.listMyCompanies(USER_ID, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(USER_ID));
        String text = sql.getValue();
        assertTrue(text.contains("opc_company_member"), "必须按成员关系查，不是按 owner：" + text);
        assertTrue(text.contains("m.status = 'ACTIVE'"), "只认在职成员：" + text);
        assertTrue(text.contains("p.status = 'NORMAL'"), "只认正常的公司：" + text);
    }

    // ==================== switchCompany ====================

    @Test
    @DisplayName("switchCompany — 切到自己是成员的公司 → 改写登录态里的 companyId")
    void switchCompany_rewritesLoginState() {
        givenMemberCompanies(List.of(
                memberRow(COMPANY_A, "示例一人公司", "OWNER"),
                memberRow(COMPANY_B, "明轩电子商务", "STAFF")));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(USER_ID);
        loginUser.setCompanyId(COMPANY_A);
        givenLoginUser(loginUser);

        Long result = service.switchCompany(USER_ID, COMPANY_B);

        assertEquals(COMPANY_B, result);
        ArgumentCaptor<LoginUser> captor = ArgumentCaptor.forClass(LoginUser.class);
        verify(tokenService).setLoginUser(captor.capture());
        assertEquals(COMPANY_B, captor.getValue().getCompanyId(),
                "登录态里的 companyId 必须被改写成新公司");
    }

    @Test
    @DisplayName("switchCompany — 不是在职成员 → 403，且登录态一个字都不许动")
    void switchCompany_notMember_rejected() {
        givenMemberCompanies(List.of(memberRow(COMPANY_A, "示例一人公司", "OWNER")));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(USER_ID);
        loginUser.setCompanyId(COMPANY_A);
        givenLoginUser(loginUser);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.switchCompany(USER_ID, COMPANY_FOREIGN));

        assertEquals(403, ex.getCode());
        assertEquals(COMPANY_A, loginUser.getCompanyId(), "被拒绝时登录态不能变");
        verify(tokenService, never()).setLoginUser(any(LoginUser.class));
    }

    @Test
    @DisplayName("switchCompany — 目标为空 → 拒绝，不查库")
    void switchCompany_nullTarget_rejected() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.switchCompany(USER_ID, null));

        assertEquals(400, ex.getCode());
        verify(jdbcTemplate, never()).queryForList(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("switchCompany — 没有登录上下文 → 401")
    void switchCompany_noLoginContext_rejected() {
        givenMemberCompanies(List.of(memberRow(COMPANY_A, "示例一人公司", "OWNER")));
        givenLoginUser(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.switchCompany(USER_ID, COMPANY_A));

        assertEquals(401, ex.getCode());
        verify(tokenService, never()).setLoginUser(any(LoginUser.class));
    }

    @Test
    @DisplayName("switchCompany — userId 为 null → 401（不查库）")
    void switchCompany_nullUser_rejected() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.switchCompany(null, COMPANY_A));

        assertEquals(401, ex.getCode());
        verify(jdbcTemplate, never()).queryForList(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("switchCompany — 切到当前已经在的那家也算成功（幂等）")
    void switchCompany_sameCompany_isIdempotent() {
        givenMemberCompanies(List.of(memberRow(COMPANY_A, "示例一人公司", "OWNER")));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(USER_ID);
        loginUser.setCompanyId(COMPANY_A);
        givenLoginUser(loginUser);

        assertEquals(COMPANY_A, service.switchCompany(USER_ID, COMPANY_A));
        verify(tokenService).setLoginUser(loginUser);
    }

    @Test
    @DisplayName("listMyCompanies — 库里没有成员关系 → 空列表")
    void listMyCompanies_noRows_returnsEmpty() {
        givenMemberCompanies(List.of());

        List<OpcCompanyBrief> list = service.listMyCompanies(USER_ID, COMPANY_A);

        assertTrue(list.isEmpty());
    }

}
