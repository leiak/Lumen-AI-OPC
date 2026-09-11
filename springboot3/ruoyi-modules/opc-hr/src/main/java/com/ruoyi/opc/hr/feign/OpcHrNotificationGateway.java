package com.ruoyi.opc.hr.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.factory.OpcHrNotificationGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-hr → opc-notification 远程调用（Task 8）。
 *
 * <p>用途：投递通知、面试提醒、Offer 发送。对端 controller 在 opc-notification 模块（9310），
 * 走 {@code @InnerAuth}，由 {@code @EnableRyFeignClients} 自动注入 {@code from-source: inner}
 * header 绕过 gateway AuthFilter。
 *
 * <p>降级策略：{@link OpcHrNotificationGatewayFactory} —— 通知失败不影响主流程，返回 R.ok()。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcHrNotification", name = "opc-notification",
        fallbackFactory = OpcHrNotificationGatewayFactory.class)
public interface OpcHrNotificationGateway {

    /**
     * 投递单条通知（email / sms / inbox 由 payload.channel 决定）。
     *
     * @param payload 含 channel/to/subject/body 等字段
     */
    @PostMapping("/opc/notification/send")
    R<Void> send(@RequestBody Map<String, Object> payload);

    /**
     * 广播通知（一对多，例如 JD 发布群发）。
     *
     * @param payload 含 channel/templateId/userIds[] 等字段
     */
    @PostMapping("/opc/notification/broadcast")
    R<Void> broadcast(@RequestBody Map<String, Object> payload);
}
