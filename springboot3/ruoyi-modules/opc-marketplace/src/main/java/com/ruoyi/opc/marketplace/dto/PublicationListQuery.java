package com.ruoyi.opc.marketplace.dto;

import lombok.Data;

/**
 * 上架商品分页查询条件
 *
 * @author OAC
 */
@Data
public class PublicationListQuery {
    private String category;

    /** ProductType: AGENT / MODULE */
    private String productType;

    private String keyword;

    /** PublicationStatus，公开查询默认 PUBLISHED */
    private String status;

    /** newest / rating / installs */
    private String sortBy;

    private Integer page = 1;

    private Integer pageSize = 12;
}