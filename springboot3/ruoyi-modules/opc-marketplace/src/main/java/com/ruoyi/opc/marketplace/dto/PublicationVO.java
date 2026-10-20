package com.ruoyi.opc.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上架商品响应（列表 / 详情，含作者与分类冗余字段）
 *
 * @author OAC
 */
@Data
public class PublicationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    /** ProductType: AGENT / MODULE */
    private String productType;
    private Long agentDefinitionId;
    private Long moduleId;
    private Long authorId;
    /** 冗余自 mp_author */
    private String authorName;
    private String authorAvatar;
    private String code;
    private String name;
    private String categoryCode;
    /** 冗余自 mp_category */
    private String categoryName;
    private String subCategoryCode;
    private String summary;
    private String longDescription;
    private String iconUrl;
    /** JSON string */
    private String screenshots;
    private String tags;
    /** PublicationStatus: DRAFT / PENDING / PUBLISHED / SUSPENDED / DEPRECATED */
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishedAt;
    private Integer viewCount;
    private Integer installCount;
    private Integer subscriptionCount;
    private BigDecimal avgRating;
    private Integer ratingCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}