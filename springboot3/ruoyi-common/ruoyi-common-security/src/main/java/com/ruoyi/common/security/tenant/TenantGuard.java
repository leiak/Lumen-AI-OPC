package com.ruoyi.common.security.tenant;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.api.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 租户（公司）作用域守卫 —— 3b 跨租户读的核心
 *
 * <p><b>缺陷背景</b>：OPC 的一批端点把 {@code companyId} 当作<b>查询条件</b> 直接
 * 交给 SQL（{@code selectList(companyId, ...)}、{@code validateAndGet(id, companyId)}），
 * 租户隔离本身在 service/mapper 层是做了的 —— 但那个 companyId 是<b>客户端给的</b>。
 * 于是「把参数从 1 改成 2」就等于「看别家公司的数据」，而网关又把一批端点放到了
 * 匿名白名单里（商品列表、职位列表、脚本列表……）。</p>
 *
 * <p><b>修复思路</b>：companyId 只能由服务端决定，客户端传什么都不算数。</p>
 * <ul>
 *   <li><b>已登录</b> → 作用域 = {@code LoginUser.companyId}（登录时由
 *       {@code UserCompanyResolver} 解析、随 token 下发）。客户端传的一致就一致，
 *       传的不一致就以登录态为准。</li>
 *   <li><b>匿名</b> → 作用域 = 配置的公开租户（{@code opc.tenant.public-company-id}，
 *       默认 1）。这批端点本来就是「公开浏览」，必须有个公开的那一家；
 *       客户端不能自己挑，否则匿名就能遍历所有租户。</li>
 *   <li><b>登录了但没加入任何公司</b> → 没有作用域。要访问任何 companyId 都是越权
 *       → 403（fail-closed）。这里<b>不能</b>用 0 兜底：把「无公司」当成「公司 0」
 *       继续查库会造成脏数据/越权。</li>
 * </ul>
 *
 * <p><b>为什么不按请求头区分「内部调用」</b>：内部服务调用只靠一个
 * {@code from-source: inner} 请求头来标识。<b>本段的前提已经变了</b> ——
 * 端口收敛之后（见 {@code e9cfbb5}）所有业务服务端口一律只绑 127.0.0.1，
 * 公网再也连不到服务端口，所以这个头<b>不再能被外部伪造</b>；再加上网关的
 * {@code AuthFilter} 会把外部流量带来的 {@code from-source} 剥掉，
 * 「带着这个头」= 「来自 compose 内网的另一个服务」。</p>
 *
 * <p>即便如此，租户作用域<b>仍然不按请求头豁免</b>：companyId 的作用域只认
 * 登录态 —— 内部调用如果需要跨公司，必须由调用方在自己的业务层把归属显式带过去
 * （见 {@code OpcLlmController#resolveCompanyId} 的做法：认 {@code from-source: inner}
 * 只是为了给「无登录上下文的内部调用」一个可信的兜底归属，不是一张万能后门）。
 * 真正的内部调用走的是 {@code @InnerAuth} 端点，豁免在拦截器里按注解判定，
 * 不看请求头。</p>
 *
 * @author OAC
 */
@Slf4j
@RequiredArgsConstructor
public class TenantGuard
{
    /** 租户参数名（查询参数与请求体 DTO 字段共用）。 */
    public static final String COMPANY_ID_PARAM = "companyId";

    /** 请求属性：服务端判定的租户作用域（拦截器写入，请求包装器读取）。 */
    public static final String EFFECTIVE_COMPANY_ID_ATTR = "opc.tenant.effectiveCompanyId";

    private final TenantProperties properties;

    /**
     * 守卫是否启用。关掉时一律放行客户端给的 companyId（仅供紧急止血用）。
     */
    public boolean isEnabled()
    {
        return properties.isEnabled();
    }

    /**
     * 当前请求的租户作用域。
     *
     * @return 已登录 → 用户所属公司；匿名 → 配置的公开租户；都没有则 null
     */
    public Long effectiveCompanyId()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser != null)
        {
            return loginUser.getCompanyId();
        }
        return properties.getPublicCompanyId();
    }

    /**
     * 解析本次请求<b>允许</b>使用的 companyId。
     *
     * <p>返回的是服务端判定的作用域，调用方应当用它<b>覆盖</b>客户端给的值 ——
     * 覆盖而不是拒绝，理由有二：</p>
     * <ul>
     *   <li>覆盖让「客户端写错了 companyId」不会变成一次功能性故障（前端有 19 处
     *       硬编码 {@code const companyId = 1}，改成 403 只会把问题放大成一片红）；</li>
     *   <li>拒绝会给探测方留下「这个 id 存不存在」的存在性预言机 —— 覆盖没有。</li>
     * </ul>
     *
     * @param requested 客户端给的 companyId（可能为 null）
     * @return 服务端判定的 companyId；作用域为 null 且客户端也没给时返回 null
     * @throws ServiceException 作用域为 null（登录了但没公司）而客户端却指定了
     *                          companyId → 403
     */
    public Long resolveAllowed(Long requested)
    {
        if (!properties.isEnabled())
        {
            return requested;
        }

        Long effective = effectiveCompanyId();
        if (effective == null)
        {
            if (requested != null)
            {
                // 登录了但没加入任何公司 —— 无作用域可用, 而客户端点名了某家公司。
                // 这种情况不是"取不到就放行", 是明确的越权, 必须拒绝。
                throw new ServiceException("当前账号未关联企业, 无法访问其它租户的数据", 403);
            }
            return null;
        }

        if (requested != null && !requested.equals(effective))
        {
            // 不拒绝, 只告警: 客户端无权决定租户, 但把它改对也不该让请求失败。
            log.warn("[租户守卫] 请求里的 companyId={} 与作用域 {} 不一致, 以服务端为准", requested, effective);
        }
        return effective;
    }
}
