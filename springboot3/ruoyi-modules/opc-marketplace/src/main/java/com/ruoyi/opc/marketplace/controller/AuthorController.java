package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 作者 Controller — P1
 *
 * <p>路由：{@code /opc/marketplace/author}
 * <ul>
 *   <li>POST /apply      — 入驻申请（@Valid JSR-303，撞唯一键 → 409）</li>
 *   <li>GET  /me         — 当前登录用户所属公司的作者档案（companyId 取自登录态，无入参）</li>
 *   <li>GET  /{id}       — 作者公开资料（<b>脱敏</b>，网关白名单允许匿名）</li>
 *   <li>GET  /list       — 作者分页列表（P1 占位返回空）</li>
 *   <li>GET  /{id}/publications — 作者的上架商品（P1 占位，P2 由 PublicationController 接管）</li>
 *   <li>GET  /{id}/stats — 作者统计（脱敏档案 + PUBLISHED 商品数）</li>
 * </ul>
 *
 * <p><b>/me 租户身份来源</b>：{@code LoginUser.companyId}（commit a943721）。
 * ruoyi-system 登录时经 {@code SysUserCompanyMapper} 反查 {@code opc_company_member}
 * 解析出默认公司并写入 LoginUser，随 token 下发到各服务的 SecurityContextHolder，
 * 因此 {@code SecurityUtils.getCompanyId()} 无需网关转发任何新请求头。
 * 该方法<b>不接受</b>调用方传入的 companyId —— 传参版是 IDOR，任何已认证用户
 * 都能读别家企业档案的邮箱/营收。
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
     * <p>申请方（公司）id 取自登录态 {@code SecurityUtils.getCompanyId()}，
     * 与 {@link #me()} 同源；创建者 id 取自 {@code SecurityUtils.getUserId()}
     * 写入 {@code create_by/update_by}。请求体只承载申请人<b>自己</b>的资料
     * （企业名 / 邮箱 / 电话 / 简介 / 头像）。
     *
     * <p><b>租户身份不再由请求体指定（越权修复）</b>：{@code AuthorApplyRequest}
     * 原先带 {@code authorCompanyId} 字段，任何已认证用户都能替别家公司申请入驻。
     * 由于 {@code mp_author.uk_author_company(author_company_id)} 是<b>不含 deleted 列</b>
     * 的硬唯一键，被冒名申请后别家公司自己每次都拿到 409 —— 属「永久占位」而非数据泄露，
     * 但足以把一家正常企业挡在市场之外。字段已从 DTO 删除，回归测试见
     * {@code AuthorControllerTest#apply_ignoresBodyAuthorCompanyId_usesLoginTenant}。
     *
     * <p>fail-closed：账号未关联任何企业 → {@code OpcException(400)}，
     * <b>禁止</b>用 {@code 0} 兜底（同 {@link #me()}）。
     */
    @PostMapping("/apply")
    public R<AuthorVO> apply(@Valid @RequestBody AuthorApplyRequest req) {
        Long companyId = SecurityUtils.getCompanyId();
        if (companyId == null) {
            throw new OpcException(400, "当前账号未关联企业，请先加入或创建企业", "MARKETPLACE");
        }
        Long userId = SecurityUtils.getUserId();
        var author = authorService.apply(req, companyId, userId);
        return R.ok(authorService.toVO(author));
    }

    /**
     * 当前登录用户所属公司的作者档案。
     *
     * <p><b>不接受任何 companyId 入参</b> —— 租户身份一律取自登录态
     * {@code SecurityUtils.getCompanyId()}（由 {@code opc_company_member} 在登录时解析，
     * 见 {@code LoginUser.companyId}）。历史上这里是 {@code @RequestParam Long companyId}，
     * 任何已认证用户都能传任意 companyId 读到别家企业档案（contactEmail / totalRevenue），
     * 属 IDOR。
     *
     * <p>fail-closed：账号未关联任何企业 → {@code OpcException(400)}，
     * <b>禁止</b>用 {@code 0} 兜底（把「无租户」当「公司 0」继续查库会越权/写脏数据）。
     *
     * <p>该公司尚未入驻 → {@code R.ok(null)}（不抛 404，调用方按 null 区分「未入驻」与「查询异常」）。
     */
    @GetMapping("/me")
    public R<AuthorVO> me() {
        Long companyId = SecurityUtils.getCompanyId();
        if (companyId == null) {
            throw new OpcException(400, "当前账号未关联企业，请先加入或创建企业", "MARKETPLACE");
        }
        var author = authorService.getByCompanyId(companyId);
        if (author == null) {
            return R.ok(null);
        }
        return R.ok(authorService.toVO(author));
    }

    /**
     * 作者公开资料（网关白名单 {@code /opc/marketplace/author/{id:[0-9]+}} 允许匿名）。
     *
     * <p>走 {@code toPublicVO} 而非 {@code toVO}：脱敏在 service 层，
     * 邮箱与营收对匿名调用方不可见。{@code "me"} 不匹配 {@code [0-9]+}，
     * 因此本端点不会把 {@code /me} 放进白名单。
     */
    @GetMapping("/{id}")
    public R<AuthorVO> get(@PathVariable Long id) {
        return R.ok(authorService.toPublicVO(authorService.getById(id)));
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
