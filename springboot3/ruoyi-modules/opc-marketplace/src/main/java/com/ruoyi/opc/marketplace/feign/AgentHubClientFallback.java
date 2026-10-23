package com.ruoyi.opc.marketplace.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link AgentHubClient} 降级工厂 — P1 Task 6
 *
 * <p>Feign 调用失败（连接拒绝 / 超时 / 4xx-5xx 透传）时由 Spring Cloud OpenFeign
 * 走 {@link FallbackFactory#create(Throwable)} 拿到 {@code Throwable cause}，
 * 转译成 {@link OpcException}(503)，由 {@code GlobalExceptionHandler} 序列化为
 * {@code {code:503, msg:..., data:null}} 响应。
 *
 * <p><b>为什么不在 msg 里贴 cause.getMessage()：</b>Feign cause 经常是
 * {@code FeignException}，其 message 形如
 * {@code "[503] during [POST] to [http://aiopc-agent-hub:9303/opc/agent/...] [...]  [<html><body>...</body></html>]"}
 * —— 会把内网地址、HTML 错误页、Nacos URL 都吐到客户端。一律用固定文案 + log 兜底。
 *
 * @author OAC
 */
@Slf4j
@Component
public class AgentHubClientFallback implements FallbackFactory<AgentHubClient> {

    @Override
    public AgentHubClient create(Throwable cause) {
        // ERROR 一行带完整堆栈,运维能定位;响应只吐固定文案,不让客户端看到内网地址/HTML
        log.error("[AgentHubClient] fallback triggered: cause={} msg={}",
                cause.getClass().getSimpleName(), cause.getMessage(), cause);
        return new AgentHubClient() {
            @Override
            public R<Map<String, Object>> createAgentInstance(Map<String, Object> body) {
                throw new OpcException(503, "opc-agent-hub 调用失败,请稍后重试", "MARKETPLACE");
            }

            @Override
            public Map<String, Object> revokeAgentInstance(Long id, Map<String, Object> body) {
                // revoke 是 best-effort 清理路径,失败也只 log,不抛 503 把原始异常吃掉
                log.error("[AgentHubClient] revokeAgentInstance failed (orphan not cleaned) instanceId={} body={}",
                        id, body, cause);
                // 返回 null 让调用方走正常路径(本就没期望拿到响应体)
                return null;
            }
        };
    }
}