package com.ruoyi.opc.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作者档案响应
 *
 * @author OAC
 */
@Data
public class AuthorVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long authorCompanyId;
    private String authorName;
    private String contactEmail;
    private String intro;
    private String avatarUrl;
    /** AuthorLevel: NORMAL / PREMIUM */
    private String level;
    private Integer totalAgents;
    private Integer totalSubscribers;
    private BigDecimal totalRevenue;
    /** ACTIVE / SUSPENDED */
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}