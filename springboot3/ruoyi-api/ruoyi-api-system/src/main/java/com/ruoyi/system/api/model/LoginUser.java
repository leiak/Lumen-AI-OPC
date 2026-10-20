package com.ruoyi.system.api.model;

import java.io.Serializable;
import java.util.Set;
import com.ruoyi.system.api.domain.SysUser;

/**
 * 用户信息
 *
 * @author ruoyi
 */
public class LoginUser implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 用户名id
     */
    private Long userid;

    /**
     * 用户名
     */
    private String username;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 权限列表
     */
    private Set<String> permissions;

    /**
     * 角色列表
     */
    private Set<String> roles;

    /**
     * 用户信息
     */
    private SysUser sysUser;

    /**
     * 当前登录用户所属公司ID（租户ID）。
     *
     * <p>来源：登录时由 ruoyi-system 依据 {@code opc_company_member} 成员关系解析，
     * 存入 Redis 的登录缓存，随 token 下发给所有下游服务。</p>
     *
     * <p><b>可为空</b>：用户尚未加入任何公司时为 {@code null}。
     * 调用方<b>必须</b>显式处理无租户场景，不要用 0 之类的默认值兜底 ——
     * 静默兜底会掩盖越权（把「无公司」当成「公司 0」去查数据）。</p>
     *
     * <p>注意：用户可能同时属于多家公司（{@code opc_company_member} 的唯一键是
     * {@code (company_id, user_id)}，即多对多）。此处保存的是登录那一刻解析出的
     * <b>默认公司</b>，用户切换公司后需要重新登录才会更新。</p>
     */
    private Long companyId;

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public Long getUserid()
    {
        return userid;
    }

    public void setUserid(Long userid)
    {
        this.userid = userid;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public Long getLoginTime()
    {
        return loginTime;
    }

    public void setLoginTime(Long loginTime)
    {
        this.loginTime = loginTime;
    }

    public Long getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(Long expireTime)
    {
        this.expireTime = expireTime;
    }

    public String getIpaddr()
    {
        return ipaddr;
    }

    public void setIpaddr(String ipaddr)
    {
        this.ipaddr = ipaddr;
    }

    public Set<String> getPermissions()
    {
        return permissions;
    }

    public void setPermissions(Set<String> permissions)
    {
        this.permissions = permissions;
    }

    public Set<String> getRoles()
    {
        return roles;
    }

    public void setRoles(Set<String> roles)
    {
        this.roles = roles;
    }

    public SysUser getSysUser()
    {
        return sysUser;
    }

    public void setSysUser(SysUser sysUser)
    {
        this.sysUser = sysUser;
    }

    public Long getCompanyId()
    {
        return companyId;
    }

    public void setCompanyId(Long companyId)
    {
        this.companyId = companyId;
    }
}
