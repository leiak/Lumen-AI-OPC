package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;

import java.util.Map;

/**
 * 评价 Service — P1
 *
 * <p>评价的「作者」语义来自 {@code sys_user}（评价者本人），而非 {@code mp_author} —— 任何登录用户（不限是否入驻作者）都可以评价已发布的商品。所以 service 入参是 {@code userId}，
 * 不是 {@code authorId}。
 *
 * <p>唯一性约束来自 {@code mp_review.uk_review(publication_id, user_id)}：
 * 同一用户对同一商品只能存在 1 条评价（无论 status 是否 VISIBLE，唯一键不含 deleted，见
 * {@link com.ruoyi.opc.marketplace.mapper.MpReviewMapper#selectByPublicationAndUser}）。
 * 创建时若发现已存在行 → 409；删除走「软删」{@code updateStatus(id, "HIDDEN")}，
 * 保留审计链路。
 *
 * @author OAC
 */
public interface ReviewService {
    MpReview create(ReviewCreateRequest req, Long userId, Long tenantCompanyId);
    MpReview update(Long id, ReviewUpdateRequest req, Long userId);
    void delete(Long id, Long userId);
    Map<String, Object> listByPublication(Long publicationId, int page, int pageSize);
}
