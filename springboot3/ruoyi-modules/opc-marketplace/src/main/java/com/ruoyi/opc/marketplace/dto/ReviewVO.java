package com.ruoyi.opc.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价响应（含评价人冗余字段）
 *
 * @author OAC
 */
@Data
public class ReviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long publicationId;
    private Long userId;
    /** 冗余自 sys_user */
    private String userName;
    private String userAvatar;
    /** 1-5 星 */
    private Integer rating;
    private String content;
    /** VISIBLE / HIDDEN */
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}