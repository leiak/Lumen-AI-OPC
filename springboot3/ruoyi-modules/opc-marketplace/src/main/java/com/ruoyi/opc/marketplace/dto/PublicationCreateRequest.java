package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 上架商品请求（创建）
 *
 * @author OAC
 */
@Data
public class PublicationCreateRequest {
    /** ProductType: AGENT / MODULE */
    @NotBlank
    @Size(max = 16)
    private String productType;

    /** productType=AGENT 时必填 */
    private Long agentDefinitionId;

    /** productType=MODULE 时必填 */
    private Long moduleId;

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 32)
    private String categoryCode;

    @Size(max = 32)
    private String subCategoryCode;

    @Size(max = 512)
    private String summary;

    private String longDescription;

    @Size(max = 512)
    private String iconUrl;

    private List<String> screenshots;

    /** 逗号分隔 */
    @Size(max = 512)
    private String tags;
}