package com.ruoyi.opc.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.notification.domain.NotificationInbox;
import com.ruoyi.opc.notification.mapper.NotificationInboxMapper;
import com.ruoyi.opc.notification.service.impl.InboxServiceImpl;
import com.ruoyi.opc.notification.ws.WsSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboxServiceTest {

    private NotificationInboxMapper mapper;
    private WsSessionRegistry wsRegistry;
    private InboxServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(NotificationInboxMapper.class);
        wsRegistry = mock(WsSessionRegistry.class);
        service = new InboxServiceImpl(mapper, wsRegistry, new ObjectMapper());
    }

    @Test
    void push_insertsRowAndAttemptsWsPush() {
        service.push(1L, "system", "Title", "Body", "http://x");

        ArgumentCaptor<NotificationInbox> captor = ArgumentCaptor.forClass(NotificationInbox.class);
        verify(mapper).insert(captor.capture());
        NotificationInbox row = captor.getValue();
        assertEquals("Title", row.getTitle());
        assertEquals(1L, row.getUserId());
        assertNotNull(row.getCreatedAt());
        verify(wsRegistry).sendToUser(eq(1L), contains("Title"));
    }

    @Test
    void listInbox_returnsPageResults() {
        NotificationInbox row = new NotificationInbox();
        row.setId(1L);
        row.setUserId(2L);
        row.setTitle("A");
        when(mapper.selectInboxPage(eq(2L), eq(0), eq(10))).thenReturn(List.of(row));

        List<NotificationInbox> result = service.listInbox(2L, 1, 10);

        assertEquals(1, result.size());
        assertEquals("A", result.get(0).getTitle());
    }

    @Test
    void listInbox_invalidPage_clampsToValid() {
        when(mapper.selectInboxPage(eq(2L), eq(0), eq(20))).thenReturn(List.of());

        service.listInbox(2L, 0, 0);

        verify(mapper).selectInboxPage(eq(2L), eq(0), eq(20));
    }

    @Test
    void markRead_updatesReadAt() {
        NotificationInbox row = new NotificationInbox();
        row.setId(1L);
        row.setUserId(2L);
        when(mapper.selectById(1L)).thenReturn(row);

        service.markRead(2L, 1L);

        ArgumentCaptor<NotificationInbox> captor = ArgumentCaptor.forClass(NotificationInbox.class);
        verify(mapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getReadAt());
    }

    @Test
    void markRead_otherUsersInbox_throws() {
        NotificationInbox row = new NotificationInbox();
        row.setId(1L);
        row.setUserId(99L);
        when(mapper.selectById(1L)).thenReturn(row);

        assertThrows(SecurityException.class, () -> service.markRead(2L, 1L));
    }

    @Test
    void markRead_nonExistent_throws() {
        when(mapper.selectById(999L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.markRead(2L, 999L));
    }

    @Test
    void unreadCount_returnsMapperCount() {
        when(mapper.selectUnreadCount(2L)).thenReturn(5L);

        long count = service.unreadCount(2L);

        assertEquals(5L, count);
    }
}
