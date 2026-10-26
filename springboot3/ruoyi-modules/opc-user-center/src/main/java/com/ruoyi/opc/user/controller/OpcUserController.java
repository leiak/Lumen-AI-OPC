package com.ruoyi.opc.user.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.service.IOpcUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OPC 用户中心 API
 *
 * @author OAC
 */
@Tag(name = "OPC 用户中心")
@RestController
@RequestMapping("/opc/user")
@RequiredArgsConstructor
public class OpcUserController extends BaseController {

    private final IOpcUserProfileService userProfileService;

    @Operation(summary = "当前用户画像")
    @GetMapping("/profile")
    public AjaxResult myProfile() {
        Long userId = SecurityUtils.getUserId();
        OpcUserProfile profile = userProfileService.getByUserId(userId);
        if (profile == null) {
            profile = new OpcUserProfile();
            profile.setUserId(userId);
        }
        return success(profile);
    }

    @Operation(summary = "创建/更新画像")
    @PostMapping("/profile")
    public AjaxResult saveProfile(@RequestBody OpcUserProfile profile) {
        Long userId = SecurityUtils.getUserId();
        profile.setUserId(userId);
        userProfileService.createOrUpdate(profile);
        return success(profile);
    }

    @Operation(summary = "我的公司列表")
    @GetMapping("/companies")
    public AjaxResult myCompanies() {
        Long userId = SecurityUtils.getUserId();
        return success(userProfileService.listCompaniesByOwner(userId));
    }

    @Operation(summary = "创建公司")
    @PostMapping("/company")
    public AjaxResult createCompany(@RequestBody OpcCompanyProfile company) {
        Long userId = SecurityUtils.getUserId();
        company.setOwnerUserId(userId);
        Long id = userProfileService.createCompany(company);
        return success(Map.of("companyId", id));
    }

    @Operation(summary = "公司详情")
    @GetMapping("/company/{id}")
    public AjaxResult getCompany(@PathVariable Long id) {
        OpcCompanyProfile company = userProfileService.getCompany(id);
        if (company != null) {
            requireCompanyAccess(company);
        }
        return success(company);
    }

    @Operation(summary = "更新公司")
    @PutMapping("/company")
    public AjaxResult updateCompany(@RequestBody OpcCompanyProfile company) {
        if (company == null || company.getId() == null) {
            return error("公司 id 不能为空");
        }
        // 更新按 id 定位 —— 不校验归属的话, 任何登录用户改一下 id 就能改别家的公司资料
        // (营业执照号、税号、法人、联系方式都在这张表里)。
        requireCompanyAccess(company);
        return success(userProfileService.updateCompany(company) > 0);
    }

    /**
     * 校验「调用方有权访问这家公司」。
     *
     * <p>公司档案是按 id 取数的，客户端能改的只有 id。公司档案里有营业执照号、
     * 税号、法人、联系电话、邮箱 —— 不校验归属就等于任何登录用户可读可改别家公司。</p>
     *
     * <p>放行两种身份：<b>所有者</b>（{@code owner_user_id}）与
     * <b>在职成员</b>（{@code opc_company_member.status='ACTIVE'}）——
     * 后者与登录期租户解析（{@code UserCompanyResolver}）用的是同一张表，口径一致。</p>
     */
    private void requireCompanyAccess(OpcCompanyProfile company) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("未关联企业, 无法访问公司资料", 403);
        }
        if (userId.equals(company.getOwnerUserId())) {
            return;
        }
        if (userProfileService.isCompanyMember(company.getId(), userId)) {
            return;
        }
        throw new ServiceException("无权访问其它公司的资料", 403);
    }

    @Operation(summary = "用户中心首页聚合")
    @GetMapping("/home")
    public AjaxResult home() {
        Long userId = SecurityUtils.getUserId();
        OpcUserProfile profile = userProfileService.getByUserId(userId);
        List<OpcCompanyProfile> companies = userProfileService.listCompaniesByOwner(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("profile", profile);
        data.put("companies", companies);
        return success(data);
    }

    // ==================== INSIGHT 聚合端点（M4 INSIGHT 拉取公司档案） ====================
    // C2 (W11 评审修复)：@InnerAuth 防止公网直接命中 —— 由 FeignRequestInterceptor 无条件注入 from-source: inner（commit c833fd6 修复，此前从未注入）。
    // C1 (W11 评审修复)：controller 级 @ExceptionHandler 把 OpcException 转成 R.fail(code, msg)，
    //   避免全局 advice 把响应体转成 AjaxResult 导致 Feign 侧 R<OpcCompanyProfile> 解析失败。
    //
    // 命名规则：plan 草稿里把 endpoint 命名为 /opc/user/companies/profile（与 List 风格保持一致），
    // 实际读 opc_company_profile 表 by id —— opc-insight 拿到后自己用 createTime 算 activeDays。

    @Operation(summary = "公司档案（INSIGHT KPI 专用）")
    @InnerAuth
    @GetMapping("/companies/profile")
    public R<OpcCompanyProfile> companyProfile(@RequestParam Long companyId) {
        OpcCompanyProfile p = userProfileService.getCompany(companyId);
        if (p == null) {
            return R.fail(404, "公司不存在: companyId=" + companyId);
        }
        return R.ok(p);
    }

    /**
     * INSIGHT 端点专用：{@link OpcException} → {@link R#fail(int, String)}，避免被全局 advice
     * 转成 AjaxResult。
     */
    @ExceptionHandler(OpcException.class)
    public R<Void> handleOpcException(OpcException e) {
        return R.fail(e.getCode().intValue(), e.getMessage());
    }

}
