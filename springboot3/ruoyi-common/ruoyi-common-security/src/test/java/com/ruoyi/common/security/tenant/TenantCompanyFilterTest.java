package com.ruoyi.common.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TenantCompanyFilter.CompanyIdRequestWrapper} 契约测试 —— 3b
 *
 * <p>包装器是"改写 companyId"这件事真正落地的地方：
 * Spring 内置的 {@code RequestParamMethodArgumentResolver} 排在自定义解析器之前，
 * 抢不过它，唯一能动手的位置就是请求本身。</p>
 *
 * @author OAC
 */
class TenantCompanyFilterTest
{
    private MockHttpServletRequest requestWith(String companyId)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/opc/erp/product/list");
        if (companyId != null)
        {
            request.setParameter("companyId", companyId);
        }
        return request;
    }

    @Test
    @DisplayName("有作用域 → 客户端的 companyId 被换成服务端的")
    void overridesClientValue()
    {
        MockHttpServletRequest request = requestWith("2");
        request.setAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR, 7L);

        TenantCompanyFilter.CompanyIdRequestWrapper wrapper =
                new TenantCompanyFilter.CompanyIdRequestWrapper(request);

        assertEquals("7", wrapper.getParameter("companyId"));
        assertEquals("7", wrapper.getParameterValues("companyId")[0]);
    }

    @Test
    @DisplayName("有作用域但客户端没带 companyId → 补上(公开浏览因此不需要客户端传)")
    void fillsMissingValue()
    {
        MockHttpServletRequest request = requestWith(null);
        request.setAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR, 1L);

        TenantCompanyFilter.CompanyIdRequestWrapper wrapper =
                new TenantCompanyFilter.CompanyIdRequestWrapper(request);

        assertEquals("1", wrapper.getParameter("companyId"));
    }

    @Test
    @DisplayName("没有作用域(判定没跑到/@InnerAuth) → 保留客户端原值")
    void keepsOriginalValueWithoutScope()
    {
        MockHttpServletRequest request = requestWith("2");

        TenantCompanyFilter.CompanyIdRequestWrapper wrapper =
                new TenantCompanyFilter.CompanyIdRequestWrapper(request);

        assertEquals("2", wrapper.getParameter("companyId"));
    }

    @Test
    @DisplayName("其它参数不受影响")
    void otherParamsUntouched()
    {
        MockHttpServletRequest request = requestWith("2");
        request.setParameter("category", "electronics");
        request.setAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR, 7L);

        TenantCompanyFilter.CompanyIdRequestWrapper wrapper =
                new TenantCompanyFilter.CompanyIdRequestWrapper(request);

        assertEquals("electronics", wrapper.getParameter("category"));
    }

    @Test
    @DisplayName("没有作用域时, 请求里也不该凭空多出 companyId")
    void noScopeAddsNoParam()
    {
        MockHttpServletRequest request = requestWith(null);

        TenantCompanyFilter.CompanyIdRequestWrapper wrapper =
                new TenantCompanyFilter.CompanyIdRequestWrapper(request);

        assertNull(wrapper.getParameter("companyId"));
        assertNull(wrapper.getParameterMap().get("companyId"));
    }
}
