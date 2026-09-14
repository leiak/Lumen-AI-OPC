package com.ruoyi.opc.content.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.feign.factory.OpcContentAiCoreGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-content → opc-ai-core 远程调用 (W74 Task 8)。
 *
 * <p>用途：4 个 LLM 场景 — {@code content_short_drama} / {@code content_video_script} /
 * {@code content_article} / {@code content_platform_adapter}。
 *
 * <p>对端 controller 在 opc-ai-core 模块 (9301)，通过 {@code @InnerAuth} 走内部 Feign 通道，
 * 由 {@code @EnableRyFeignClients} 自动注入 {@code from-source: inner} header 绕过 gateway
 * AuthFilter。
 *
 * <p>降级策略：{@link OpcContentAiCoreGatewayFactory} —— LLM 失败应显式感知，
 * 返回 {@link R#fail(String)} 让业务层决定是否走缓存或人工兜底，绝不静默吞错。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcContentAiCore", name = "opc-ai-core",
        path = "/opc/llm", fallbackFactory = OpcContentAiCoreGatewayFactory.class)
public interface OpcContentAiCoreGateway {

    /**
     * 统一 LLM 调用入口 (payload 含 messages/temperature/scene/maxTokens)。
     *
     * <p>payload schema:
     * <pre>
     * {
     *   "scene": "content_short_drama",   // 场景标识
     *   "temperature": 0.8,                // 温度
     *   "maxTokens": 3000,                 // 最大 token 数
     *   "messages": [
     *     {"role": "system", "content": "..."},
     *     {"role": "user", "content": "..."}
     *   ]
     * }
     * </pre>
     *
     * <p>scene 取值：{@code content_short_drama} / {@code content_video_script} /
     * {@code content_article} / {@code content_platform_adapter}.
     */
    @PostMapping("/chat")
    R<Map<String, Object>> chat(@RequestBody Map<String, Object> payload);
}
