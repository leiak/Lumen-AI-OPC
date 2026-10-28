package com.ruoyi.gateway.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.ruoyi.common.core.utils.ServletUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 自定义限流异常处理
 *
 * <p><b>以前这里返回的是 HTTP 200。</b>原实现拿到 Sentinel 的
 * {@code DefaultBlockRequestHandler} 造出来的响应（状态码 429）之后，
 * <b>把它丢掉了</b>，改用 {@code ServletUtils.webFluxResponseWriter(response, msg)} 写回 —
 * 而那个重载写的是 {@code HttpStatus.OK}，body 是 {@code R.fail(...)}（code=500）。
 *
 * <p>后果是：<b>被限流掉的请求和成功的请求，在 HTTP 层面完全一样</b>。
 * 客户端拿 200 当成功处理，监控系统里看不到一个 429，
 * 「限流到底生效没有」只能靠翻日志猜 —— 而日志里那条
 * "请求超过最大数，请稍候再试" 是 INFO 级，正常跑业务时根本没人看。
 *
 * <p>现在按 HTTP 的规矩来：被限流就是 <b>429 Too Many Requests</b>，
 * body 仍用 RuoYi 的信封（{@code code}/{@code msg}/{@code data}），
 * code 也跟着改成 429 —— body 和状态码说的是同一件事。
 *
 * @author ruoyi
 */
public class SentinelFallbackHandler implements WebExceptionHandler
{
    /** 被限流时返回给客户端的话。 */
    private static final String BLOCK_MESSAGE = "请求超过最大数，请稍候再试";

    /**
     * 写回响应。
     *
     * <p>状态码优先用 block handler 自己给的（默认 handler 给的是 429），
     * 拿不到就退到 429 —— 反正「被限流」这件事只有 429 一个正确答案。
     * <b>body 用 RuoYi 的信封</b>，不用 Sentinel 的 {@code {"code":..,"message":..}}：
     * 前端和健康检查都按 {@code code}/{@code msg} 读。
     */
    private Mono<Void> writeResponse(ServerResponse response, ServerWebExchange exchange)
    {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        try
        {
            HttpStatusCode fromHandler = response == null ? null : response.statusCode();
            if (fromHandler instanceof HttpStatus hs)
            {
                status = hs;
            }
            else if (fromHandler != null)
            {
                HttpStatus resolved = HttpStatus.resolve(fromHandler.value());
                if (resolved != null)
                {
                    status = resolved;
                }
            }
        }
        catch (Exception ignored)
        {
            // handler 没给出状态码就用 429
        }
        ServerHttpResponse raw = exchange.getResponse();
        int bodyCode = status.value();
        return ServletUtils.webFluxResponseWriter(raw, status, BLOCK_MESSAGE, bodyCode);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex)
    {
        if (exchange.getResponse().isCommitted())
        {
            return Mono.error(ex);
        }
        if (!BlockException.isBlockException(ex))
        {
            return Mono.error(ex);
        }
        return handleBlockedRequest(exchange, ex).flatMap(response -> writeResponse(response, exchange));
    }

    private Mono<ServerResponse> handleBlockedRequest(ServerWebExchange exchange, Throwable throwable)
    {
        return GatewayCallbackManager.getBlockHandler().handleRequest(exchange, throwable);
    }
}
