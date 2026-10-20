package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.dto.PublicationCreateRequest;
import com.ruoyi.opc.marketplace.dto.PublicationEditRequest;
import com.ruoyi.opc.marketplace.dto.PublicationListQuery;
import com.ruoyi.opc.marketplace.dto.PublicationVO;
import com.ruoyi.opc.marketplace.service.AuthorService;
import com.ruoyi.opc.marketplace.service.PublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 上架商品 Controller — P1
 *
 * <p>路由：{@code /opc/marketplace/publication}
 * <ul>
 *   <li>GET  /list, /search     — 公开列表，<b>恒定只含 PUBLISHED</b>（service 层硬编码）</li>
 *   <li>GET  /{id}              — 公开详情（白名单允许匿名）→ 只返回 PUBLISHED，其余 404</li>
 *   <li>GET  /my/{id}           — 作者读自己的任意状态商品（含 DRAFT），非本人 403</li>
 *   <li>GET  /my/list           — 作者的全部状态商品（批量 VO 装配，避免 N+1）</li>
 *   <li>POST /                 — 创建（DRAFT）</li>
 *   <li>PUT  /{id}              — 编辑（局部更新，非本人 403）</li>
 *   <li>POST /{id}/submit-audit — 提交审核（P1 自动过审 → PUBLISHED）</li>
 *   <li>POST /{id}/online       — 上架（DRAFT / DEPRECATED → PUBLISHED）</li>
 *   <li>POST /{id}/offline      — 下架（→ DEPRECATED）</li>
 * </ul>
 *
 * <p><b>租户身份一律取自登录态</b>：{@code SecurityUtils.getCompanyId()}，
 * null（账号未关联企业）→ 400，<b>禁止</b>用 0 兜底。所有 {@code /my/*} 与写操作都要先
 * 解析出当前作者的 {@code MpAuthor}；解析不到 → 403「请先申请成为作者」，
 * 绝不让 {@code author.getId()} 空指针变成 500。
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/marketplace/publication")
@RequiredArgsConstructor
public class PublicationController {

    private static final String NOT_AUTHOR = "请先申请成为作者";
    private static final String NO_TENANT = "当前账号未关联企业，请先加入或创建企业";

    private final PublicationService publicationService;
    private final AuthorService authorService;

    // ==================== 公开读 ====================

    @GetMapping("/list")
    public R<Map<String, Object>> list(PublicationListQuery query) {
        return R.ok(publicationService.list(query));
    }

    @GetMapping("/search")
    public R<Map<String, Object>> search(PublicationListQuery query) {
        return R.ok(publicationService.list(query));
    }

    /**
     * 公开详情。网关白名单 {@code /opc/marketplace/publication/{id:[0-9]+}} 允许匿名，
     * 因此<b>必须</b>走 {@code getPublishedById}（SQL 层 {@code status='PUBLISHED'}），
     * 否则匿名调用方遍历 id 就能枚举 DRAFT / PENDING / SUSPENDED 草稿。
     * 作者要读自己的 DRAFT 请走 {@code GET /my/{id}}。
     */
    @GetMapping("/{id}")
    public R<PublicationVO> get(@PathVariable Long id) {
        MpPublication p = publicationService.getPublishedById(id);
        return R.ok(publicationService.toVO(p));
    }

    // ==================== 作者读（需登录 + 校验归属） ====================

    /**
     * 作者读取自己任一状态商品（含 DRAFT/PENDING/SUSPENDED）用于编辑表单填充。
     * 需登录，不在白名单中，且会校验 authorId 归属，非作者本人 → 403。
     */
    @GetMapping("/my/{id}")
    public R<PublicationVO> myGet(@PathVariable Long id) {
        Long authorId = currentAuthorId();
        MpPublication p = publicationService.getById(id);
        if (!java.util.Objects.equals(p.getAuthorId(), authorId)) {
            throw new OpcException(403, "只能读取自己的商品", "MARKETPLACE");
        }
        return R.ok(publicationService.toVO(p));
    }

    @GetMapping("/my/list")
    public R<List<PublicationVO>> myList() {
        Long companyId = requireCompanyId();
        MpAuthor author = authorService.getByCompanyId(companyId);
        if (author == null) {
            // 未入驻 = 没有商品,不是错误(与 /my/list 的空列表语义一致)
            return R.ok(List.of());
        }
        // 走批量 VO 装配,避免 2N+1 (每行 author+category 各 1 次单查)
        return R.ok(publicationService.listByAuthorVOs(author.getId()));
    }

    // ==================== 写 ====================

    @PostMapping
    public R<PublicationVO> create(@Valid @RequestBody PublicationCreateRequest req) {
        Long authorId = currentAuthorId();
        Long userId = SecurityUtils.getUserId();
        MpPublication p = publicationService.create(req, authorId, userId);
        return R.ok(publicationService.toVO(p));
    }

    @PutMapping("/{id}")
    public R<PublicationVO> edit(@PathVariable Long id, @Valid @RequestBody PublicationEditRequest req) {
        Long authorId = currentAuthorId();
        MpPublication p = publicationService.edit(id, req, authorId);
        return R.ok(publicationService.toVO(p));
    }

    @PostMapping("/{id}/submit-audit")
    public R<PublicationVO> submitAudit(@PathVariable Long id) {
        Long authorId = currentAuthorId();
        MpPublication p = publicationService.submitAudit(id, authorId);
        return R.ok(publicationService.toVO(p));
    }

    @PostMapping("/{id}/online")
    public R<PublicationVO> online(@PathVariable Long id) {
        Long authorId = currentAuthorId();
        MpPublication p = publicationService.online(id, authorId);
        return R.ok(publicationService.toVO(p));
    }

    @PostMapping("/{id}/offline")
    public R<PublicationVO> offline(@PathVariable Long id) {
        Long authorId = currentAuthorId();
        MpPublication p = publicationService.offline(id, authorId);
        return R.ok(publicationService.toVO(p));
    }

    // ==================== 内部 ====================

    /**
     * fail-closed：未关联企业的账号不得进入租户维度操作，<b>禁止</b>用 0 兜底
     * （把「无租户」当「公司 0」继续查库会越权 / 写脏数据）。
     */
    private Long requireCompanyId() {
        Long companyId = SecurityUtils.getCompanyId();
        if (companyId == null) {
            throw new OpcException(400, NO_TENANT, "MARKETPLACE");
        }
        return companyId;
    }

    /**
     * 解析出当前登录租户的作者档案 id。
     * 无租户 → 400；已登录但未入驻 → 403。两者都是 fail-closed：
     * 不允许 authorId 为 null 流入 service（否则 {@code p.getAuthorId().equals(null)}
     * 之类的判断会走偏，或把商品挂到 author_id=0 的幽灵名下）。
     */
    private Long currentAuthorId() {
        MpAuthor author = authorService.getByCompanyId(requireCompanyId());
        if (author == null) {
            throw new OpcException(403, NOT_AUTHOR, "MARKETPLACE");
        }
        return author.getId();
    }
}
