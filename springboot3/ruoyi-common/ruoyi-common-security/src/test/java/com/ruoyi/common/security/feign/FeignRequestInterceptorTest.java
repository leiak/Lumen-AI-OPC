package com.ruoyi.common.security.feign;

import java.util.Collection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import com.ruoyi.common.core.constant.SecurityConstants;
import feign.RequestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FeignRequestInterceptor} 契约测试 —— {@code from-source: inner} 的注入。
 *
 * <p>{@code @InnerAuth} 端点（本仓库唯一的跨服务闸门）由 {@code InnerAuthAspect} 校验
 * 请求头 {@code from-source == inner}，不匹配即抛 {@code InnerAuthException}。而全平台
 * 所有 Feign 调用都只经过这一个拦截器 —— 拦截器不注入这个头，就没有任何一条 Feign 调用
 * 能合法命中 {@code @InnerAuth} 端点。
 *
 * <p>这里特意在<b>没有</b>绑定 {@code HttpServletRequest} 的情况下调用
 * {@code apply()}：定时任务 / Quartz / MQ 消费等后台发起的 Feign 调用正是最容易命中
 * {@code @InnerAuth} 的场景，而它们没有 servlet 上下文。若注入被写在
 * {@code if (httpServletRequest != null)} 守卫内，这些调用必然被拒。
 *
 * <p>外部无法伪造该头：gateway {@code AuthFilter:82} 对入站流量
 * {@code removeHeader(FROM_SOURCE)}，且外部客户端走不到本拦截器。
 *
 * @author ruoyi
 */
class FeignRequestInterceptorTest
{
    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无 servlet 请求上下文时，apply() 仍必须注入 from-source: inner")
    void apply_injectsFromSourceWhenNoServletRequest()
    {
        // 后台线程场景：确保没有 servlet 请求属性
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate requestTemplate = new RequestTemplate();
        new FeignRequestInterceptor().apply(requestTemplate);

        Collection<String> fromSource = requestTemplate.headers()
                .get(SecurityConstants.FROM_SOURCE);
        assertNotNull(fromSource,
                "FeignRequestInterceptor 必须无条件注入 from-source —— 否则 @InnerAuth 端点"
                        + "(InnerAuthAspect)会拒绝所有 Feign 调用，异常又被 fallbackFactory 静默吞掉");
        assertTrue(fromSource.contains(SecurityConstants.INNER),
                "from-source 必须包含 \"inner\"，实际为: " + fromSource);
    }

    @Test
    @DisplayName("重复 apply() 不会把 from-source 堆成多个值")
    void apply_isIdempotentOnHeaderValue()
    {
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate requestTemplate = new RequestTemplate();
        FeignRequestInterceptor interceptor = new FeignRequestInterceptor();
        interceptor.apply(requestTemplate);
        interceptor.apply(requestTemplate);

        Collection<String> fromSource = requestTemplate.headers()
                .get(SecurityConstants.FROM_SOURCE);
        assertNotNull(fromSource, "from-source 必须存在");
        assertTrue(fromSource.contains(SecurityConstants.INNER),
                "from-source 必须仍然包含 \"inner\"，实际为: " + fromSource);
    }
}