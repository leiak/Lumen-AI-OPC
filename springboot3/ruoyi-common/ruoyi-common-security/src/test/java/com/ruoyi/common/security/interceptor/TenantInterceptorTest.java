package com.ruoyi.common.security.interceptor;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.common.security.tenant.TenantGuard;
import com.ruoyi.common.security.tenant.TenantProperties;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantInterceptor} 契约测试 —— 3b
 *
 * @author OAC
 */
class TenantInterceptorTest
{
    private final TenantInterceptor interceptor = new TenantInterceptor(new TenantGuard(new TenantProperties()));

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

    private HandlerMethod handler(String method) throws NoSuchMethodException
    {
        return new HandlerMethod(new FakeController(), FakeController.class.getMethod(method));
    }

    @Test
    @DisplayName("普通端点 → 把服务端判定的作用域写进请求属性")
    void writesEffectiveCompanyId() throws Exception
    {
        loginAs(42L, 7L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/opc/erp/product/list");
        request.setParameter("companyId", "2");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("list")));

        assertEquals(7L, request.getAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR),
                "作用域必须来自登录态, 不能来自请求参数");
    }

    @Test
    @DisplayName("客户端没带 companyId → 也要给出作用域(公开浏览靠它补上参数)")
    void writesScopeEvenWithoutParam() throws Exception
    {
        loginAs(42L, 7L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/opc/erp/product/list");

        interceptor.preHandle(request, new MockHttpServletResponse(), handler("list"));

        assertEquals(7L, request.getAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR));
    }

    @Test
    @DisplayName("@InnerAuth 端点 → 不判作用域, 保留调用方显式指定的公司")
    void innerAuthEndpointIsExempt() throws Exception
    {
        loginAs(42L, 7L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/opc/user/companies/profile");
        request.setParameter("companyId", "999");

        interceptor.preHandle(request, new MockHttpServletResponse(), handler("profile"));

        assertNull(request.getAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR),
                "@InnerAuth 端点由 InnerAuthAspect 自己把关, 这里不能改写它的 companyId");
    }

    @Test
    @DisplayName("companyId 不是数字 → 400")
    void malformedCompanyIdIsRejected() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/opc/erp/product/list");
        request.setParameter("companyId", "abc");

        ServiceException e = assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("list")));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("非 ControllerMethod(如静态资源) → 不处理")
    void nonHandlerIsSkipped() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");
        request.setParameter("companyId", "2");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(request.getAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR));
    }

    /** 假控制器 —— 只为拿到 HandlerMethod 与 @InnerAuth 标注。 */
    static class FakeController
    {
        public void list()
        {
        }

        @InnerAuth
        public void profile()
        {
        }
    }
}
