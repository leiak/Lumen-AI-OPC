package com.ruoyi.system.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 用户-公司（租户）成员关系 数据层
 *
 * <p>租户归属的<b>唯一事实来源</b>是 {@code opc_company_member}，不是 {@code sys_user}。
 * {@code sys_user} 表上没有任何公司字段，因此登录时只能按 userId 反查成员关系。
 *
 * @author ruoyi
 */
public interface SysUserCompanyMapper
{
    /**
     * 查询用户所属的默认公司ID
     *
     * <p>一个用户可能同时属于多家公司（{@code opc_company_member} 的唯一键是
     * {@code (company_id, user_id)}，即多对多）。这里按「老板优先、其次 ID 最小」
     * 排序取一条，保证同一用户每次登录解析结果<b>稳定可复现</b>，而不是依赖
     * InnoDB 的返回顺序。
     *
     * <p>只认「在职成员」({@code opc_company_member.status='ACTIVE'}) 且「公司正常」
     * ({@code opc_company_profile.status='NORMAL'}) 的关系。
     *
     * @param userId 用户ID
     * @return 默认公司ID，用户未加入任何有效公司时返回 null
     */
    Long selectDefaultCompanyId(@Param("userId") Long userId);
}