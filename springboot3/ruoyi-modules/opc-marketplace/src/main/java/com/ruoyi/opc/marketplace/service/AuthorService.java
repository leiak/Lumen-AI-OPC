package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;

import java.util.List;
import java.util.Map;

/**
 * 作者档案 Service — P1
 *
 * <p>对外契约（6 方法）：
 * <ul>
 *   <li>{@link #apply} — 入驻申请（撞 uk_author_company 唯一键 → 409）</li>
 *   <li>{@link #getByCompanyId} — 按入驻公司查（用于 {@code /me}）</li>
 *   <li>{@link #getById} — 按 id 查（不存在 → 404 OpcException）</li>
 *   <li>{@link #toVO} — domain → VO 转换（<b>含联系方式，仅供本人/内部调用</b>）</li>
 *   <li>{@link #toPublicVO} — domain → <b>脱敏</b> VO（公开端点用）</li>
 *   <li>{@link #list} — 列表（P1 内存过滤占位，P2 接 mapper 全量列表）</li>
 *   <li>{@link #stats} — 统计（作者档案 + 上架商品数）</li>
 * </ul>
 *
 * @author OAC
 */
public interface AuthorService {

    /**
     * 入驻申请
     *
     * <p>前置检查：{@code mp_author.uk_author_company(author_company_id)} 是唯一键（不含 {@code deleted} 列），
     * 若同一公司已入驻（任何状态）重复申请 → {@code OpcException(409)}。Service 层先查一次，
     * mapper 层 INSERT 撞唯一键时由 SQL 兜底（{@code 1062 Duplicate entry}）。
     *
     * @param req         入驻申请 DTO（带 JSR-303 校验）
     * @param operatorId  当前操作用户 id（来自 {@code SecurityUtils.getUserId()}），写入 {@code create_by/update_by}
     * @return 新作者档案（含自增 {@code id}）
     * @throws com.ruoyi.opc.common.exception.OpcException 409 已入驻 / 404 不存在
     */
    MpAuthor apply(AuthorApplyRequest req, Long operatorId);

    /**
     * 按入驻公司查作者；返回 {@code null} 表示该公司未入驻（不抛异常，供 {@code /me} 区分「未入驻」和「查询异常」）。
     */
    MpAuthor getByCompanyId(Long companyId);

    /**
     * 按 id 查作者；不存在 → 404 OpcException（区别于 {@link #getByCompanyId} 的 null 返回语义）。
     */
    MpAuthor getById(Long id);

    /**
     * domain → VO 映射（<b>包含 contactEmail / totalRevenue</b>）
     *
     * <p><b>只允许本人或内部流程调用</b>。任何对外的公开端点（网关白名单里的
     * {@code GET /opc/marketplace/author/{id}}）必须改用 {@link #toPublicVO(MpAuthor)}，
     * 否则匿名调用方可直接读到企业邮箱与营收。
     */
    AuthorVO toVO(MpAuthor a);

    /**
     * domain → <b>公开脱敏</b> VO：清空 {@code contactEmail}、{@code contactPhone}、{@code totalRevenue}。
     *
     * <p>脱敏放在 service 层而不是 controller 层 —— controller 里写
     * {@code vo.setContactEmail(null)} 只需一次重构就会被后人「顺手删掉」，
     * 而白名单是公开的，删掉即等于全网泄露邮箱。{@link AuthorVO} 当前<b>没有</b>
     * {@code contactPhone} 字段（domain 有），因此该字段本来就不会出现在响应里；
     * 若日后给 VO 补上该字段，必须同时在此处清空。
     *
     * @param a 作者 domain（{@code null} → 返回 {@code null}）
     * @return 脱敏后的 VO
     */
    AuthorVO toPublicVO(MpAuthor a);

    /**
     * 作者分页列表（P1: 占位实现，返回空 rows/total，P2 接 MpAuthorMapper 的全量列表）
     */
    Map<String, Object> list(AuthorListQuery query);

    /**
     * 作者统计：作者档案 + 上架商品数（{@code mp_publication.status='PUBLISHED'}）
     */
    Map<String, Object> stats(Long id);
}
