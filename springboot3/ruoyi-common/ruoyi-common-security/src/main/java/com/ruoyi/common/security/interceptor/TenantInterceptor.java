package com.ruoyi.common.security.interceptor;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.common.security.tenant.TenantGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 租户作用域拦截器 —— 3b
 *
 * <p>在 {@link HeaderInterceptor}（order -10，把 token 解析成登录态）之后运行
 * （order -5），因此这里能拿到 {@code LoginUser.companyId}。</p>
 *
 * <p>职责只有一件：判定本次请求的<b>租户作用域</b>，写进请求属性，
 * 供 {@code TenantCompanyFilter} 的请求包装器改写查询参数里的 companyId。</p>
 *
 * <p><b>为什么改写而不是拒绝</b>：见 {@link TenantGuard#resolveAllowed}。
 * 简单说：客户端说了不算，但也不该因此报错。</p>
 *
 * @author OAC
 */
@Slf4j
@RequiredArgsConstructor
public class TenantInterceptor implements AsyncHandlerInterceptor
{
    private final TenantGuard tenantGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        // 非 ControllerMethod（静态资源、默认 servlet 等）不涉及业务数据, 不判作用域
        if (!(handler instanceof HandlerMethod method))
        {
            return true;
        }

        // @InnerAuth 端点（内部服务调用用）有自己的一套信任模型:
        // InnerAuthAspect 已经校验过 from-source 请求头。这类端点的 companyId
        // 由调用方显式指定（如 opc-insight 查某家公司的档案），不归本守卫管。
        if (method.getMethodAnnotation(InnerAuth.class) != null)
        {
            return true;
        }

        String raw = request.getParameter(TenantGuard.COMPANY_ID_PARAM);
        Long requested = null;
        if (StringUtils.isNotBlank(raw))
        {
            try
            {
                requested = Long.valueOf(raw.trim());
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("companyId 格式非法: " + raw, 400);
            }
        }

        Long effective = tenantGuard.resolveAllowed(requested);
        if (effective != null)
        {
            request.setAttribute(TenantGuard.EFFECTIVE_COMPANY_ID_ATTR, effective);
        }
        return true;
    }
}
