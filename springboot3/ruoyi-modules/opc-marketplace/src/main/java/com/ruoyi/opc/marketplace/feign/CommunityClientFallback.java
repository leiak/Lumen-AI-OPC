package com.ruoyi.opc.marketplace.feign;

import com.ruoyi.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link CommunityClient} 降级工厂 — W80 P2
 *
 * <p><b>与 {@link AgentHubClientFallback} 的降级语义刻意相反。</b>
 *
 * <p>agent-hub 降级 → 抛 503，整个安装失败：实例没建出来，「装成功」是假的。
 *
 * <p>community 降级 → 返回一个失败的 {@code R}，<b>不抛异常</b>，让安装流程继续走完：
 * 社区的 {@code install_count} 只是一个展示计数，它没涨只说明这次计数没记上，
 * 不说明模块没装。让一个展示计数把整个安装操作回滚，比数字不准更糟。
 * 调用方（{@code InstallServiceImpl}）看到失败码只记一行 WARN。
 *
 * <p><b>为什么不把 cause 的 message 吐给客户端</b>：Feign cause 经常是
 * {@code FeignException}，message 形如
 * {@code "[404] during [POST] to [http://aiopc-community:9316/opc/community/...] [<html>...</html>]"}
 * —— 会把内网地址和 HTML 错误页一起端到用户面前。堆栈只进日志。
 *
 * @author OAC
 */
@Slf4j
@Component
public class CommunityClientFallback implements FallbackFactory<CommunityClient> {

    /** 降级时返回的固定文案 —— 不含内网地址、不含上游堆栈。 */
    private static final String DEGRADED_MSG = "opc-community 调用失败,社区安装计数未更新";

    @Override
    public CommunityClient create(Throwable cause) {
        // ERROR 一行带完整堆栈,运维能定位;响应只吐固定文案
        log.error("[CommunityClient] fallback triggered: cause={} msg={}",
                cause.getClass().getSimpleName(), cause.getMessage(), cause);
        return new CommunityClient() {
            @Override
            public R<Map<String, Object>> installModule(Long id, Map<String, Object> body) {
                // 不抛异常:调用方按「失败但可降级」处理,安装照常成功
                log.warn("[CommunityClient] installModule degraded moduleId={} (社区安装计数未更新,安装本身成功)",
                        id);
                return R.fail(DEGRADED_MSG);
            }
        };
    }
}
