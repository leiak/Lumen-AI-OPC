package com.ruoyi.common.security.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 租户作用域过滤器 —— 3b
 *
 * <p>把请求里的 {@code companyId} 查询参数<b>改写成服务端判定的作用域</b>。
 * 之所以要做这一层（而不是只在拦截器里校验）：</p>
 *
 * <p>Spring 的 {@code RequestParamMethodArgumentResolver} 是内置解析器，
 * 注册在自定义解析器<b>之前</b>，自定义 {@code HandlerMethodArgumentResolver}
 * 根本抢不过它 —— 所以没法在参数解析阶段把 {@code @RequestParam Long companyId}
 * 换掉。唯一能在参数解析之前动手的位置就是请求本身：包一层
 * {@link HttpServletRequestWrapper}，让 {@code getParameter("companyId")}
 * 返回服务端判定的值。</p>
 *
 * <p>作用域的值由 {@code TenantInterceptor} 写入请求属性（它跑在
 * {@code HeaderInterceptor} 之后，那时登录态才可用），
 * 本过滤器只负责在参数被读取的那一刻把包装器挂上去。</p>
 *
 * @author OAC
 */
@RequiredArgsConstructor
public class TenantCompanyFilter extends OncePerRequestFilter
{
    private final TenantGuard tenantGuard;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        if (!tenantGuard.isEnabled())
        {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new CompanyIdRequestWrapper(request), response);
    }

    /**
     * 改写 companyId 查询参数的请求包装器。
     *
     * <p>作用域取自请求属性 {@link TenantGuard#EFFECTIVE_COMPANY_ID_ATTR}：
     * 属性没被设置（{@code @InnerAuth} 端点、或拦截器没跑到）时，
     * 一律<b>保留客户端原值</b> —— 不给作用域就不做改写，
     * 避免把「没判定」变成「乱改」。</p>
     */
    static class CompanyIdRequestWrapper extends HttpServletRequestWrapper
    {
        CompanyIdRequestWrapper(HttpServletRequest request)
        {
            super(request);
        }

        /** 服务端判定的作用域；null 表示本次请求没有判定（不乱写）。 */
        private String effectiveCompanyId()
        {
            Object attr = getAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR);
            return attr == null ? null : String.valueOf(attr);
        }

        private boolean isCompanyId(String name)
        {
            return TenantGuard.COMPANY_ID_PARAM.equals(name);
        }

        @Override
        public String getParameter(String name)
        {
            if (isCompanyId(name))
            {
                String effective = effectiveCompanyId();
                if (effective != null)
                {
                    return effective;
                }
            }
            return super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name)
        {
            if (isCompanyId(name))
            {
                String effective = effectiveCompanyId();
                if (effective != null)
                {
                    return new String[] { effective };
                }
            }
            return super.getParameterValues(name);
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            Map<String, String[]> map = new HashMap<>(super.getParameterMap());
            String effective = effectiveCompanyId();
            if (effective != null)
            {
                map.put(TenantGuard.COMPANY_ID_PARAM, new String[] { effective });
            }
            return Collections.unmodifiableMap(map);
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getParameterNames();
            while (original != null && original.hasMoreElements())
            {
                names.add(original.nextElement());
            }
            if (effectiveCompanyId() != null)
            {
                names.add(TenantGuard.COMPANY_ID_PARAM);
            }
            return Collections.enumeration(names);
        }
    }
}
