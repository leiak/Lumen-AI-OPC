package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.service.AuthorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 作者 Controller — P1
 *
 * <p>路由：{@code /opc/marketplace/author}
 * <ul>
 *   <li>POST /apply      — 入驻申请（@Valid JSR-303，撞唯一键 → 409）</li>
 *   <li>GET  /me         — 当前公司作者档案（{@code companyId} 由调用方显式传入）</li>
 *   <li>GET  /{id}       — 作者详情（公开）</li>
 *   <li>GET  /list       — 作者分页列表（P1 占位返回空）</li>
 *   <li>GET  /{id}/publications — 作者的上架商品（P1 占位，P2 由 PublicationController 接管）</li>
 *   <li>GET  /{id}/stats — 作者统计（档案 + PUBLISHED 商品数）</li>
 * </ul>
 *
 * <p><b>/me 公司 id 取值约定</b>：ruoyi-api-system 的 {@code LoginUser.getSysUser()} 没有
 * {@code companyId} 字段（{@code SysUser} 不含），不能从 token 直接拿 companyId。
 * 因此 {@code /me} 显式要求 query 参数 {@code companyId}，由调用方传入 —
 * 与 opc-billing 的 {@code GET /wallet?companyId=...} 一致。
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/marketplace/author")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    /**
     * 入驻申请
     * <p>申请方（公司）id 来自请求体（{@code AuthorApplyRequest.authorCompanyId}），
     * 创建者 id 来自 {@code SecurityUtils.getUserId()} 写入 {@code create_by/update_by}。
     */
    @PostMapping("/apply")
    public R<AuthorVO> apply(@Valid @RequestBody AuthorApplyRequest req) {
        Long userId = SecurityUtils.getUserId();
        var author = authorService.apply(req, userId);
        return R.ok(authorService.toVO(author));
    }

    /**
     * 当前公司的作者档案；未入驻 → {@code R.ok(null)}（不抛 404，调用方按 null 区分）
     */
    @GetMapping("/me")
    public R<AuthorVO> me(@RequestParam Long companyId) {
        var author = authorService.getByCompanyId(companyId);
        if (author == null) {
            return R.ok(null);
        }
        return R.ok(authorService.toVO(author));
    }

    /**
     * 作者详情（公开 — 调用方匿名也能访问，由网关白名单控制）
     */
    @GetMapping("/{id}")
    public R<AuthorVO> get(@PathVariable Long id) {
        return R.ok(authorService.toVO(authorService.getById(id)));
    }

    /**
     * 作者分页列表（P1 占位返回空，P2 接 {@code MpAuthorMapper.selectList/countList}）
     */
    @GetMapping("/list")
    public R<Map<String, Object>> list(AuthorListQuery query) {
        return R.ok(authorService.list(query));
    }

    /**
     * 作者的上架商品列表 — P1 占位，P2 由 PublicationController 接管并返回真实商品
     */
    @GetMapping("/{id}/publications")
    public R<Object> publications(@PathVariable Long id) {
        return R.ok(Map.of("rows", java.util.List.of(), "total", 0));
    }

    /**
     * 作者统计：档案 + PUBLISHED 商品数
     */
    @GetMapping("/{id}/stats")
    public R<Map<String, Object>> stats(@PathVariable Long id) {
        return R.ok(authorService.stats(id));
    }
}
