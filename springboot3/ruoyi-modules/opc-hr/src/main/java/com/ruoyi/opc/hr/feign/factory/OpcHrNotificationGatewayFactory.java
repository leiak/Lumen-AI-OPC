package com.ruoyi.opc.hr.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.OpcHrNotificationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * opc-notification 降级工厂（Task 8）。
 *
 * <p>策略：通知是辅助链路（投递通知 / 面试提醒 / Offer 发送），失败不应阻塞主流程，
 * 返回 {@link R#ok()} 让业务侧继续推进；调用方可在日志 / 监控中感知异常并异步重试。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcHrNotificationGatewayFactory implements FallbackFactory<OpcHrNotificationGateway> {

    @Override
    public OpcHrNotificationGateway create(Throwable cause) {
        log.warn("[opc-hr] notification fallback triggered: {}", cause.getMessage());
        return new OpcHrNotificationGateway() {
            @Override
            public R<Void> send(Map<String, Object> payload) {
                String channel = payload == null ? null : String.valueOf(payload.get("channel"));
                log.warn("[opc-hr] notification send fallback channel={} payload={}",
                        channel, payload);
                return R.ok();
            }

            @Override
            public R<Void> broadcast(Map<String, Object> payload) {
                log.warn("[opc-hr] notification broadcast fallback payload={}", payload);
                return R.ok();
            }
        };
    }
}
