package com.ruoyi.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import com.ruoyi.common.core.constant.CacheConstants;
import java.util.List;
import java.util.Set;
import com.ruoyi.common.core.constant.HttpStatus;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.TokenConstants;
import com.ruoyi.common.core.utils.JwtUtils;
import com.ruoyi.common.core.utils.ServletUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.gateway.config.properties.IgnoreWhiteProperties;
import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

/**
 * 网关鉴权
 * 
 * @author ruoyi
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered
{
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    /**
     * 白名单条目里允许出现的 method 前缀。
     * 只有冒号左边正好是其中之一时，那条条目才被解读为「只放行该 method」。
     */
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    // 排除过滤的 uri 地址，nacos自行添加
    @Autowired
    private IgnoreWhiteProperties ignoreWhite;

    @Autowired
    private RedisService redisService;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder mutate = request.mutate();

        String url = request.getURI().getPath();
        // 跳过不需要验证的路径
        if (isWhiteListed(url, request.getMethod().name(), ignoreWhite.getWhites()))
        {
            return chain.filter(exchange);
        }
        String token = getToken(request);
        if (StringUtils.isEmpty(token))
        {
            return unauthorizedResponse(exchange, "令牌不能为空");
        }
        Claims claims = JwtUtils.parseToken(token);
        if (claims == null)
        {
            return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
        }
        String userkey = JwtUtils.getUserKey(claims);
        boolean islogin = redisService.hasKey(getTokenKey(userkey));
        if (!islogin)
        {
            return unauthorizedResponse(exchange, "登录状态已过期");
        }
        String userid = JwtUtils.getUserId(claims);
        String username = JwtUtils.getUserName(claims);
        if (StringUtils.isEmpty(userid) || StringUtils.isEmpty(username))
        {
            return unauthorizedResponse(exchange, "令牌验证失败");
        }

        // 设置用户信息到请求
        addHeader(mutate, SecurityConstants.USER_KEY, userkey);
        addHeader(mutate, SecurityConstants.DETAILS_USER_ID, userid);
        addHeader(mutate, SecurityConstants.DETAILS_USERNAME, username);
        // 内部请求来源参数清除
        removeHeader(mutate, SecurityConstants.FROM_SOURCE);
        return chain.filter(exchange.mutate().request(mutate.build()).build());
    }

    /**
     * 白名单命中判定。
     *
     * <p>条目语法有两种：
     * <ul>
     *   <li>{@code /opc/xxx/**} —— 放行<b>任意</b> HTTP method（老条目都这么写，行为不变）</li>
     *   <li>{@code GET:/opc/xxx/**} —— 只放行指定 method</li>
     * </ul>
     *
     * <p><b>为什么要 method 限定</b>：{@code AuthFilter} 只按路径匹配，不区分 method。
     * 白名单里那些 {@code /**} 通配是为「公开浏览」准备的，却把同一前缀下的
     * <b>写端点一并放行</b> —— 匿名 {@code DELETE /opc/erp/product/{id}} 能真的删掉
     * 商品，{@code PUT /opc/marketplace/publication/{id}} 能改别人的商品。
     * 2026-10-23 实测：匿名 DELETE 穿过网关直达 service，只因 id 不存在才被业务层拦下。
     *
     * <p>method 前缀只在「冒号左边正好是一个已知 HTTP method」时才生效。
     * 路径本身可以含冒号（{@code /opc/user/invitations/{code:[A-HJ-KM-NP-Z2-9]{8}}}），
     * 那种冒号左边是一段路径而不是 method，不会被误当成前缀。
     *
     * @param url    请求路径
     * @param method 请求 method
     * @param whites 白名单条目
     * @return 是否命中白名单（命中即跳过 token 校验）
     */
    private boolean isWhiteListed(String url, String method, List<String> whites)
    {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(whites))
        {
            return false;
        }
        for (String entry : whites)
        {
            if (StringUtils.isEmpty(entry))
            {
                continue;
            }
            String pattern = entry;
            String requiredMethod = null;
            int colon = entry.indexOf(':');
            if (colon > 0)
            {
                String maybe = entry.substring(0, colon).trim().toUpperCase();
                // 冒号左边必须正好是一个 HTTP method —— 否则按普通路径处理
                if (HTTP_METHODS.contains(maybe))
                {
                    requiredMethod = maybe;
                    pattern = entry.substring(colon + 1).trim();
                }
            }
            if (requiredMethod != null && !requiredMethod.equalsIgnoreCase(method))
            {
                // method 不符 → 这条不适用，继续看下一条
                continue;
            }
            if (StringUtils.isMatch(pattern, url))
            {
                return true;
            }
        }
        return false;
    }

    private void addHeader(ServerHttpRequest.Builder mutate, String name, Object value)
    {
        if (value == null)
        {
            return;
        }
        String valueStr = value.toString();
        String valueEncode = ServletUtils.urlEncode(valueStr);
        mutate.header(name, valueEncode);
    }

    private void removeHeader(ServerHttpRequest.Builder mutate, String name)
    {
        mutate.headers(httpHeaders -> httpHeaders.remove(name)).build();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg)
    {
        log.error("[鉴权异常处理]请求路径:{},错误信息:{}", exchange.getRequest().getPath(), msg);
        return ServletUtils.webFluxResponseWriter(exchange.getResponse(), msg, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 获取缓存key
     */
    private String getTokenKey(String token)
    {
        return CacheConstants.LOGIN_TOKEN_KEY + token;
    }

    /**
     * 获取请求token
     */
    private String getToken(ServerHttpRequest request)
    {
        String token = request.getHeaders().getFirst(SecurityConstants.AUTHORIZATION_HEADER);
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX))
        {
            token = token.replaceFirst(TokenConstants.PREFIX, StringUtils.EMPTY);
        }
        return token;
    }

    @Override
    public int getOrder()
    {
        return -200;
    }
}