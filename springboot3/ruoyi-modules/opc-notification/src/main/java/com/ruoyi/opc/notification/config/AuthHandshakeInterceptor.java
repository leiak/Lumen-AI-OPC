package com.ruoyi.opc.notification.config;

import com.ruoyi.common.core.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权（从 query param ?token= 提取 userId）
 *
 * 为什么用 query param：WebSocket spec 不支持自定义 header，浏览器原生 API 只能传 query string
 */
@Slf4j
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            token = servletRequest.getServletRequest().getParameter("token");
        }
        if (token == null || token.isEmpty()) {
            log.warn("[ws] handshake rejected: missing token");
            return false;
        }
        try {
            String userIdStr = JwtUtils.getUserId(token);
            if (userIdStr == null || userIdStr.isEmpty()) {
                log.warn("[ws] handshake rejected: token missing userId claim");
                return false;
            }
            Long userId = Long.parseLong(userIdStr);
            attributes.put("userId", userId);
            return true;
        } catch (Exception e) {
            log.warn("[ws] handshake rejected: invalid token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
