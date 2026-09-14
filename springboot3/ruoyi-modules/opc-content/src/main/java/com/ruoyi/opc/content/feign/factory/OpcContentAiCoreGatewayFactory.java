package com.ruoyi.opc.content.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.feign.OpcContentAiCoreGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * opc-ai-core (LLM) 降级工厂 (W74 Task 8)。
 *
 * <p>策略：LLM 失败应显式感知 —— 返回 {@link R#fail(String)}，让业务层决定是否走
 * 缓存（{@code opc_content_draft}）或人工兜底，绝不静默吞错导致下游误判「生成完成」。
 *
 * <p>短剧 / 视频脚本 / 图文生成失败：service 层可提示用户「AI 暂时不可用，请稍后重试或手工填写」。
 * 平台适配失败：service 层应保留原文，不阻塞发布主流程。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcContentAiCoreGatewayFactory implements FallbackFactory<OpcContentAiCoreGateway> {

    private static final String LLM_FAIL_MSG = "LLM 服务暂时不可用，请稍后重试";

    @Override
    public OpcContentAiCoreGateway create(Throwable cause) {
        log.warn("[opc-content] ai-core fallback triggered: {}", cause.getMessage());
        return new OpcContentAiCoreGateway() {
            @Override
            public R<Map<String, Object>> chat(Map<String, Object> payload) {
                String scene = payload == null ? null : String.valueOf(payload.get("scene"));
                log.warn("[opc-content] ai-core chat fallback scene={} cause={}", scene, cause.getMessage());
                return R.fail(LLM_FAIL_MSG);
            }
        };
    }
}
