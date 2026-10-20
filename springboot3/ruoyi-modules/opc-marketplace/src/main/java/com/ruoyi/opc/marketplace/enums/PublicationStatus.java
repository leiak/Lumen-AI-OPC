package com.ruoyi.opc.marketplace.enums;

/**
 * 上架商品状态机
 *
 * <p><b>P1（当前实现）的可达状态只有 3 个</b>：{@code DRAFT} / {@code PUBLISHED} /
 * {@code DEPRECATED}。落到具体端点上：
 * <pre>
 *   DRAFT ──(submit-audit | online)──▶ PUBLISHED ──offline──▶ DEPRECATED
 *     │                                 ▲                       │
 *     └────────────(online)─────────────┴───────────────────────┘
 * </pre>
 *
 * <p>三条 P1 转移，与 {@code PublicationServiceImpl} 逐一对应：
 * <ul>
 *   <li>{@code DRAFT → PUBLISHED}：{@code submit-audit}（仅接受 DRAFT）与 {@code online}
 *       （接受 DRAFT / DEPRECATED）都走它。P1 无真实审核流，提交即自动过审。</li>
 *   <li>{@code PUBLISHED → DEPRECATED}：{@code offline}。</li>
 *   <li>{@code DEPRECATED → PUBLISHED}：{@code online}。<b>这条必须存在</b> ——
 *       少了它，下架的商品就永远回不了市场。</li>
 * </ul>
 * 其余来源状态一律 400 fail-closed（{@code ONLINE_FROM = {DRAFT, DEPRECATED}}）。
 *
 * <p><b>已定义但 P1 不可达</b>：{@link #PENDING} 与 {@link #SUSPENDED}。
 * P1 没有审核流，因此没有任何代码路径会写出这两个值 —— 它们是 P2 审核流的预留位。
 * 届时 {@code submit-audit} 改为 {@code DRAFT → PENDING}、平台封禁改为
 * {@code PUBLISHED → SUSPENDED}，本图随之扩写。
 * <b>不要去找现在就能设置它们的转换</b>，找不到是预期行为，不是 bug。
 *
 * @author OAC
 */
public enum PublicationStatus {
    /** 草稿（仅作者可见）。P1 的初始状态。 */
    DRAFT,
    /** 待审核。P1 不可达 —— 预留给 P2 审核流（{@code submit-audit} → PENDING）。 */
    PENDING,
    /** 已发布（市场可见）。P1 的终态，可被 {@code offline} 打到 DEPRECATED。 */
    PUBLISHED,
    /** 已封禁。P1 不可达 —— 预留给 P2 的平台封禁动作（{@code PUBLISHED → SUSPENDED}）。 */
    SUSPENDED,
    /** 已下架/废弃。{@code offline} 的产物，{@code online} 可将其送回 {@code PUBLISHED}。 */
    DEPRECATED
}