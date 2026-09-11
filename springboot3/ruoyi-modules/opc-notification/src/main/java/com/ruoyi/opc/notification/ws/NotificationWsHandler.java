package com.ruoyi.opc.notification.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 通知 WebSocket 处理器（server-push 模式）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWsHandler extends TextWebSocketHandler {

    private final WsSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            log.warn("[ws] connection without userId, closing: {}", session.getId());
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ignored) {
            }
            return;
        }
        registry.register(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            registry.unregister(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client → server messages are no-op for now (server-push only)
        log.debug("[ws] received from {}: {}", session.getId(), message.getPayload());
    }
}
