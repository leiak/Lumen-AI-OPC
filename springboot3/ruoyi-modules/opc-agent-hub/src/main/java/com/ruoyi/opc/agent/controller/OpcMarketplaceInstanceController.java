package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.agent.domain.OpcAgentInstance;
import com.ruoyi.opc.agent.service.IOpcAgentInstanceService;
import com.ruoyi.opc.common.exception.OpcException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 实例 — Marketplace 内部 API（W80 P1 Task 6 第二段 commit）。
 *
 * <p>专供 opc-marketplace 通过 Feign（{@code AgentHubClient}）调用的端点，标注
 * {@link InnerAuth}（要求 {@code from-source: inner} header）。
 * 网关 AuthFilter 会剥掉外部请求伪造的该 header，因此这些端点不会被公网触达。
 *
 * <p>与既有的 {@link OpcAgentController#hire(HireRequest)} / {@link OpcAgentController#instanceAction}
 * <b>不重叠</b>：
 * <ul>
 *   <li>{@link OpcAgentController#hire} 走 SecurityUtils.getUserId() 登录态，service.hire(companyId, definitionId, ...)
 *       参数对齐到 opc-agent-hub 内部对象（HireRequest），是「自服务市场」（用户自己来雇佣）。</li>
 *   <li>本 controller 不绑 securityUtils,直接吃 Feign body；用 {@code requestBody.tenantCompanyId}
 *       替代登录态 companyId，{@code requestBody.userId} 替代 {@code createBy}。
 *       这是「跨服务市场」（marketplace 替用户一键雇佣 + 写 mp_install 行）。</li>
 *   <li>{@link OpcAgentController#instanceAction} 要求登录用户操作「自己的」实例；
 *       本 controller 的 {@code revoke} 只接 instanceId，由 marketplace 兜底清理孤儿，
 *       不校验调用方是不是该实例的所有者(orphan 没有 mp_install 关联,无法用常规归属判定)。</li>
 * </ul>
 *
 * @author OAC
 */
@Tag(name = "OPC Agent Marketplace 内部 API")
@Slf4j
@RestController
@RequestMapping("/opc/agent/marketplace-instance")
@RequiredArgsConstructor
public class OpcMarketplaceInstanceController {

    private final IOpcAgentInstanceService instanceService;

    /**
     * 创建 Agent 实例（供市场安装商品时调用）。
     *
     * <p>request body schema:
     * <pre>
     * {
     *   "agentDefinitionId": 100,     // 商品指向的 Agent 定义
     *   "tenantCompanyId":    200,     // 租户公司 id(来自登录态,非 0 兜底)
     *   "userId":             7,       // 操作者用户 id;作为 createBy 写入审计列
     *   "nickname":           "可选:实例昵称"
     * }
     * </pre>
     *
     * <p>默认 {@code hireType="MONTHLY"}、{@code duration=1}（既有的 {@code hire} 方法对
     * 默认值有处理：{@code duration=null → 1}，{@code hireType=null → 进入 default 分支按月计费}）。
     * marketplace 路径上用户没有显式选择计费档位的入口（P2 再加）,先按月度单周期出实例。
     */
    @Operation(summary = "Marketplace 创建一个 Agent 实例")
    @InnerAuth
    @PostMapping("/create")
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long agentDefinitionId = asLong(body, "agentDefinitionId");
        Long tenantCompanyId = asLong(body, "tenantCompanyId");
        Long userId = asLong(body, "userId");
        String nickname = asString(body, "nickname");
        if (agentDefinitionId == null || tenantCompanyId == null) {
            // 400 而非 500 — 这是 marketplace 调用方契约错,不算本服务 bug
            throw new OpcException(400, "agentDefinitionId 与 tenantCompanyId 必填", "AGENT");
        }

        OpcAgentInstance inst = instanceService.hire(
                tenantCompanyId, agentDefinitionId, "MONTHLY", 1, nickname);
        log.info("[Marketplace] instance created instanceCode={} companyId={} definitionId={} by userId={}",
                inst.getInstanceCode(), tenantCompanyId, agentDefinitionId, userId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", inst.getId());
        resp.put("instanceCode", inst.getInstanceCode());
        resp.put("companyId", inst.getCompanyId());
        resp.put("definitionId", inst.getDefinitionId());
        resp.put("status", inst.getStatus());
        return R.ok(resp);
    }

    /**
     * 退订（{@code REVOKED}）一个 Agent 实例 —— 仅供 opc-marketplace 做孤儿实例的
     * best-effort 清理（远端成功 / 本地写失败时调用）。
     *
     * <p>需要传入 {@code tenantCompanyId}（请求体 JSON 字段）做租户作用域校验：
     * <ul>
     *   <li>{@code @InnerAuth} 已保证调用方是 marketplace Feign（不是公网直连）</li>
     *   <li>{@code tenantCompanyId} 是 marketplace 安装时的「同租户公司 id」,
     *       由 {@code InstallServiceImpl.createRemoteAgentInstance} 透传过来,
     *       保证 cleanup 不会误退订别家实例</li>
     *   <li>rowsAffected=0 → 403,不区分「不存在」与「不属于该租户」</li>
     * </ul>
     */
    @Operation(summary = "Marketplace 退订一个 Agent 实例(orphan 清理用)")
    @InnerAuth
    @PostMapping("/revoke/{id}")
    public R<Map<String, Object>> revoke(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        if (id == null) {
            throw new OpcException(400, "instance id 必填", "AGENT");
        }
        Long tenantCompanyId = body == null ? null : asLong(body, "tenantCompanyId");
        if (tenantCompanyId == null) {
            throw new OpcException(400, "tenantCompanyId 必填 — 防止跨租户误退订", "AGENT");
        }
        // operatorId 用 "marketplace" 以便审计日志能区分清理路径
        int n = instanceService.revoke(id, tenantCompanyId, "marketplace");
        log.warn("[Marketplace] instance revoked (orphan cleanup) instanceId={} tenantCompanyId={} rows={}",
                id, tenantCompanyId, n);
        return R.ok(Map.of("id", id, "rowsAffected", n));
    }

    private static Long asLong(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String asString(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }
}