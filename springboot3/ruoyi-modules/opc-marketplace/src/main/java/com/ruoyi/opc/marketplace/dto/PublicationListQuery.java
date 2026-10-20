package com.ruoyi.opc.marketplace.dto;

import lombok.Data;

/**
 * 上架商品分页查询条件
 *
 * <p>说明: 不含 {@code status} 字段. 公开 endpoint (网关白名单) 恒定只返回 PUBLISHED;
 * 若允许调用方传 status,匿名用户可枚举 DRAFT/PENDING/SUSPENDED —— 见 Task3 审查 Critical#1.
 * 作者查看自己的全部状态请走 {@code /my/list} (需登录).
 *
 * @author OAC
 */
@Data
public class PublicationListQuery {
    private String category;

    /** ProductType: AGENT / MODULE */
    private String productType;

    private String keyword;

    /** newest / rating / installs */
    private String sortBy;

    private Integer page = 1;

    private Integer pageSize = 12;
}