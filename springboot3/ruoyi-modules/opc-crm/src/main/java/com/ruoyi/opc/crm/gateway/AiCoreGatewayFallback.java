package com.ruoyi.opc.crm.gateway;

import com.ruoyi.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * opc-ai-core (LLM) 降级工厂。
 *
 * <p>语义：<b>不编分数</b>。对端挂了 / 网络不通 → 返回 {@link R#fail(String)}，
 * 业务层把它翻译成一句「AI 评分服务暂时不可用」给用户，
 * 商机上原有的 {@code score} / {@code score_reason} 一个字都不动。
 *
 * <p>降级成「0 分」或「按金额算个分」看起来更稳，但那是拿一个<b>编出来的数</b>
 * 覆盖掉用户可能已经拿到的真实评分 —— 这个特性要修的病正是「演示环境说假话」。
 *
 * @author OAC
 */
@Slf4j
@Component
public class AiCoreGatewayFallback implements FallbackFactory<AiCoreGateway> {

    /** 对外统一的失败文案（业务层直接透给用户）。 */
    public static final String LLM_FAIL_MSG = "AI 评分服务暂时不可用，请稍后重试";

    @Override
    public AiCoreGateway create(Throwable cause) {
        log.warn("[opc-crm] ai-core fallback triggered: {}", cause.getMessage());
        return payload -> {
            log.warn("[opc-crm] ai-core chat fallback scene=crm_opportunity_score cause={}",
                    cause.getMessage());
            return R.fail(LLM_FAIL_MSG);
        };
    }
}
