package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 作者入驻申请请求
 *
 * <p><b>没有 authorCompanyId 字段</b> —— 申请方（公司）身份一律取自登录态
 * {@code SecurityUtils.getCompanyId()}，由 {@code AuthorController.apply()} 解析后
 * 作为 {@code AuthorService#apply(req, companyId, operatorId)} 的第二个入参传入。
 *
 * <p>历史上该字段在请求体里，任何已认证用户都能替<b>别家公司</b>申请入驻：
 * 由于 {@code mp_author.uk_author_company(author_company_id)} 是<b>不含 deleted 列</b>
 * 的硬唯一键，被冒名申请后别家公司自己就永远申请不了（每次 409）—— 是「占位」而非
 * 数据泄露，但足以把一家正常企业永久挡在市场之外。删除该字段后，
 * 请求体里若仍带 {@code authorCompanyId}，Spring Boot 默认关闭
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}，会被静默忽略（回归测试见
 * {@code AuthorControllerTest#apply_ignoresBodyAuthorCompanyId_usesLoginTenant}）。
 *
 * <p>其余字段（企业名 / 邮箱 / 电话 / 简介 / 头像）是申请人<b>自己的</b>资料，
 * 允许由请求体提交。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorApplyRequest implements Serializable {
    private static final long serialVersionUID = 1L;

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
