package com.ruoyi.common.security.tenant;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TenantGuard} 契约测试 —— 3b 跨租户读
 *
 * <p>守卫要守住的那条线是：<b>客户端说的 companyId 不算数</b>。
 * 端点普遍把 companyId 直接当查询条件交给 SQL
 * （{@code selectList(companyId, ...)}、{@code validateAndGet(id, companyId)}），
 * 租户隔离做在 service/mapper 层 —— 但那个 companyId 是客户端给的，
 * 于是「把参数从 1 改成 2」就等于「看别家公司的数据」。</p>
 *
 * @author OAC
 */
class TenantGuardTest
{
    private final TenantProperties properties = new TenantProperties();

    private TenantGuard guard()
    {
        return new TenantGuard(properties);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    private void loginAs(long userId, Long companyId)
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setCompanyId(companyId);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        SecurityContextHolder.setUserId(String.valueOf(userId));
    }

    @Test
    @DisplayName("已登录 → 作用域是自己的公司")
    void loggedIn_scopeIsOwnCompany()
    {
        loginAs(42L, 7L);

        assertEquals(7L, guard().effectiveCompanyId());
    }

    @Test
    @DisplayName("客户端指定别家公司 → 改写成自己的公司(不拒绝)")
    void loggedIn_otherCompanyIsOverridden()
    {
        loginAs(42L, 7L);

        assertEquals(7L, guard().resolveAllowed(999L),
                "客户端无权决定租户; 也不能因为它填错就让请求失败");
    }

    @Test
    @DisplayName("客户端没给 companyId → 用自己的公司补上")
    void loggedIn_missingCompanyIdIsFilled()
    {
        loginAs(42L, 7L);

        assertEquals(7L, guard().resolveAllowed(null));
    }

    @Test
    @DisplayName("客户端指定的就是自己的公司 → 原样通过")
    void loggedIn_sameCompanyPassesThrough()
    {
        loginAs(42L, 7L);

        assertEquals(7L, guard().resolveAllowed(7L));
    }

    @Test
    @DisplayName("匿名 → 作用域是配置的公开租户, 不能由客户端挑")
    void anonymous_scopeIsPublicCompany()
    {
        assertEquals(1L, guard().effectiveCompanyId(),
                "匿名公开浏览必须有明确的那一家, 否则匿名可以遍历所有租户");
    }

    @Test
    @DisplayName("匿名指定非公开租户 → 改写成公开租户")
    void anonymous_otherCompanyIsOverridden()
    {
        assertEquals(1L, guard().resolveAllowed(2L));
    }

    @Test
    @DisplayName("匿名 → 服务端补上公开租户, 公开浏览照常工作")
    void anonymous_missingCompanyIdIsFilled()
    {
        assertEquals(1L, guard().resolveAllowed(null));
    }

    @Test
    @DisplayName("公开租户可配置 (opc.tenant.public-company-id)")
    void publicCompanyIsConfigurable()
    {
        properties.setPublicCompanyId(3L);

        assertEquals(3L, guard().effectiveCompanyId());
    }

    @Test
    @DisplayName("登录了但没加入任何公司 + 客户端指定了公司 → 403(fail-closed)")
    void companylessUserRequestingCompanyIsRejected()
    {
        loginAs(42L, null);

        ServiceException e = assertThrows(ServiceException.class, () -> guard().resolveAllowed(2L),
                "没有作用域可用时, 点名要某家公司就是越权, 必须拒绝");
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("登录了但没加入任何公司 + 客户端没指定 → 作用域为 null(不用 0 兜底)")
    void companylessUserWithoutRequestIsNull()
    {
        loginAs(42L, null);

        assertNull(guard().resolveAllowed(null),
                "用 0 兜底会把'无公司'伪装成'公司0', 继续查库即越权");
    }

    @Test
    @DisplayName("守卫被关掉 → 客户端的值原样放行(紧急止血开关)")
    void disabledPassesThrough()
    {
        properties.setEnabled(false);
        loginAs(42L, 7L);

        assertEquals(999L, guard().resolveAllowed(999L));
    }
}
