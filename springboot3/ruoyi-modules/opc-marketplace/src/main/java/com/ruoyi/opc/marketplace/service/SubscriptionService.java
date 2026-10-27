package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpSubscription;

import java.util.List;

/**
 * 订阅 Service — W80 P2
 *
 * <p><b>为什么现在才有这个接口</b>：{@code mp_publication.subscription_count}
 * 是商品卡上唯一<b>完全没有写入路径</b>的数字 ——
 * {@code MpPublicationMapper#incrementSubscriptionCount} 写好了却零调用方，
 * 订阅这个产品动作从来没被实现过。一个数在页面上展示着，却不对应任何一条真实记录。
 *
 * <p><b>口径</b>：
 * <ul>
 *   <li>{@code subscription_count == COUNT(mp_subscription WHERE status='ACTIVE')}</li>
 *   <li>订阅成功 +1，退订 -1，重新订阅 +1</li>
 *   <li>作者档案的 {@code total_subscribers} = {@code COUNT(DISTINCT tenant_company_id)}
 *       —— 同一家公司的 5 个人订阅算 1 个订阅者。这是「回写」而不是「+1/-1」：
 *       退订时不知道该减几个，增量写法在这种口径下必然漂移。</li>
 * </ul>
 *
 * <p><b>只做免费订阅</b>：marketplace 与 opc-billing 之间没有写单路径，
 * {@code plan} 只有 {@code FREE}。刻意不造付费套餐 —— 一个点不动的「付费」按钮
 * 比没有更糟。
 *
 * @author OAC
 */
public interface SubscriptionService {

    /**
     * 订阅（首次 → INSERT；既有 CANCELLED → 复活）。
     *
     * <p>{@code tenantCompanyId} 与 {@code userId} 一律来自 controller 的登录态解析，
     * service 层不再校验非空 —— 但调用方要保证 null 不流入。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 商品不存在 / 400 商品未发布 /
     *                                              409 已订阅
     */
    MpSubscription subscribe(Long publicationId, Long tenantCompanyId, Long userId);

    /**
     * 退订（{@code ACTIVE → CANCELLED}）。
     *
     * <p>未订阅 → 404；已退订 → 409（不重复减计数）。
     */
    void unsubscribe(Long publicationId, Long tenantCompanyId, Long userId);

    /**
     * 单条订阅状态查询。返回 {@code null} 表示「未订阅」（状态查询语义，不算 404）。
     */
    MpSubscription getStatus(Long publicationId, Long tenantCompanyId, Long userId);

    /** 当前用户全部订阅记录（按 start_time DESC）。 */
    List<MpSubscription> listByUser(Long userId);

    /** 当前租户（公司）全部订阅记录。 */
    List<MpSubscription> listByTenant(Long tenantCompanyId);
}
