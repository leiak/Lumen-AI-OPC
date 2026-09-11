package com.ruoyi.opc.hr.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.OpcHrAiCoreGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * opc-ai-core (LLM) 降级工厂（Task 8）。
 *
 * <p>策略：LLM 失败应显式感知 —— 返回 {@link R#fail(String)}，让业务层决定是否走
 * 缓存（{@code opc_hr_match_score}）或人工兜底，绝不静默吞错导致下游误判「评分完成」。
 *
 * <p>JD 生成 / 简历解析失败：service 层可提示用户「AI 暂时不可用，请稍后重试或手工填写」。
 * 候选人评分失败：service 层应跳过 LLM 评分，记录 TODO 等下次手动触发。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcHrAiCoreGatewayFactory implements FallbackFactory<OpcHrAiCoreGateway> {

    private static final String LLM_FAIL_MSG = "LLM 服务暂时不可用，请稍后重试";

    @Override
    public OpcHrAiCoreGateway create(Throwable cause) {
        log.warn("[opc-hr] ai-core fallback triggered: {}", cause.getMessage());
        return new OpcHrAiCoreGateway() {
            @Override
            public R<Map<String, Object>> chat(Map<String, Object> payload) {
                String scene = payload == null ? null : String.valueOf(payload.get("scene"));
                log.warn("[opc-hr] ai-core chat fallback scene={} cause={}", scene, cause.getMessage());
                return R.fail(LLM_FAIL_MSG);
            }

            @Override
            public R<Map<String, Object>> embedding(Map<String, Object> payload) {
                log.warn("[opc-hr] ai-core embedding fallback payload={}", payload);
                return R.fail(LLM_FAIL_MSG);
            }
        };
    }
}
