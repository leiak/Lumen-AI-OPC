package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 评价修改请求
 *
 * @author OAC
 */
@Data
@Builder
public class ReviewUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 1-5 星 */
    @NotNull(message = "rating 必填")
    @Min(value = 1, message = "rating 最小 1 星")
    @Max(value = 5, message = "rating 最大 5 星")
    private Integer rating;

    @Size(max = 1024, message = "content 长度不能超过 1024")
    private String content;
}