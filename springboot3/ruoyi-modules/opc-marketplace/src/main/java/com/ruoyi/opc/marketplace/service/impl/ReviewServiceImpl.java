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
            throw new OpcException(404, "商品不存在", "MARKETPLACE");
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
        BigDecimal avg = reviewMapper.averageForPublication(req.getPublicationId());
        int count = reviewMapper.countForPublication(req.getPublicationId());
        if (count > 0) {
            publicationMapper.updateRating(req.getPublicationId(),
                    avg.setScale(2, RoundingMode.HALF_UP), count);
        }
        log.info("[Review] created publicationId={} userId={} rating={}",
                req.getPublicationId(), userId, req.getRating());
        return r;
    }

    @Override
    public MpReview update(Long id, ReviewUpdateRequest req, Long userId) {
        MpReview r = reviewMapper.selectById(id);
        if (r == null) {
            throw new OpcException(404, "评价不存在", "MARKETPLACE");
        }
        // 归属:只有本人才可改
        if (!r.getUserId().equals(userId)) {
            throw new OpcException(403, "只有本人可修改", "MARKETPLACE");
        }
        // 局部更新:null = 不改
        if (req.getRating() != null) r.setRating(req.getRating());
        if (req.getContent() != null) r.setContent(req.getContent());
        r.setUpdateBy(String.valueOf(userId));
        reviewMapper.updateById(r);

        // 修改后重新计算 avg —— 修改人改到自己那条的星级,AVG/COUNT 都可能变
        BigDecimal avg = reviewMapper.averageForPublication(r.getPublicationId());
        int count = reviewMapper.countForPublication(r.getPublicationId());
        if (count > 0) {
            publicationMapper.updateRating(r.getPublicationId(),
                    avg.setScale(2, RoundingMode.HALF_UP), count);
        }
        return r;
    }

    @Override
    public void delete(Long id, Long userId) {
        MpReview r = reviewMapper.selectById(id);
        if (r == null) {
            throw new OpcException(404, "评价不存在", "MARKETPLACE");
        }
        // 归属:只有本人才可删
        if (!r.getUserId().equals(userId)) {
            throw new OpcException(403, "只有本人可删除", "MARKETPLACE");
        }
        // 软删:status='HIDDEN',原评分行不再计入 avg/count(见 mapper XML 的 status 过滤),
        // 但保留审计链路 —— 不走 reviewMapper.delete(物理删会让 uk_review 释放,
        // 同一用户就能再次 create,绕过「每人每商品一条评价」的语义)
        reviewMapper.updateStatus(id, "HIDDEN");
    }

    @Override
    public Map<String, Object> listByPublication(Long publicationId, int page, int pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("publicationId", publicationId);
        // 公开列表恒定只返回 VISIBLE,不让 caller 传 status(否则能枚举 HIDDEN)
        params.put("status", "VISIBLE");
        params.put("offset", (page - 1) * pageSize);
        params.put("limit", pageSize);
        var rows = reviewMapper.selectList(params);
        int total = reviewMapper.countList(params);
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }
}
