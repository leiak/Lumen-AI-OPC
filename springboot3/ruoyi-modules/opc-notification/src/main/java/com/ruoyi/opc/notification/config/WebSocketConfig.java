package com.ruoyi.opc.notification.config;

import com.ruoyi.opc.notification.ws.NotificationWsHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 /ws/notification 端点
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWsHandler notificationWsHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWsHandler, "/ws/notification")
                .addInterceptors(new AuthHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
    }
}
