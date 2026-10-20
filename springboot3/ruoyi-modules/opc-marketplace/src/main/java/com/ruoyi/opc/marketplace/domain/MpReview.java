package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * mp_review — 评价
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpReview implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long publicationId;
    private Long tenantCompanyId;
    private Long userId;
    /** 1-5 星 */
    private Integer rating;
    private String content;
    /** VISIBLE / HIDDEN */
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}