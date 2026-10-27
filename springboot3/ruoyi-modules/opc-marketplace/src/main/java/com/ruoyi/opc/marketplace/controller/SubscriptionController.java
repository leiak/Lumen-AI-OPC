package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpSubscription;
import com.ruoyi.opc.marketplace.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订阅 Controller — W80 P2
 *
 * <p>路由：{@code /opc/marketplace/subscription}
 * <ul>
 *   <li>POST   /{publicationId}        — 订阅（建 mp_subscription 行 + 计数 +1）</li>
 *   <li>DELETE /{publicationId}        — 退订（翻 CANCELLED + 计数 -1）</li>
 *   <li>GET    /{publicationId}/status — 单条订阅状态查询</li>
 *   <li>GET    /my                     — 当前用户全部订阅记录</li>
 *   <li>GET    /tenant                 — 当前租户（公司）全部订阅记录</li>
 * </ul>
 *
 * <p><b>租户身份一律取自登录态</b>：{@code SecurityUtils.getCompanyId()}，
 * null（账号未关联企业）→ 400，<b>禁止</b>用 0 兜底（同 {@link InstallController}）。
 *
 * <p><b>不在网关白名单里</b>：订阅是写操作，必须登录。公开白名单只有
 * {@code GET:/opc/marketplace/publication/**} 那几条。
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/marketplace/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private static final String NO_TENANT = "当前账号未关联企业，请先加入或创建企业";

    private final SubscriptionService subscriptionService;

    @PostMapping("/{publicationId}")
    public R<MpSubscription> subscribe(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = requireCompanyId();
        return R.ok(subscriptionService.subscribe(publicationId, companyId, userId));
    }

    @DeleteMapping("/{publicationId}")
    public R<Void> unsubscribe(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = requireCompanyId();
        subscriptionService.unsubscribe(publicationId, companyId, userId);
        return R.ok();
    }

    /**
     * 单条订阅状态查询。返回 {@code null} 表示「未订阅」——
     * 状态查询语义，不算 404（同 {@link InstallController#status}）。
     */
    @GetMapping("/{publicationId}/status")
    public R<MpSubscription> status(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = requireCompanyId();
        return R.ok(subscriptionService.getStatus(publicationId, companyId, userId));
    }

    /** 当前登录用户的全部订阅记录。 */
    @GetMapping("/my")
    public R<List<MpSubscription>> my() {
        Long userId = SecurityUtils.getUserId();
        return R.ok(subscriptionService.listByUser(userId));
    }

    /** 当前租户（公司）全部订阅记录。 */
    @GetMapping("/tenant")
    public R<List<MpSubscription>> tenant() {
        Long companyId = requireCompanyId();
        return R.ok(subscriptionService.listByTenant(companyId));
    }

    /**
     * fail-closed：未关联企业的账号不得进入租户维度操作，<b>禁止</b>用 0 兜底。
     */
    private Long requireCompanyId() {
        Long companyId = SecurityUtils.getCompanyId();
        if (companyId == null) {
            throw new OpcException(400, NO_TENANT, "MARKETPLACE");
        }
        return companyId;
    }
}
