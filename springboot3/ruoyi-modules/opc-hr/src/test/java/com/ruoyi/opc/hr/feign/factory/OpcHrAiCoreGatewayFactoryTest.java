package com.ruoyi.opc.hr.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.OpcHrAiCoreGateway;
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
 * OpcHrAiCoreGatewayFactory 单测（Task 8 — 2 cases）。
 *
 * <p>验证 LLM 降级语义：返回 {@link R#fail(String)}（code=500），绝不能静默成功
 * —— 否则业务层会误判「LLM 已生成」并写入下游。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcHrAiCoreGatewayFactory 单测 (2 cases)")
class OpcHrAiCoreGatewayFactoryTest {

    private final OpcHrAiCoreGatewayFactory factory = new OpcHrAiCoreGatewayFactory();

    /** Test 1 */
    @Test
    @DisplayName("chat 降级返回 R.fail(code=500),业务侧应感知 LLM 不可用")
    void chat_fallbackReturnsFail() {
        OpcHrAiCoreGateway gateway = factory.create(new RuntimeException("LLM timeout"));
        assertThat(gateway).isNotNull();

        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", "hr_candidate_score");
        payload.put("prompt", "评估候选人张三的匹配度");

        R<Map<String, Object>> result = gateway.chat(payload);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(R.FAIL);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(R.isError(result)).isTrue();
        assertThat(result.getMsg()).contains("LLM");
    }

    /** Test 2 */
    @Test
    @DisplayName("embedding 降级返回 R.fail(),embedding 用于向量召回,失败必须显式")
    void embedding_fallbackReturnsFail() {
        OpcHrAiCoreGateway gateway = factory.create(new RuntimeException("embedding service down"));

        R<Map<String, Object>> result = gateway.embedding(null);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(R.FAIL);
        assertThat(result.getMsg()).isNotBlank();
    }
}
