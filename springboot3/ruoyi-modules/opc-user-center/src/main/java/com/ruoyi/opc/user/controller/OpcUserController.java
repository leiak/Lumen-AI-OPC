package com.ruoyi.opc.user.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.user.domain.OpcCompanyBrief;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import com.ruoyi.opc.user.domain.OpcCompanySwitchRequest;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.service.IOpcCompanySwitchService;
import com.ruoyi.opc.user.service.IOpcUserProfileService;
import com.ruoyi.system.api.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OPC 用户中心 API
 *
 * @author OAC
 */
@Slf4j
@Tag(name = "OPC 用户中心")
@RestController
@RequestMapping("/opc/user")
@RequiredArgsConstructor
public class OpcUserController extends BaseController {

    private final IOpcUserProfileService userProfileService;

    private final IOpcCompanySwitchService companySwitchService;

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

    @Operation(summary = "我的公司列表（我创办的）")
    @GetMapping("/companies")
    public AjaxResult myCompanies() {
        Long userId = SecurityUtils.getUserId();
        return success(userProfileService.listCompaniesByOwner(userId));
    }

    /**
     * 我加入的公司（= 可切换的租户）。
     *
     * <p>与 {@code /companies} 的区别：那个按 {@code owner_user_id} 过滤，只能看到
     * <b>自己创办</b>的公司；这个按成员关系（{@code opc_company_member}）过滤，
     * 被邀请进别人公司的员工也能看到 —— 否则多公司用户在前端只能看见默认那一家。</p>
     */
    @Operation(summary = "我加入的公司（可切换的租户）")
    @GetMapping("/company-members")
    public AjaxResult myCompanyMembers() {
        Long userId = SecurityUtils.getUserId();
        return success(companySwitchService.listMyCompanies(userId, currentCompanyId()));
    }

    /**
     * 切换当前公司（租户）。
     *
     * <p>请求体里的字段叫 {@code targetCompanyId} 而不是 {@code companyId}：
     * 后者会被租户守卫的 {@code CompanyIdBodyAdvice} 改写成「当前公司」，
     * 那样这个端点永远收到当前公司，切换就永远切不过去。</p>
     */
    @Operation(summary = "切换当前公司（租户）")
    @PostMapping("/switch-company")
    public AjaxResult switchCompany(@RequestBody OpcCompanySwitchRequest request) {
        if (request == null || request.getTargetCompanyId() == null) {
            return error("目标公司不能为空");
        }
        Long userId = SecurityUtils.getUserId();
        Long companyId = companySwitchService.switchCompany(userId, request.getTargetCompanyId());
        return success(Map.of("companyId", companyId));
    }

    @Operation(summary = "创建公司")
    @PostMapping("/company")
    public AjaxResult createCompany(@RequestBody OpcCompanyProfile company) {
        Long userId = SecurityUtils.getUserId();
        company.setOwnerUserId(userId);
        Long id = userProfileService.createCompany(company);
        // 建完公司就把当前租户切过去 —— 否则新公司对创建人自己都是「看不见的租户」
        // （登录态还停在旧公司/空公司），用户会以为没建成功。
        // 切不过去不该让建公司失败：公司已经建好了，失败只记日志。
        try {
            companySwitchService.switchCompany(userId, id);
        } catch (Exception e) {
            log.warn("[公司切换] 创建公司后切换租户失败 (companyId={}): {}", id, e.getMessage());
        }
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
     * 当前登录态所在的公司。
     *
     * <p>取自 Redis 里的登录缓存（{@code LoginUser.companyId}）——
     * 切过公司之后这里就是新公司。没有登录上下文时返回 null
     * （不用 {@code SecurityUtils.getCompanyId()}：它在没有登录上下文时会抛异常）。</p>
     */
    private Long currentCompanyId() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getCompanyId();
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
