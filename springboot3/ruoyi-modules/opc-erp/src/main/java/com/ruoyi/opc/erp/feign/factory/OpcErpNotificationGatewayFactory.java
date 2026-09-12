package com.ruoyi.opc.erp.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.erp.feign.OpcErpNotificationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * opc-notification 降级工厂（Task 8）。
 *
 * <p>策略：通知是辅助链路（低库存预警 / 退货确认），失败不应阻塞主流程，
 * 返回 {@link R#ok()} 让业务侧继续推进；调用方可在日志 / 监控中感知异常并异步重试。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcErpNotificationGatewayFactory implements FallbackFactory<OpcErpNotificationGateway> {

    @Override
    public OpcErpNotificationGateway create(Throwable cause) {
        log.warn("[opc-erp] notification fallback triggered: {}", cause.getMessage());
        return new OpcErpNotificationGateway() {
            @Override
            public R<Void> sendEmail(Map<String, Object> req) {
                String to = req == null ? null : String.valueOf(req.get("to"));
                log.warn("[opc-erp] sendEmail fallback (no-op) to={}", to);
                return R.ok();
            }

            @Override
            public R<Void> sendInbox(Map<String, Object> req) {
                Object userId = req == null ? null : req.get("userId");
                log.warn("[opc-erp] sendInbox fallback (no-op) userId={}", userId);
                return R.ok();
            }
        };
    }
}