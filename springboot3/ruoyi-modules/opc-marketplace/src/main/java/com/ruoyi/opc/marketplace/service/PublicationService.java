package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.dto.PublicationCreateRequest;
import com.ruoyi.opc.marketplace.dto.PublicationEditRequest;
import com.ruoyi.opc.marketplace.dto.PublicationListQuery;
import com.ruoyi.opc.marketplace.dto.PublicationVO;

import java.util.List;
import java.util.Map;

/**
 * 上架商品 Service — P1
 *
 * <p><b>两条读取路径的语义区别是本接口最容易踩的坑</b>：
 * <ul>
 *   <li>{@link #getById} —— <b>不过滤 status</b>。作者侧（编辑 / 上下架 / 我的列表）必须用它，
 *       否则作者看不到也改不了自己的 DRAFT。</li>
 *   <li>{@link #getPublishedById} —— 只返回 PUBLISHED，非 PUBLISHED 一律 404。
 *       公开端点（网关白名单 {@code /opc/marketplace/publication/{id:[0-9]+}} 允许匿名）
 *       <b>必须</b>用它，否则匿名调用方遍历 id 就能枚举出全部 DRAFT / PENDING / SUSPENDED。</li>
 * </ul>
 *
 * <p>所有写方法的 {@code authorId} 参数是「当前操作者的作者档案 id」，
 * 由 controller 从登录态 {@code SecurityUtils.getCompanyId()} → {@code AuthorService.getByCompanyId}
 * 解析得到。service 层不再查一次「这条商品是不是你的」以外的东西，归属校验一律
 * 「{@code p.getAuthorId()} 是否等于入参 authorId」，不等即 403。
 *
 * @author OAC
 */
public interface PublicationService {

    /**
     * 创建商品（初始 {@code DRAFT}）
     *
     * @param req        创建请求（{@code productType=AGENT} 须带 agentDefinitionId，{@code =MODULE} 须带 moduleId）
     * @param authorId   商品归属的作者档案 id（登录态租户解析而来）
     * @param operatorId 当前操作用户 id，写入 {@code create_by/update_by}
     * @return 新建商品（含自增 {@code id}）
     * @throws com.ruoyi.opc.common.exception.OpcException 400 类型/类目不合法、409 code 已存在
     */
    MpPublication create(PublicationCreateRequest req, Long authorId, Long operatorId);

    /**
     * 编辑商品（局部更新：请求体中为 null 的字段保持原值）
     *
     * <p>读路径走 {@link #getById}（不过滤 status）—— 作者必须能编辑自己的 DRAFT。
     * 非本人 → 403。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 不存在 / 403 非本人
     */
    MpPublication edit(Long id, PublicationEditRequest req, Long authorId);

    /**
     * 提交审核。P1 无真实审核流，等价于「上架」：{@code DRAFT → PUBLISHED} 并写 {@code publishedAt}。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 不存在 / 403 非本人 / 400 非 DRAFT 状态
     */
    MpPublication submitAudit(Long id, Long authorId);

    /**
     * 上架。P1 与 {@link #submitAudit} 同为自动过审，但<b>入参状态不同</b>：
     * 本方法接受 {@code DRAFT}（首次上架）与 {@code DEPRECATED}（下架后重新上架）。
     * 若照搬 {@code submitAudit} 的「仅 DRAFT」判定，{@code offline → online} 的
     * 往返会永久卡死在 400。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 不存在 / 403 非本人 / 400 已是 PUBLISHED 或状态不允许
     */
    MpPublication online(Long id, Long authorId);

    /**
     * 下架：{@code → DEPRECATED}（已上架的商品对市场不可见，公开详情随即 404）
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 不存在 / 403 非本人
     */
    MpPublication offline(Long id, Long authorId);

    /**
     * 作者侧任意状态读取：<b>不过滤 status</b>，用于编辑 / 上下架 / 我的列表。
     * 调用方需自行校验 authorId 与 author 归属（见 edit / submitAudit / online / offline）。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 不存在
     */
    MpPublication getById(Long id);

    /**
     * 匿名公开详情用：非 PUBLISHED 一律按 404 处理，不泄露草稿存在性。
     * 与 {@link #getById} 语义不同 —— 网关白名单 {@code /opc/marketplace/publication/{id:[0-9]+}}
     * 会绕过鉴权，必须走本方法。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 不存在或未发布
     */
    MpPublication getPublishedById(Long id);

    /**
     * domain → VO（单条路径：author + category 各 1 次 DB 命中）。
     * <p>列表路径请走实现类内部的批量重载，<b>不要</b>在循环里调本方法，否则退化成 2N+1。
     */
    PublicationVO toVO(MpPublication p);

    /**
     * 公开分页列表。<b>恒定只返回 {@code status='PUBLISHED'}</b>，不接受调用方传 status
     * （否则匿名用户可枚举 DRAFT/PENDING/SUSPENDED）。作者查看自己的全部状态请走
     * {@code GET /my/list}（需登录 + 校验归属）。
     *
     * @return {@code {rows: List<PublicationVO>, total, page, pageSize}}
     */
    Map<String, Object> list(PublicationListQuery query);

    /**
     * 作者自己的全部状态行（供 {@code /my/list} 等内部用）—— 不过滤 status。
     * 返回原始行，不做 VO 装配；VO 装配请走 {@link #listByAuthorVOs(Long)}。
     */
    List<MpPublication> listByAuthor(Long authorId);

    /**
     * 作者自己全部状态的 VO 列表，一次性批量填充 {@code authorName} + {@code categoryName}
     * （一次 {@code selectByIds} + 每个 productType 一次 {@code selectByCodes}，
     * 替代每行 2 次单查的 N+1）。
     */
    List<PublicationVO> listByAuthorVOs(Long authorId);
}
