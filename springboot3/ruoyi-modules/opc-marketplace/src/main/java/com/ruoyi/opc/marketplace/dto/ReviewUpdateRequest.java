package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 评价修改请求 — PATCH 语义:rating 与 content 均为可选,null = 不改。
 * 若传入则必须落在合理区间(1-5 星 / ≤1024 字),与 {@link com.ruoyi.opc.marketplace.dto.PublicationEditRequest}
 * 「全部字段可选」约定保持一致。
 *
 * @author OAC
 */
@Data
@Builder
public class ReviewUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 1-5 星,可选。null = 不改;若传值必须 ≥1 且 ≤5 */
    @Min(value = 1, message = "rating 最小 1 星")
    @Max(value = 5, message = "rating 最大 5 星")
    private Integer rating;

    @Size(max = 1024, message = "content 长度不能超过 1024")
    private String content;
}