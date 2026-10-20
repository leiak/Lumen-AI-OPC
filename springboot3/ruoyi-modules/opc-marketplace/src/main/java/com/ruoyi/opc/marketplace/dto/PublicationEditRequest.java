package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 上架商品请求（编辑，全部字段可选）
 *
 * @author OAC
 */
@Data
public class PublicationEditRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @Size(max = 128, message = "name 长度不能超过 128")
    private String name;

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