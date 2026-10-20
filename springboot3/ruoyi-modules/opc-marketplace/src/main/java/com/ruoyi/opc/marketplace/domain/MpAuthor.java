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
 * mp_author — 作者档案
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpAuthor implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long authorCompanyId;
    private String authorName;
    private String contactEmail;
    private String contactPhone;
    private String intro;
    private String avatarUrl;
    /** AuthorLevel: NORMAL / PREMIUM */
    private String level;
    private Integer totalAgents;
    private Integer totalSubscribers;
    private BigDecimal totalRevenue;
    /** ACTIVE / SUSPENDED */
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}