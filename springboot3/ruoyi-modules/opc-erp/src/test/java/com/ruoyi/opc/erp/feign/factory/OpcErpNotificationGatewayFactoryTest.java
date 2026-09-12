package com.ruoyi.opc.erp.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.erp.feign.OpcErpNotificationGateway;
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
 * OpcErpNotificationGatewayFactory 单测（Task 8 — 2 cases）。
 *
 * <p>验证降级逻辑：notification 不可达时返回 {@link R#ok()}，不阻塞主流程。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpNotificationGatewayFactory 单测 (2 cases)")
class OpcErpNotificationGatewayFactoryTest {

    private final OpcErpNotificationGatewayFactory factory = new OpcErpNotificationGatewayFactory();

    /** Test 1 */
    @Test
    @DisplayName("sendEmail 降级返回 R.ok()(通知失败不应阻塞主流程)")
    void sendEmail_fallbackReturnsOk() {
        OpcErpNotificationGateway gateway = factory.create(new RuntimeException("connection refused"));
        assertThat(gateway).isNotNull();

        Map<String, Object> req = new HashMap<>();
        req.put("to", "ops@example.com");
        req.put("subject", "低库存预警");
        req.put("body", "SKU-001 库存 5 < 阈值 10");

        R<Void> result = gateway.sendEmail(req);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        assertThat(R.isSuccess(result)).isTrue();
    }

    /** Test 2 */
    @Test
    @DisplayName("sendInbox 降级返回 R.ok(),即使 req=null 也不抛 NPE")
    void sendInbox_fallbackReturnsOkAndToleratesNullPayload() {
        OpcErpNotificationGateway gateway = factory.create(new RuntimeException("timeout"));

        R<Void> result = gateway.sendInbox(null);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        assertThat(result.getData()).isNull();
    }
}