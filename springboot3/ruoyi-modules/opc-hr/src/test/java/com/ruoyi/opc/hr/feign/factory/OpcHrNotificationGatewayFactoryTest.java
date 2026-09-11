package com.ruoyi.opc.hr.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.OpcHrNotificationGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpcHrNotificationGatewayFactory 单测（Task 8 — 2 cases）。
 *
 * <p>验证降级逻辑：notification 不可达时返回 {@link R#ok()}，不阻塞主流程。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcHrNotificationGatewayFactory 单测 (2 cases)")
class OpcHrNotificationGatewayFactoryTest {

    private final OpcHrNotificationGatewayFactory factory = new OpcHrNotificationGatewayFactory();

    /** Test 1 */
    @Test
    @DisplayName("send 降级返回 R.ok()(通知失败不应阻塞主流程)")
    void send_fallbackReturnsOk() {
        OpcHrNotificationGateway gateway = factory.create(new RuntimeException("connection refused"));
        assertThat(gateway).isNotNull();

        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "email");
        payload.put("to", "user@example.com");
        payload.put("subject", "面试提醒");

        R<Void> result = gateway.send(payload);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        assertThat(R.isSuccess(result)).isTrue();
    }

    /** Test 2 */
    @Test
    @DisplayName("broadcast 降级返回 R.ok(),即使 payload=null 也不抛 NPE")
    void broadcast_fallbackReturnsOkAndToleratesNullPayload() {
        OpcHrNotificationGateway gateway = factory.create(new RuntimeException("timeout"));

        R<Void> result = gateway.broadcast(null);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        assertThat(result.getData()).isNull();
    }
}
