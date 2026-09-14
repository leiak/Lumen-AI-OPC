package com.ruoyi.opc.content.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.feign.OpcContentNotificationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * opc-notification 降级工厂 (W74 Task 8)。
 *
 * <p>策略：通知是辅助链路（发布成功通知、平台分发回执、AI 生成失败提醒），失败不应阻塞
 * 主流程（用户已成功发布作品），返回 {@link R#ok()} 让业务侧继续推进；调用方可在日志 /
 * 监控中感知异常并异步重试。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcContentNotificationGatewayFactory implements FallbackFactory<OpcContentNotificationGateway> {

    @Override
    public OpcContentNotificationGateway create(Throwable cause) {
        log.warn("[opc-content] notification fallback triggered: {}", cause.getMessage());
        return new OpcContentNotificationGateway() {
            @Override
            public R<Map<String, Object>> send(Map<String, Object> payload) {
                String channel = payload == null ? null : String.valueOf(payload.get("channel"));
                log.warn("[opc-content] notification send fallback channel={} payload={}",
                        channel, payload);
                return R.ok();
            }
        };
    }
}
