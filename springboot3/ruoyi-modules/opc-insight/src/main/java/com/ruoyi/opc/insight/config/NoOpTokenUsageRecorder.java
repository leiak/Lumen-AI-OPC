package com.ruoyi.opc.insight.config;

import com.ruoyi.opc.ai.gateway.llm.TokenMeter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * No-op {@link TokenMeter.TokenUsageRecorder} for opc-insight.
 *
 * <p><b>为什么需要</b>：{@code opc-ai-core} 的 {@code TokenMeter} 通过构造器注入
 * {@code TokenUsageRecorder}（由 {@code opc-agent-hub} 模块在运行时提供实现）。
 * {@code opc-insight} 不依赖 {@code opc-agent-hub}，因此这里提供一个无操作实现，
 * 满足 Spring 容器装配约束；{@code TokenMeter.record()} 的调用是 {@code try/catch}
 * 包住的，recorder.save() 抛异常也只会打 ERROR 日志，不会影响 LLM 调用主流程。</p>
 *
 * <p><b>如果未来 opc-insight 需要真实的 Token 计量上报</b>，可以引入
 * {@code opc-agent-hub} 的实现并把本 Bean 标记为 {@code @Primary} 或者移除本类。</p>
 *
 * @author OAC
 */
@Slf4j
@Component
public class NoOpTokenUsageRecorder implements TokenMeter.TokenUsageRecorder {

    @Override
    public void save(TokenMeter.TokenUsageRecord record) {
        // No-op: opc-insight 不落地 Token 计量数据；opc-agent-hub 在生产环境接管。
        log.debug("[NoOpTokenUsageRecorder] 丢弃计量记录 model={} tokens={}",
                record.getModel(), record.getTokenTotal());
    }
}