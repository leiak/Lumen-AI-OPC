package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 上架商品请求（创建）
 *
 * @author OAC
 */
@Data
public class PublicationCreateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** ProductType: AGENT / MODULE */
    @NotBlank(message = "productType 必填")
    @Size(max = 16, message = "productType 长度不能超过 16")
    @Pattern(regexp = "AGENT|MODULE", message = "商品类型只能是 AGENT 或 MODULE")
    private String productType;

    /** productType=AGENT 时必填 */
    private Long agentDefinitionId;

    /** productType=MODULE 时必填 */
    private Long moduleId;

    @NotBlank(message = "code 必填")
    @Size(max = 64, message = "code 长度不能超过 64")
    private String code;

    @NotBlank(message = "name 必填")
    @Size(max = 128, message = "name 长度不能超过 128")
    private String name;

    @NotBlank(message = "categoryCode 必填")
    @Size(max = 32, message = "categoryCode 长度不能超过 32")
    private String categoryCode;

    @Size(max = 32, message = "subCategoryCode 长度不能超过 32")
    private String subCategoryCode;

    @Size(max = 512, message = "summary 长度不能超过 512")
    private String summary;

    private String longDescription;

    @Size(max = 512, message = "iconUrl 长度不能超过 512")
    private String iconUrl;

    private List<String> screenshots;

    /** 逗号分隔 */
    @Size(max = 512, message = "tags 长度不能超过 512")
    private String tags;
}