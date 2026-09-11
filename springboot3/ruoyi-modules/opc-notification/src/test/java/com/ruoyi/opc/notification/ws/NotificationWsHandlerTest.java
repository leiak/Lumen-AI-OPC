package com.ruoyi.opc.notification.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationWsHandlerTest {

    private WsSessionRegistry registry;
    private NotificationWsHandler handler;

    @BeforeEach
    void setUp() {
        registry = new WsSessionRegistry();
        handler = new NotificationWsHandler(registry);
    }

    @Test
    void afterConnectionEstablished_registersSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of("userId", 1L));
        when(session.getId()).thenReturn("sess-1");

        handler.afterConnectionEstablished(session);

        Set<WebSocketSession> sessions = registry.sessionsForUser(1L);
        assertEquals(1, sessions.size());
        assertTrue(sessions.contains(session));
    }

    @Test
    void afterConnectionEstablished_noUserId_closesSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of());
        when(session.getId()).thenReturn("sess-1");

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Test
    void afterConnectionClosed_unregistersSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of("userId", 2L));
        when(session.getId()).thenReturn("sess-2");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertEquals(0, registry.sessionsForUser(2L).size());
    }

    @Test
    void handleTextMessage_isNoOp() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        // No assertions needed — just verify no exception thrown
        handler.handleTextMessage(session, new TextMessage("client ping"));
    }
}
