package com.ruoyi.opc.content.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.feign.factory.OpcContentNotificationGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-content → opc-notification 远程调用 (W74 Task 8)。
 *
 * <p>用途：内容发布成功通知、平台分发结果回执、AI 生成失败提醒等辅助链路。
 *
 * <p>对端 controller 在 opc-notification 模块 (9310)，通过 {@code @InnerAuth} 走内部 Feign 通道，
 * 由 {@code @EnableRyFeignClients} 自动注入 {@code from-source: inner} header 绕过 gateway
 * AuthFilter。
 *
 * <p>降级策略：{@link OpcContentNotificationGatewayFactory} —— 通知失败不影响主流程，
 * 返回 {@link R#ok()} 让业务侧继续推进；调用方可在日志 / 监控中感知异常并异步重试。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcContentNotification", name = "opc-notification",
        path = "/opc/notification", fallbackFactory = OpcContentNotificationGatewayFactory.class)
public interface OpcContentNotificationGateway {

    /**
     * 发送单条通知（email / sms / inbox 由 payload.channel 决定）。
     *
     * <p>payload schema:
     * <pre>
     * {
     *   "channel": "INBOX",     // INBOX / EMAIL / SMS
     *   "userId":  12345,       // 接收方用户 ID
     *   "subject": "内容发布成功",
     *   "body":    "您创建的短剧《xxx》已发布到抖音平台"
     * }
     * </pre>
     *
     * @param payload 含 channel/userId/subject/body 等字段
     */
    @PostMapping("/send")
    R<Map<String, Object>> send(@RequestBody Map<String, Object> payload);
}
