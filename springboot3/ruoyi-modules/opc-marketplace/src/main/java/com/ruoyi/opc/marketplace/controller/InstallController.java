package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpInstall;
import com.ruoyi.opc.marketplace.service.InstallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 安装 Controller — P1 Task 6
 *
 * <p>路由：{@code /opc/marketplace/install}
 * <ul>
 *   <li>POST   /{publicationId}        — 安装（远端建实例 + 本地登记）</li>
 *   <li>DELETE /{publicationId}        — 卸载（只翻本地状态,不调远端退订实例）</li>
 *   <li>GET    /my                     — 当前用户的所有安装记录（按 installed_at DESC）</li>
 *   <li>GET    /{publicationId}/status — 单条安装状态查询</li>
 *   <li>POST   /{publicationId}/upgrade — P1 升级占位(P2 实现真实升级语义)</li>
 * </ul>
 *
 * <p><b>租户身份一律取自登录态</b>：{@code SecurityUtils.getCompanyId()}，
 * null（账号未关联企业）→ 400，<b>禁止</b>用 0 兜底（同
 * {@link AuthorController} / {@link PublicationController}，详见 Task 6 前置说明）。
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/marketplace/install")
@RequiredArgsConstructor
public class InstallController {

    private static final String NO_TENANT = "当前账号未关联企业，请先加入或创建企业";

    private final InstallService installService;

    @PostMapping("/{publicationId}")
    public R<MpInstall> install(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = requireCompanyId();
        return R.ok(installService.install(publicationId, companyId, userId));
    }

    @DeleteMapping("/{publicationId}")
    public R<Void> uninstall(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = requireCompanyId();
        installService.uninstall(publicationId, companyId, userId);
        return R.ok();
    }

    /**
     * 当前登录用户的全部安装记录。
     *
     * <p>只按 userId 过滤（不按 tenantCompanyId）—— 用户在同一公司下的所有安装都该看到；
     * 跨公司的「我的安装」不在 P1 范围。同 userId 的人在多个公司有不同安装记录的情况,
     * <code>opc-product-marketplace</code> 后续会接 {@code opc_company_member}
     * 多公司切换,本接口保留 userId 维度的简单实现。
     */
    @GetMapping("/my")
    public R<List<MpInstall>> my() {
        Long userId = SecurityUtils.getUserId();
        return R.ok(installService.listByUser(userId));
    }

    @GetMapping("/{publicationId}/status")
    public R<MpInstall> status(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = requireCompanyId();
        return R.ok(installService.getInstallStatus(publicationId, companyId, userId));
    }

    /**
     * 升级占位。P1 不实现真实升级语义（实例替换 / 配置迁移 / 计费冲账），
     * 留 endpoint 是为了让前端 P1 演示可以打通链路,P2 接 {@code AgentHubClient}
     * 真实「replace instance + carry over config」流。
     */
    @PostMapping("/{publicationId}/upgrade")
    public R<Void> upgrade(@PathVariable Long publicationId) {
        return R.ok();
    }

    /**
     * fail-closed:未关联企业的账号不得进入租户维度操作,<b>禁止</b>用 0 兜底。
     */
    private Long requireCompanyId() {
        Long companyId = SecurityUtils.getCompanyId();
        if (companyId == null) {
            throw new OpcException(400, NO_TENANT, "MARKETPLACE");
        }
        return companyId;
    }
}