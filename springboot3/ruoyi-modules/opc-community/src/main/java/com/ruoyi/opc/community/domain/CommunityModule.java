package com.ruoyi.opc.community.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityModule implements Serializable {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String description;
    private Long ownerId;
    private String ownerName;
    private String icon;
    private BigDecimal rating;
    private Integer ratingCount;
    private Integer installCount;
    private Integer commentCount;
    private String status;
    private String tags;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
