package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import com.ruoyi.opc.marketplace.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评价 Controller — P1
 *
 * <p>路由：{@code /opc/marketplace/review}
 * <ul>
 *   <li>POST /                       — 创建评价（需登录 + 已关联企业）</li>
 *   <li>GET  /list/{publicationId}   — 公开列表：恒定只返回 VISIBLE</li>
 *   <li>PUT  /{id}                   — 修改本人评价（403 归属校验由 service 抛）</li>
 *   <li>DELETE /{id}                 — 软删本人评价（HIDDEN，保留审计链路）</li>
 * </ul>
 *
 * <p>写入路径（{@code POST}）<b>必须</b>有租户上下文 —— {@code tenantCompanyId}
 * 来自 {@code SecurityUtils.getCompanyId()}，null（账号未关联企业）→ 400，
 * <b>禁止</b>用 0 兜底。把「无租户」当「公司 0」继续查库会越权 / 写脏数据。
 * 修改 / 删除路径由 service 层以 {@code userId == r.userId} 做归属校验，非本人 403。
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/marketplace/review")
@RequiredArgsConstructor
public class ReviewController {

    private static final String NO_TENANT = "当前账号未关联企业，请先加入或创建企业";

    private final ReviewService reviewService;

    /**
     * 创建评价。@Valid 校验 publicationId 必填、rating 在 [1,5]、content 长度上限。
     */
    @PostMapping
    public R<MpReview> create(@Valid @RequestBody ReviewCreateRequest req) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed:未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) {
            throw new OpcException(400, NO_TENANT, "MARKETPLACE");
        }
        return R.ok(reviewService.create(req, userId, companyId));
    }

    /** 公开列表：恒定只返回 VISIBLE，避免 HIDDEN 泄露 */
    @GetMapping("/list/{publicationId}")
    public R<Map<String, Object>> list(@PathVariable Long publicationId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.listByPublication(publicationId, page, pageSize));
    }

    /** 修改本人评价；非本人 → service 抛 403 */
    @PutMapping("/{id}")
    public R<MpReview> update(@PathVariable Long id, @Valid @RequestBody ReviewUpdateRequest req) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(reviewService.update(id, req, userId));
    }

    /** 软删本人评价：status='HIDDEN'，保留审计链路；非本人 → service 抛 403 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        reviewService.delete(id, userId);
        return R.ok();
    }
}
