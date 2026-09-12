package com.ruoyi.opc.erp.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.erp.feign.factory.OpcErpNotificationGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-erp → opc-notification 远程调用（Task 8）。
 *
 * <p>用途：低库存预警（email）+ 退货确认（inbox）。对端 controller 在 opc-notification 模块（9310），
 * 走 {@code @InnerAuth}（由 {@code @EnableRyFeignClients} 自动注入 {@code from-source: inner}
 * header 绕过 gateway AuthFilter）。
 *
 * <p>降级策略：{@link OpcErpNotificationGatewayFactory} —— 通知失败不影响主流程，返回 R.ok()。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcErpNotification", name = "opc-notification",
        fallbackFactory = OpcErpNotificationGatewayFactory.class)
public interface OpcErpNotificationGateway {

    /**
     * 发送邮件通知（低库存预警邮件）。
     *
     * <p>对端真实路径：{@code EmailController#send} →
     * {@code @RequestMapping("/opc/notification/email") + @PostMapping("/send")}
     *
     * @param req 含 to / subject / body 等字段
     */
    @PostMapping("/opc/notification/email/send")
    R<Void> sendEmail(@RequestBody Map<String, Object> req);

    /**
     * 发送站内信（退货确认 / 低库存提示）。
     *
     * <p>⚠️ W49 遗留问题：对端 {@code InboxController} 当前未暴露 {@code /send} 或 {@code /push}
     * POST endpoint（{@code InboxService.push} 仅在 service 层实现）。调用会以 404 失败，
     * 触发 {@link OpcErpNotificationGatewayFactory} 降级返回 {@link R#ok()}，不阻塞主流程。
     * 该问题与 opc-crm {@code NotificationGateway.pushInbox} 路径一致，待 W53+ 修复。
     *
     * @param req 含 userId / title / body 等字段
     */
    @PostMapping("/opc/notification/inbox/send")
    R<Void> sendInbox(@RequestBody Map<String, Object> req);
}