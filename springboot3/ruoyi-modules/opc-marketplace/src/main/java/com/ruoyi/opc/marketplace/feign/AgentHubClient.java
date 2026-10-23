package com.ruoyi.opc.marketplace.feign;

import com.ruoyi.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-marketplace → opc-agent-hub Feign 客户端 — P1 Task 6
 *
 * <p>用途：安装商品（{@code productType='AGENT'}）时由 opc-agent-hub 创建
 * {@code opc_agent_instance} 行，并通过 {@link #revokeAgentInstance(Long)}
 * 做孤儿实例的 best-effort 清理（远端成功 / 本地写失败时）。
 *
 * <p>对端 controller 标注 {@code @InnerAuth}（要求 {@code from-source: inner} header），
 * 由 {@code @EnableRyFeignClients} 自动注入该 header，绕过 gateway AuthFilter。
 *
 * <p>不使用 {@code url=...} 硬编码（与 {@code opc-content} 一致）：依赖 Nacos 服务发现，
 * 容器内通过 Docker DNS 名 {@code aiopc-agent-hub} 路由 ——
 * 历史上 {@code opc-crm} 曾用过 {@code url=...}，但 {@code opc-content} / 本节验证
 * Nacos 发现 + Feign 负载均衡在 Docker 网络里可用。
 *
 * <p>超时：默认 Feign 连接超时 10s、读超时 60s（{@code spring-cloud-openfeign} 默认值），
 * 对 {@code createAgentInstance} 这种「建实例 + 累加下载数 + 写 N 行」的开销足够；
 * 如需更激进收紧，单独给 {@code @FeignClient(configuration=...)} 配，不影响其他 client。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcMarketplaceAgentHub",
        name = "opc-agent-hub",
        path = "/opc/agent",
        fallbackFactory = AgentHubClientFallback.class)
public interface AgentHubClient {

    /**
     * 创建一个 Agent 实例（供市场安装商品时调用）。
     *
     * <p>request body schema（由对端 {@code OpcMarketplaceInstanceController.createAgentInstance} 解析）：
     * <pre>
     * {
     *   "agentDefinitionId": 100,     // 商品指向的 Agent 定义
     *   "tenantCompanyId":    200,     // 租户公司 id（来自登录态,非 0 兜底）
     *   "userId":             7,       // 操作者用户 id
     *   "nickname":           "可选:实例昵称"
     * }
     * </pre>
     *
     * <p>成功响应（远端 {@code R.ok} body）：
     * <pre>{@code {"code":200,"msg":"操作成功","data":{"id":12345,"instanceCode":"I...", ...}}}</pre>
     *
     * <p>⚠️ 返回类型必须是 {@code R<Map<String,Object>>} 而不是 {@code Map<String,Object>}。
     * 对端 {@code OpcMarketplaceInstanceController.create} 返回的是 {@code R.ok(resp)},
     * 实例 id 嵌在 {@code data} 里;若把整个响应体反序列化成 {@code Map},
     * {@code resp.get("id")} 恒为 null,安装会以「返回的实例 id 缺失或类型异常」失败,
     * 且远端已建好的实例变成孤儿(revoke 清理路径同样读不到 id)。
     * 与 {@code RemoteFinanceService} 等跨模块 Feign 的约定一致(W11 C1 评审修复)。
     */
    @PostMapping("/marketplace-instance/create")
    R<Map<String, Object>> createAgentInstance(@RequestBody Map<String, Object> body);

    /**
     * 退订（{@code REVOKED}）一个 Agent 实例 ——
     * 仅用于「远端成功 / 本地写失败」时 best-effort 清理孤儿实例，
     * 正常 uninstall 路径不会调它（uninstall 只把 mp_install 置 {@code UNINSTALLED}，
     * 不下架 Agent 实例,这是设计选择：实例由用户后续在 opc-agent-hub 显式退订）。
     *
     * <p>对应 server 端 {@code @InnerAuth} 端点 {@code POST /opc/agent/marketplace-instance/revoke/{id}}。
     *
     * @param id {@code opc_agent_instance.id}
     */
    /**
     * 退订（{@code REVOKED}）一个 Agent 实例 ——
     * 仅用于「远端成功 / 本地写失败」时 best-effort 清理孤儿实例，
     * 正常 uninstall 路径不会调它（uninstall 只把 mp_install 置 {@code UNINSTALLED}，
     * 不下架 Agent 实例,这是设计选择：实例由用户后续在 opc-agent-hub 显式退订）。
     *
     * <p>对应 server 端 {@code @InnerAuth} 端点 {@code POST /opc/agent/marketplace-instance/revoke/{id}}。
     * {@code body} 必须含 {@code tenantCompanyId} —— 与 install 时的 id 一致,
     * 服务端在 SQL 层校验 {@code WHERE id = #{id} AND company_id = #{tenantCompanyId}}
     * —— 0 行 → 403,避免 marketplace 跨租户误退订别家实例。
     *
     * @param id {@code opc_agent_instance.id}
     * @param body 必须含 {@code tenantCompanyId}
     */
    @PostMapping("/marketplace-instance/revoke/{id}")
    Map<String, Object> revokeAgentInstance(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);
}