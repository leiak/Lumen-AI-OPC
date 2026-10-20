package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评价创建请求
 *
 * @author OAC
 */
@Data
public class ReviewCreateRequest {
    @NotNull
    private Long publicationId;

    /** 1-5 星 */
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 1024)
    private String content;
}