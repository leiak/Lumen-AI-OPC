package com.ruoyi.opc.content.feign;

import com.ruoyi.common.core.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-content → opc-ai-core 远程调用 (W74 Task 6 占位 interface)。
 *
 * <p>Task 8 会在此文件加 {@code @FeignClient(contextId = "opcContentAiCore", name = "opc-ai-core",
 * fallbackFactory = OpcContentAiCoreGatewayFactory.class)} + FallbackFactory, 当前为 plain interface
 * 以满足 ContentLlmClient 的编译依赖。
 *
 * <p>scene 取值: {@code content_short_drama} / {@code content_video_script} /
 * {@code content_article} / {@code content_platform_adapter}.
 *
 * <p>对端 controller 在 opc-ai-core 模块 (9301), 走 {@code @InnerAuth}。
 *
 * <p>降级策略 (Task 8 实现): 网关不可用时返回 R.fail(), 让业务层决定是否走缓存或人工兜底, 不静默吞错。
 *
 * @author OAC
 */
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
     */
    @PostMapping("/opc/llm/chat")
    R<Map<String, Object>> chat(@RequestBody Map<String, Object> payload);
}
