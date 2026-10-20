package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * mp_publication — 上架商品（AGENT / MODULE）
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpPublication implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    /** ProductType: AGENT / MODULE */
    private String productType;
    /** AGENT 时指向 opc_agent_definition.id */
    private Long agentDefinitionId;
    /** MODULE 时指向 opc_community_module.id */
    private Long moduleId;
    private Long authorId;
    private String code;
    private String name;
    private String categoryCode;
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
    private String auditMessage;
    private Integer viewCount;
    private Integer installCount;
    private Integer subscriptionCount;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}