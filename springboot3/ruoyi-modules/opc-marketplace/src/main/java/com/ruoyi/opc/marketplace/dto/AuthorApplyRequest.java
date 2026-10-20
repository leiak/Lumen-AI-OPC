package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 作者入驻申请请求
 *
 * @author OAC
 */
@Data
public class AuthorApplyRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前用户所属公司（取自 SecurityUtils） */
    @NotNull(message = "authorCompanyId 必填")
    private Long authorCompanyId;

    @NotBlank(message = "authorName 必填")
    @Size(max = 128, message = "authorName 长度不能超过 128")
    private String authorName;

    @NotBlank(message = "contactEmail 必填")
    @Email(message = "contactEmail 格式不正确")
    @Size(max = 128, message = "contactEmail 长度不能超过 128")
    private String contactEmail;

    @Size(max = 32, message = "contactPhone 长度不能超过 32")
    private String contactPhone;

    @Size(max = 1024, message = "intro 长度不能超过 1024")
    private String intro;

    @Size(max = 512, message = "avatarUrl 长度不能超过 512")
    private String avatarUrl;
}