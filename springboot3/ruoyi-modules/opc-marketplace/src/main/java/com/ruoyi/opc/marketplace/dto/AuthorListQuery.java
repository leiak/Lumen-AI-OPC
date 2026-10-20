package com.ruoyi.opc.marketplace.dto;

import lombok.Data;

/**
 * 作者分页查询条件
 *
 * @author OAC
 */
@Data
public class AuthorListQuery {
    private String keyword;

    /** AuthorLevel: NORMAL / PREMIUM */
    private String level;

    private Integer page = 1;

    private Integer pageSize = 12;
}