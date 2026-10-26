package com.ruoyi.common.security.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户（公司）相关配置。
 *
 * <p>对应配置前缀 {@code opc.tenant}，在 Nacos 的 {@code application-dev.yml} 里可覆盖：</p>
 * <pre>
 * opc:
 *   tenant:
 *     public-company-id: 1     # 匿名公开访问时可见的租户
 * </pre>
 *
 * @author OAC
 */
@ConfigurationProperties(prefix = "opc.tenant")
public class TenantProperties
{
    /**
     * 匿名（未登录）访问时的租户作用域。
     *
     * <p>网关把一批「公开浏览」端点放到了匿名可访问的白名单里（商品列表、职位列表、
     * 脚本列表……），而这些端点是按公司隔离的 —— 那就得有个「公开的那一家」。
     * 客户端传的 companyId 不能决定这个（否则匿名就能遍历所有租户的数据），
     * 所以由服务端统一指定。</p>
     *
     * <p>默认 1：演示环境里公司 1 就是公开展示的那一家。</p>
     */
    private Long publicCompanyId = 1L;

    /**
     * 是否启用租户守卫。
     *
     * <p>正常情况下不要关。留这个开关只是给运维一条「不用重新发版就能止血」的通道 ——
     * 守卫误伤时先关掉止血、再定位。</p>
     */
    private boolean enabled = true;

    public Long getPublicCompanyId()
    {
        return publicCompanyId;
    }

    public void setPublicCompanyId(Long publicCompanyId)
    {
        this.publicCompanyId = publicCompanyId;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
