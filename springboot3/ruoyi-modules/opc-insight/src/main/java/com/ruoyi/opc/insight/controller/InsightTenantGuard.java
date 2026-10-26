package com.ruoyi.opc.insight.controller;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;

import java.util.Objects;

/**
 * <b>INSIGHT 模块的租户归属校验</b>。
 *
 * <p>洞察模块的列表类接口（dashboard / alerts 列表 / daily 列表 / advice 列表）
 * 都用 {@link #resolveCompanyId()} 拿当前公司做查询作用域，天然按租户隔离。
 * 但"按 id 取单条"的接口（{@code /alerts/{id}}、{@code /daily/{id}}、
 * {@code /advice/{id}}）以及"按 id 改状态"的写操作（{@code /alerts/{id}/ack}、
 * {@code /advice/{id}/regenerate}）历史上只认 id —— 客户端把 id 改一下，
 * 就能读别家公司的洞察数据，甚至把别家的异常标记成已确认。</p>
 *
 * <p>这里统一按「记录自己的 companyId == 当前登录用户所属公司」来判定，
 * 与列表口径同源（都取 {@code LoginUser.companyId}），避免出现两套作用域。</p>
 *
 * @author OAC
 */
final class InsightTenantGuard
{
    private InsightTenantGuard()
    {
    }

    /**
     * 取当前登录用户所属公司。
     *
     * <p>没有登录上下文时 {@code SecurityUtils.getCompanyId()} 抛
     * {@code ServiceException}；登录了但没加入任何公司时返回 null，
     * 这里再抛 {@code OpcException} —— 与各 controller 原有的
     * {@code resolveCompanyId()} 语义完全一致。</p>
     */
    static Long resolveCompanyId()
    {
        Long id = SecurityUtils.getCompanyId();
        if (id == null)
        {
            throw new OpcException("当前账号未关联企业，请先加入或创建企业");
        }
        return id;
    }

    /**
     * 校验「这条记录属于当前登录用户所在的公司」。
     *
     * @param recordCompanyId  记录自身的 companyId
     * @param currentCompanyId 当前登录用户所属公司
     * @param what             业务对象名（拼进错误信息）
     * @throws ServiceException 归属不符时抛出（code=403）
     */
    static void requireOwned(Long recordCompanyId, Long currentCompanyId, String what)
    {
        // 记录不存在（companyId 为 null）与「归属别家」同样拒绝：
        // 端点只按 id 取数，放行等于把「id 存不存在」这件事也变成可枚举的信息。
        if (currentCompanyId == null || !Objects.equals(currentCompanyId, recordCompanyId))
        {
            throw new ServiceException("无权访问其它公司的" + what, 403);
        }
    }
}
