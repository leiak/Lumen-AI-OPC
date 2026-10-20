package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 评价 Service 实现 — P1
 *
 * <p><b>唯一性 + 评分回写</b>：同一用户对同一商品只能有一条评价行（uk_review 不含 deleted），
 * 创建时先查一次给出友好 409；新增/修改评价后，{@code publicationMapper.updateRating} 把
 * 该商品的 {@code avg_rating}/{@code rating_count} 同步成 VISIBLE 行的实时 AVG 与计数。
 *
 * <p><b>{@link com.ruoyi.opc.marketplace.mapper.MpReviewMapper#averageForPublication} 对零行返
 * {@code null}</b>（SQL {@code AVG()} 本职行为），本类在调用处用 {@code if (count > 0)} 守
 * {@code not-null}：只要至少 1 行 VISIBLE，{@code AVG} 必返非 null，因此
 * {@code avg.setScale(2, RoundingMode.HALF_UP)} 安全。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final MpReviewMapper reviewMapper;
    private final MpPublicationMapper publicationMapper;

    @Override
    public MpReview create(ReviewCreateRequest req, Long userId, Long tenantCompanyId) {
        MpPublication p = publicationMapper.selectById(req.getPublicationId());
        if (p == null) {
            throw new OpcException(404, "商品不存在: " + req.getPublicationId(), "MARKETPLACE");
        }
        // 与 InstallServiceImpl 同步:未发布商品不可评价 —— DRAFT/PENDING/SUSPENDED 上评价会污染 avg
        if (!"PUBLISHED".equals(p.getStatus())) {
            throw new OpcException(400, "商品未发布，无法评价", "MARKETPLACE");
        }

        // uk_review(publication_id, user_id) 不含 deleted —— 即便是 HIDDEN 行也占唯一键,
        // 用 status 过滤会绕过这个语义,所以不过滤,让「重复评价」返回的是历史隐藏行也算命中
        MpReview existing = reviewMapper.selectByPublicationAndUser(req.getPublicationId(), userId);
        if (existing != null) {
            throw new OpcException(409, "已评价，请勿重复", "MARKETPLACE");
        }

        MpReview r = new MpReview();
        r.setPublicationId(req.getPublicationId());
        r.setTenantCompanyId(tenantCompanyId);
        r.setUserId(userId);
        r.setRating(req.getRating());
        r.setContent(req.getContent());
        r.setStatus("VISIBLE");
        r.setCreateBy(String.valueOf(userId));
        r.setUpdateBy(String.valueOf(userId));
        reviewMapper.insert(r);

        // 评分回写。count > 0 保证 avg 非 null,详见类注释
        refreshPublicationRating(req.getPublicationId());
        log.info("[Review] created id={} publicationId={} userId={} rating={}",
                r.getId(), req.getPublicationId(), userId, req.getRating());
        return r;
    }

    @Override
    public MpReview update(Long id, ReviewUpdateRequest req, Long userId) {
        MpReview r = reviewMapper.selectById(id);
        if (r == null) {
            throw new OpcException(404, "评价不存在", "MARKETPLACE");
        }
        // 归属:只有本人才可改。用 Objects.equals 避免 userId 为 null 时 NPE → 500(fail-closed → 403)
        if (!Objects.equals(r.getUserId(), userId)) {
            throw new OpcException(403, "只有本人可修改", "MARKETPLACE");
        }
        // 局部更新:null = 不改
        if (req.getRating() != null) r.setRating(req.getRating());
        if (req.getContent() != null) r.setContent(req.getContent());
        r.setUpdateBy(String.valueOf(userId));
        reviewMapper.updateById(r);

        // 修改后重新计算 avg —— 修改人改到自己那条的星级,AVG/COUNT 都可能变
        refreshPublicationRating(r.getPublicationId());
        return r;
    }

    @Override
    public void delete(Long id, Long userId) {
        MpReview r = reviewMapper.selectById(id);
        if (r == null) {
            throw new OpcException(404, "评价不存在", "MARKETPLACE");
        }
        // 归属:只有本人才可删
        if (!Objects.equals(r.getUserId(), userId)) {
            throw new OpcException(403, "只有本人可删除", "MARKETPLACE");
        }
        // 软删:status='HIDDEN',原评分行不再计入 avg/count(见 mapper XML 的 status 过滤),
        // 但保留审计链路 —— 不走 reviewMapper.delete(物理删会让 uk_review 释放,
        // 同一用户就能再次 create,绕过「每人每商品一条评价」的语义)
        reviewMapper.updateStatus(id, "HIDDEN");
        // C1 修复:delete 后行已离开 VISIBLE 集,必须重算 avg/count,否则商品聚合永久陈旧
        // 直至下一次 create/update 触发回写
        refreshPublicationRating(r.getPublicationId());
    }

    @Override
    public Map<String, Object> listByPublication(Long publicationId, int page, int pageSize) {
        // 与 PublicationServiceImpl 308-310 同步:?page=0/-1 让 MySQL LIMIT offset 为负直接报错;
        // ?pageSize=99999 让单次返回巨大结果集 → DOS 兜底
        int p = page > 0 ? page : 1;
        int size = pageSize > 0 ? Math.min(pageSize, 100) : 10;
        Map<String, Object> params = new HashMap<>();
        params.put("publicationId", publicationId);
        // 公开列表恒定只返回 VISIBLE,不让 caller 传 status(否则能枚举 HIDDEN)
        params.put("status", "VISIBLE");
        params.put("offset", (p - 1) * size);
        params.put("limit", size);
        var rows = reviewMapper.selectList(params);
        int total = reviewMapper.countList(params);
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("total", total);
        data.put("page", p);
        data.put("pageSize", size);
        return data;
    }

    /**
     * 重算并回写 {@code mp_publication.avg_rating / rating_count}。
     * <p>从 VISIBLE 行集做 AVG + COUNT —— 与 mapper XML 一致。零行时保留现有值(由 DB
     * COALESCE/默认值定义),不强行 null out —— 与 {@link InstallServiceImpl#install} 同
     * SKU 的「从不显式回滚并发量」原则一致。</p>
     */
    private void refreshPublicationRating(Long publicationId) {
        int count = reviewMapper.countForPublication(publicationId);
        if (count <= 0) return;  // no VISIBLE rows — leave existing avg/count untouched
        BigDecimal avg = reviewMapper.averageForPublication(publicationId);
        publicationMapper.updateRating(publicationId,
                avg.setScale(2, RoundingMode.HALF_UP), count);
    }
}
