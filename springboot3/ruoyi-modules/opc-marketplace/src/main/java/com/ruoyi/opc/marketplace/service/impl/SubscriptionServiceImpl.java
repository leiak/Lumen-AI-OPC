package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.domain.MpSubscription;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpSubscriptionMapper;
import com.ruoyi.opc.marketplace.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link SubscriptionService} 实现 — W80 P2
 *
 * <p><b>计数器口径</b>（两个数字，别混）：
 * <ul>
 *   <li>{@code mp_publication.subscription_count} —— 这个商品的活跃订阅<b>行数</b>。
 *       订阅 +1，退订 -1，重新订阅 +1。它与
 *       {@code COUNT(mp_subscription WHERE status='ACTIVE')} 恒等，
 *       健康检查第 19 节断言这一点。</li>
 *   <li>{@code mp_author.total_subscribers} —— 这个作者有多少家<b>公司</b>在订阅
 *       （{@code COUNT(DISTINCT tenant_company_id)}）。同一家公司的 5 个人订阅算 1 个。
 *       这个用<b>回写</b>而不是 +1/-1 —— 退订时不知道该减几个
 *       （可能减 0，也可能减 1），增量写法在这种口径下必然漂移。</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final MpSubscriptionMapper subscriptionMapper;
    private final MpPublicationMapper publicationMapper;
    private final MpAuthorMapper authorMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MpSubscription subscribe(Long publicationId, Long tenantCompanyId, Long userId) {
        // —— 1. 商品必须存在且已发布 ——
        MpPublication p = publicationMapper.selectById(publicationId);
        if (p == null) {
            throw new OpcException(404, "商品不存在: " + publicationId, "MARKETPLACE");
        }
        if (!"PUBLISHED".equals(p.getStatus())) {
            throw new OpcException(400, "商品未发布，无法订阅", "MARKETPLACE");
        }

        // —— 2. 唯一键前置检查(uk_sub 不含 deleted,退订行也占位)——
        MpSubscription existing = subscriptionMapper.selectUnique(publicationId, tenantCompanyId, userId);
        if (existing != null && STATUS_ACTIVE.equals(existing.getStatus())) {
            throw new OpcException(409, "已订阅，请勿重复", "MARKETPLACE");
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = String.valueOf(userId);
        MpSubscription sub;
        if (existing != null) {
            // 复活:退订后重新订阅 → 计数回到 +1
            subscriptionMapper.updateStatus(existing.getId(), STATUS_ACTIVE, null, operator);
            sub = existing;
            sub.setStatus(STATUS_ACTIVE);
            sub.setEndTime(null);
            sub.setStartTime(now);
            log.info("[Subscribe] resubscribed publicationId={} userId={}", publicationId, userId);
        } else {
            sub = MpSubscription.builder()
                    .publicationId(publicationId)
                    .tenantCompanyId(tenantCompanyId)
                    .userId(userId)
                    .productType(p.getProductType())
                    .plan("FREE")
                    .status(STATUS_ACTIVE)
                    .startTime(now)
                    .createBy(operator)
                    .updateBy(operator)
                    .build();
            subscriptionMapper.insert(sub);
            log.info("[Subscribe] subscribed publicationId={} userId={}", publicationId, userId);
        }

        // —— 3. 计数器 ——
        publicationMapper.incrementSubscriptionCount(publicationId);
        refreshAuthorSubscribers(p.getAuthorId());
        return sub;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unsubscribe(Long publicationId, Long tenantCompanyId, Long userId) {
        MpSubscription existing = subscriptionMapper.selectUnique(publicationId, tenantCompanyId, userId);
        if (existing == null) {
            throw new OpcException(404, "未订阅", "MARKETPLACE");
        }
        if (!STATUS_ACTIVE.equals(existing.getStatus())) {
            // 已经退订过了 → 不重复减计数(否则计数会掉到 0 以下)
            throw new OpcException(409, "该订阅已取消", "MARKETPLACE");
        }
        subscriptionMapper.updateStatus(existing.getId(), STATUS_CANCELLED, LocalDateTime.now(),
                String.valueOf(userId));

        MpPublication p = publicationMapper.selectById(publicationId);
        publicationMapper.decrementSubscriptionCount(publicationId);
        if (p != null) {
            refreshAuthorSubscribers(p.getAuthorId());
        }
        log.info("[Unsubscribe] publicationId={} userId={}", publicationId, userId);
    }

    @Override
    public MpSubscription getStatus(Long publicationId, Long tenantCompanyId, Long userId) {
        return subscriptionMapper.selectUnique(publicationId, tenantCompanyId, userId);
    }

    @Override
    public List<MpSubscription> listByUser(Long userId) {
        return subscriptionMapper.selectByUser(userId);
    }

    @Override
    public List<MpSubscription> listByTenant(Long tenantCompanyId) {
        return subscriptionMapper.selectByTenant(tenantCompanyId);
    }

    /**
     * 按真实订阅行回写作者的「订阅者数」。
     *
     * <p>回写而不是增减 —— 见类注释里两个口径的差别。
     */
    private void refreshAuthorSubscribers(Long authorId) {
        if (authorId == null) {
            return;
        }
        int count = subscriptionMapper.countDistinctTenantsByAuthor(authorId);
        authorMapper.refreshTotalSubscribers(authorId, count);
    }
}
