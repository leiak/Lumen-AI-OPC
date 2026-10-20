package com.ruoyi.opc.marketplace.enums;

/**
 * 上架商品状态机
 * <pre>
 *   DRAFT --提交审核--> PENDING --审核通过--> PUBLISHED
 *                          |                    |
 *                          |--审核驳回--> DRAFT   |--下架/封禁--> SUSPENDED --再次上架--> PUBLISHED
 *                                              PUBLISHED --迭代--> DEPRECATED
 * </pre>
 *
 * @author OAC
 */
public enum PublicationStatus {
    /** 草稿（仅作者可见） */
    DRAFT,
    /** 待审核 */
    PENDING,
    /** 已发布（市场可见） */
    PUBLISHED,
    /** 已下架 / 封禁 */
    SUSPENDED,
    /** 已废弃 */
    DEPRECATED
}