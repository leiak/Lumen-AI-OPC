package com.ruoyi.common.security.config;

import com.ruoyi.common.security.interceptor.TenantInterceptor;
import com.ruoyi.common.security.tenant.CompanyIdBodyAdvice;
import com.ruoyi.common.security.tenant.TenantCompanyFilter;
import com.ruoyi.common.security.tenant.TenantGuard;
import com.ruoyi.common.security.tenant.TenantProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 租户守卫装配 —— 3b
 *
 * <p>把 {@link TenantGuard} / {@link TenantInterceptor} / {@link TenantCompanyFilter} /
 * {@link CompanyIdBodyAdvice} 装进每个 OPC 服务。</p>
 *
 * <p><b>执行顺序</b>（决定了「服务端什么时候才可能知道租户是谁」）：</p>
 * <ol>
 *   <li>{@code TenantCompanyFilter}（filter）—— 只负责把请求包一层，
 *       作用域的值它此刻还不知道；</li>
 *   <li>{@code HeaderInterceptor}（order -10）—— 解析 token，填充登录态；</li>
 *   <li>{@code TenantInterceptor}（order -5）—— 此时登录态已就绪，
 *       判定租户作用域并写进请求属性；</li>
 *   <li>参数解析 —— 包装器把 companyId 换成服务端判定的作用域。</li>
 * </ol>
 *
 * <p>filter 必须比拦截器<b>早</b>，因为包装器要挂在请求上；但它拿到的作用域来自
 * 请求属性，所以真正判定的时刻在拦截器里 —— 顺序反了作用域就会退化成
 * 「匿名时的公开租户」。</p>
 *
 * @author OAC
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantConfig implements WebMvcConfigurer
{
    /**
     * 用 {@link ObjectProvider} 而不是直接注入：{@code TenantGuard} 由本类自己定义，
     * 构造器直接依赖它会形成自引用。
     */
    private final ObjectProvider<TenantGuard> tenantGuardProvider;

    public TenantConfig(ObjectProvider<TenantGuard> tenantGuardProvider)
    {
        this.tenantGuardProvider = tenantGuardProvider;
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantGuard tenantGuard(TenantProperties properties)
    {
        return new TenantGuard(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CompanyIdBodyAdvice companyIdBodyAdvice(TenantGuard tenantGuard)
    {
        return new CompanyIdBodyAdvice(tenantGuard);
    }

    /**
     * 请求包装过滤器。顺序排在最前 —— 它要在任何东西读参数之前把包装器挂上去。
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TenantCompanyFilter> tenantCompanyFilter(TenantGuard tenantGuard)
    {
        FilterRegistrationBean<TenantCompanyFilter> registration =
                new FilterRegistrationBean<>(new TenantCompanyFilter(tenantGuard));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        registration.addUrlPatterns("/*");
        registration.setName("tenantCompanyFilter");
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        TenantGuard tenantGuard = tenantGuardProvider.getIfAvailable();
        if (tenantGuard == null)
        {
            return;
        }
        registry.addInterceptor(new TenantInterceptor(tenantGuard))
                .addPathPatterns("/**")
                .excludePathPatterns(WebMvcConfig.excludeUrls)
                // HeaderInterceptor 是 -10, 必须排在它后面 —— 那时登录态才填好了
                .order(-5);
    }
}
