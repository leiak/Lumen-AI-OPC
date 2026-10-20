package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者入驻申请请求
 *
 * @author OAC
 */
@Data
public class AuthorApplyRequest {
    /** 当前用户所属公司（取自 SecurityUtils） */
    @NotNull
    private Long authorCompanyId;

    @NotBlank
    @Size(max = 128)
    private String authorName;

    @NotBlank
    @Email
    @Size(max = 128)
    private String contactEmail;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 1024)
    private String intro;

    @Size(max = 512)
    private String avatarUrl;
}