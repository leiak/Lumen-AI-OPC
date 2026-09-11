package com.ruoyi.opc.notification.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表（userId → Set&lt;WebSocketSession&gt;）
 */
@Slf4j
@Component
public class WsSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("[ws] register user={} session={}", userId, session.getId());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        log.debug("[ws] unregister user={} session={}", userId, session.getId());
    }

    public Set<WebSocketSession> sessionsForUser(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions == null ? Collections.emptySet() : sessions;
    }

    public void sendToUser(Long userId, String payload) {
        for (WebSocketSession session : sessionsForUser(userId)) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.warn("[ws] send failed user={} session={}: {}",
                        userId, session.getId(), e.getMessage());
                }
            }
        }
    }
}
