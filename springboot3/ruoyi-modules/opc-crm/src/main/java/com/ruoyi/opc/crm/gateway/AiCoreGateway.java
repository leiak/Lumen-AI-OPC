package com.ruoyi.opc.crm.gateway;

import com.ruoyi.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-crm → opc-ai-core (LLM) 远程调用。
 *
 * <p><b>这个接口此前指向一个从没被实现过的地址。</b>原来写的是
 * {@code @FeignClient(name = "opc-ai-core", url = "${opc.crm.ai-core.base-url}")}：
 * <ul>
 *   <li>{@code url} 会<b>覆盖</b> Nacos 服务发现（Spring Cloud OpenFeign 的规则：
 *       name 和 url 同时给，url 赢），所以 {@code name="opc-ai-core"} 形同虚设；</li>
 *   <li>那个 base-url 是 {@code http://opc-ai-core:9301}，而 compose 里的服务名
 *       是 {@code aiopc-ai-core} —— {@code opc-ai-core} 这个主机名在容器网里
 *       根本解析不了；</li>
 *   <li>就算解析得了，{@code POST /opc/ai-core/opportunity/score} 这个端点
 *       <b>从来不存在</b>：opc-ai-core 里唯一的控制器是 {@code OpcLlmController}
 *       （{@code /opc/llm}），只有 {@code POST /chat} 和 {@code GET /models}。</li>
 * </ul>
 *
 * <p>结论：前端商机看板上的「LLM 评分」按钮<b>点了必定失败</b>。
 *
 * <p>现在走的是 opc-content / opc-hr 已经在用的那条路：
 * <ul>
 *   <li>不写 {@code url} → 只按 {@code name} 走 Nacos 服务发现；</li>
 *   <li>{@code path = "/opc/llm"} + {@code POST /chat} → 对端真实存在的端点；</li>
 *   <li>{@code fallbackFactory} → 对端挂掉时降级成 {@link R#fail(String)}，
 *       由业务层决定怎么告诉用户，<b>绝不静默编一个分数</b>。</li>
 * </ul>
 *
 * @author OAC
 */
@FeignClient(contextId = "opcCrmAiCore", name = "opc-ai-core",
        path = "/opc/llm", fallbackFactory = AiCoreGatewayFallback.class)
public interface AiCoreGateway {

    /**
     * 统一 LLM 调用入口。
     *
     * <p>payload schema 与 opc-content 一致：
     * <pre>
     * {
     *   "scene": "crm_opportunity_score",  // 场景标识（计量按租户归集）
     *   "temperature": 0.2,                // 评分要稳，别发挥
     *   "maxTokens": 500,
     *   "messages": [
     *     {"role": "system", "content": "..."},
     *     {"role": "user",   "content": "..."}
     *   ]
     * }
     * </pre>
     *
     * <p>返回体的 {@code data} 是 opc-ai-core 的 {@code ChatResponse} 反序列化成的
     * Map，模型输出的文本在 {@code data.content}。
     */
    @PostMapping("/chat")
    R<Map<String, Object>> chat(@RequestBody Map<String, Object> payload);
}
