package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpInstall;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.feign.AgentHubClient;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.service.InstallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link InstallService} 实现 — P1 Task 6
 *
 * <p><b>分布式一致性的处理（设计文档 §6 关键陷阱 + 任务前置说明）</b>：
 * <ol>
 *   <li>远端（{@link AgentHubClient#createAgentInstance}）失败 → 503，本地无任何写入，
 *       没有不一致状态。</li>
 *   <li>远端成功 → 本地写入失败（极少：network race / DB 锁 / 服务器 OOM）→
 *       <b>best-effort 调</b> {@link AgentHubClient#revokeAgentInstance} 把远端实例置
 *       {@code REVOKED}，避免「幽灵 AgentInstance + 无 mp_install 行」。
 *       revoke 失败时 log ERROR 一行带 {@code (instanceId, publicationId, userId)} 三元组，
 *       运维可以按 {@code opc_agent_instance} 的定义 id 反查回收（orphan 没有 mp_install 行
 *       关联,但其 definition_id 与 publication.agentDefinitionId 严格对应,可定位）。</li>
 *   <li>本地写入顺序：先 {@code selectUnique} → ACTIVE → 409；非 ACTIVE 才走到远端。
 *       这保证「重复安装」（用户连点两次按钮）只触发一次远端调用,而不是两次。</li>
 *   <li>{@link MpPublicationMapper#incrementInstallCount} 仅在「首次安装」时调用,
 *       「复活既有 UNINSTALLED 行」<b>不</b>调用 —— 这是市场级计数器,「卸载-重装」刷榜是
 *       真实缺陷。具体计数语义:每次 {@code mp_install} 从无到有 (status=ACTIVE) 算 +1;
 *       复活行 (deleted=0, status=ACTIVE) 是同一行的 status 翻转,不算新装。</li>
 * </ol>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallServiceImpl implements InstallService {

    private final MpInstallMapper installMapper;
    private final MpPublicationMapper publicationMapper;
    private final AgentHubClient agentHubClient;

    /** 远端回包里 {@code id} 字段的 key（opc-agent-hub {@code OpcAgentInstance.id}）。 */
    private static final String RESP_KEY_ID = "id";

    @Override
    public MpInstall install(Long publicationId, Long tenantCompanyId, Long userId) {
        // —— 1. 校验商品存在 + 已发布 ——
        MpPublication p = publicationMapper.selectById(publicationId);
        if (p == null) {
            throw new OpcException(404, "商品不存在: " + publicationId, "MARKETPLACE");
        }
        if (!"PUBLISHED".equals(p.getStatus())) {
            throw new OpcException(400, "商品未发布，无法安装", "MARKETPLACE");
        }

        // —— 2. 唯一键前置检查(uk_install 不含 deleted,软删除行也占位)——
        MpInstall existing = installMapper.selectUnique(publicationId, tenantCompanyId, userId);
        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            // 重复安装 → 409,不走到远端(避免「用户连点按钮」双计远端实例数)
            throw new OpcException(409, "已安装，请勿重复", "MARKETPLACE");
        }

        // —— 3. 仅 AGENT 商品需要远端建实例;MODULE 是纯本地登记 ——
        Long instanceId = null;
        if ("AGENT".equals(p.getProductType())) {
            instanceId = createRemoteAgentInstance(p, tenantCompanyId, userId);
        }

        // —— 4. 远端成功 → 本地复活/插入 ——
        LocalDateTime now = LocalDateTime.now();
        try {
            if (existing != null) {
                // 复活既有行(uk_install 撞键的唯一合法路径)
                installMapper.reactivate(existing.getId(), instanceId, now, String.valueOf(userId));
                existing.setAgentInstanceId(instanceId);
                existing.setStatus("ACTIVE");
                existing.setInstalledAt(now);
                // 不自增 install_count:见类注释 §4
                log.info("[Install] reinstalled (revived row) publicationId={} userId={} instanceId={}",
                        publicationId, userId, instanceId);
                return existing;
            }

            MpInstall install = new MpInstall();
            install.setPublicationId(publicationId);
            install.setTenantCompanyId(tenantCompanyId);
            install.setUserId(userId);
            install.setAgentInstanceId(instanceId);
            install.setInstallSource("MARKET");
            install.setStatus("ACTIVE");
            install.setInstalledAt(now);
            install.setCreateBy(String.valueOf(userId));
            install.setUpdateBy(String.valueOf(userId));
            installMapper.insert(install);

            // 首次安装 → 累加市场级计数器
            publicationMapper.incrementInstallCount(publicationId);
            log.info("[Install] installed publicationId={} userId={} instanceId={}",
                    publicationId, userId, instanceId);
            return install;
        } catch (RuntimeException ex) {
            // —— 5. 本地写失败 → best-effort 退订远端实例 ——
            // existing != null (复活) 时也会进这里;复活行的 reactivate 失败意味着
            // 我们既丢了实例也丢了 mp_install 行 —— 仍按 best-effort revoke + log 处理
            if (instanceId != null) {
                try {
                    agentHubClient.revokeAgentInstance(instanceId);
                    log.warn("[Install] local write failed, attempted best-effort revoke: "
                            + "publicationId={} userId={} instanceId={}",
                            publicationId, userId, instanceId);
                } catch (RuntimeException revokeEx) {
                    // 两条路径都失败 —— 这是真实的孤儿状态,日志必须能定位
                    log.error("[Install] ORPHAN AgentInstance: local write failed AND revoke failed. "
                                    + "publicationId={} userId={} instanceId={} "
                                    + "→ 需运维按 instanceId 手动清理 opc_agent_instance "
                                    + "(可按 agent_definition_id → mp_publication.agent_definition_id 反查)",
                            publicationId, userId, instanceId, revokeEx);
                }
            }
            throw ex;
        }
    }

    /**
     * 调远端建 Agent 实例；远端失败 → 503 由 {@link AgentHubClientFallback} 抛出。
     * 返回的 {@code instanceId} 在远端成功时非 null,本地写失败时供 best-effort 清理用。
     */
    private Long createRemoteAgentInstance(MpPublication p, Long tenantCompanyId, Long userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentDefinitionId", p.getAgentDefinitionId());
        body.put("tenantCompanyId", tenantCompanyId);
        body.put("userId", userId);
        body.put("nickname", p.getName()); // 可选:实例昵称默认跟商品名

        // AgentHubClientFallback 已把异常转译成 OpcException(503, "opc-agent-hub 调用失败,请稍后重试")
        Map<String, Object> resp = agentHubClient.createAgentInstance(body);
        if (resp == null) {
            // fallback 返回 null 通常只发生在 revoke 路径;createAgentInstance 路径 fallback 抛 503,
            // 走到这里 = 真的没拿到响应体,作为 OOM exception(500)处理
            throw new OpcException(503, "opc-agent-hub 返回空响应", "MARKETPLACE");
        }
        Object idObj = resp.get(RESP_KEY_ID);
        if (!(idObj instanceof Number)) {
            throw new OpcException(503, "opc-agent-hub 返回的实例 id 缺失或类型异常", "MARKETPLACE");
        }
        return ((Number) idObj).longValue();
    }

    @Override
    public void uninstall(Long publicationId, Long tenantCompanyId, Long userId) {
        MpInstall existing = installMapper.selectUnique(publicationId, tenantCompanyId, userId);
        if (existing == null) {
            throw new OpcException(404, "未安装", "MARKETPLACE");
        }
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new OpcException(409, "该安装记录已卸载", "MARKETPLACE");
        }
        installMapper.updateStatus(existing.getId(), "UNINSTALLED", LocalDateTime.now());
        log.info("[uninstall] publicationId={} userId={}", publicationId, userId);
    }

    @Override
    public MpInstall getInstallStatus(Long publicationId, Long tenantCompanyId, Long userId) {
        // null = 未安装;UI 用是否存在 + status=='ACTIVE' 区分 "未安装/已卸载"
        return installMapper.selectUnique(publicationId, tenantCompanyId, userId);
    }

    @Override
    public List<MpInstall> listByUser(Long userId) {
        return installMapper.selectByUser(userId);
    }

    @Override
    public List<MpInstall> listByTenant(Long tenantCompanyId) {
        return installMapper.selectByTenant(tenantCompanyId);
    }
}