package com.ruoyi.opc.crm.gateway;

import com.ruoyi.common.core.domain.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * opc-ai-core 降级工厂单测。
 *
 * <p>守的是一条不该被"优化掉"的语义：<b>对端挂了不能变成一个编出来的分数</b>。
 * 降级成 {@code R.fail(...)} 才是对的 —— 业务层据此告诉用户"服务不可用"，
 * 商机上原有的分数一个字不动。
 */
@DisplayName("AiCoreGatewayFallback 单测")
class AiCoreGatewayFallbackTest {

    private final AiCoreGatewayFallback factory = new AiCoreGatewayFallback();

    @Test
    @DisplayName("降级返回 fail —— 绝不返回一个 0 分或任何编出来的分数")
    void fallback_should_return_fail_not_a_score() {
        AiCoreGateway gateway = factory.create(new RuntimeException("connection refused"));

        R<Map<String, Object>> resp = gateway.chat(new HashMap<>());

        assertThat(resp).isNotNull();
        assertThat(R.isSuccess(resp)).isFalse();
        assertThat(resp.getData()).isNull();
        assertThat(resp.getMsg()).contains("暂时不可用");
    }

    @Test
    @DisplayName("降级不抛异常 —— 异常要由业务层翻译成人话，不是原样冒给前端")
    void fallback_should_not_throw() {
        AiCoreGateway gateway = factory.create(new RuntimeException("timeout"));

        assertThat(gateway.chat(null)).isNotNull();
    }
}
