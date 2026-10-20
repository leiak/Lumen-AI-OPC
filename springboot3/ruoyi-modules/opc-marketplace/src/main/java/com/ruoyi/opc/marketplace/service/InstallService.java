package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpInstall;

import java.util.List;

/**
 * 安装记录 Service — P1 Task 6
 *
 * <p>设计要点（与 Task 6 前置说明一并阅读）：
 * <ul>
 *   <li>{@code install} 走「远端先 / 本地后」：先 {@link #install} 在 opc-agent-hub 建实例，
 *       再在 mp_install 表写 / 复活行。本地写失败时 <b>best-effort 调用</b>
 *       {@code AgentHubClient.revokeAgentInstance(...)} 退订远端实例，
 *       避免「幽灵 AgentInstance + 无安装记录」的孤儿状态。
 *       这是分布式事务做不到 0/0/0 时唯一可接受的折中：远端多花一次 HTTP 调用
 *       换本地的强一致视觉。</li>
 *   <li>{@link #install} 不自增 {@code incrementInstallCount} —— 那是「市场累计安装数」计数器，
 *       <b>复活既有 UNINSTALLED 行时不算一次新安装</b>（避免「卸载-重装」刷榜）。
 *       仅 {@code existing == null}（首次安装）才 +1。详见 {@link com.ruoyi.opc.marketplace.service.impl.InstallServiceImpl}。</li>
 *   <li>{@link #uninstall} <b>不调</b> agent-hub 退订实例：设计选择是把 mp_install 与实例生命周期解耦，
 *       实例由用户在 opc-agent-hub 显式退订；这里只是「不再以商品视角持有」。</li>
 *   <li>{@code tenantCompanyId} 永远来自登录态 ({@code SecurityUtils.getCompanyId()})，
 *       controller 已 fail-closed（null → 400）。service 层不做 0 兜底。</li>
 * </ul>
 *
 * @author OAC
 */
public interface InstallService {

    /**
     * 安装（首次安装 → INSERT；既有 UNINSTALLED → reactivate）。
     *
     * <p>{@code tenantCompanyId} 与 {@code userId} 一律来自 controller 的登录态解析，
     * service 层不再校验非空 —— 但调用方要保证 null 不流入。
     *
     * @throws com.ruoyi.opc.common.exception.OpcException 404 商品不存在 / 400 商品未发布 /
     *                                              409 已安装 / 503 远端失败
     */
    MpInstall install(Long publicationId, Long tenantCompanyId, Long userId);

    /**
     * 卸载（{@code ACTIVE → UNINSTALLED}）。{@code exists} 为 null → 404；非 ACTIVE → 409。
     *
     * <p>不调 agent-hub 退订实例（设计选择，详见接口注释）。
     */
    void uninstall(Long publicationId, Long tenantCompanyId, Long userId);

    /**
     * 单条安装状态查询（供 {@code GET /opc/marketplace/install/{publicationId}/status}）。
     * 返回 {@code null} 表示「未安装」(语义上不算 404 —— 状态查询)。
     */
    MpInstall getInstallStatus(Long publicationId, Long tenantCompanyId, Long userId);

    /**
     * 当前用户全部安装记录（按 {@code installed_at} 倒序）。{@code userId} null → 400。
     */
    List<MpInstall> listByUser(Long userId);

    /**
     * 当前租户（公司）全部安装记录。
     */
    List<MpInstall> listByTenant(Long tenantCompanyId);
}