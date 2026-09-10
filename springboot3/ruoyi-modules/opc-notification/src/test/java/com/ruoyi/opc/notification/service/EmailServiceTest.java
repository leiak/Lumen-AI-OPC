package com.ruoyi.opc.notification.service;

import com.ruoyi.opc.notification.domain.NotificationEmailLog;
import com.ruoyi.opc.notification.mapper.NotificationEmailLogMapper;
import com.ruoyi.opc.notification.provider.EmailProvider;
import com.ruoyi.opc.notification.provider.EmailSendException;
import com.ruoyi.opc.notification.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private EmailProvider provider;
    private NotificationEmailLogMapper mapper;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        provider = mock(EmailProvider.class);
        mapper = mock(NotificationEmailLogMapper.class);
        service = new EmailServiceImpl(provider, mapper, 3, 0L);
    }

    @Test
    void send_success_logsAsSent() {
        service.send("a@b.com", "S", "<p>b</p>");

        ArgumentCaptor<NotificationEmailLog> captor = ArgumentCaptor.forClass(NotificationEmailLog.class);
        verify(mapper).insert(captor.capture());
        NotificationEmailLog log = captor.getValue();
        assertEquals("a@b.com", log.getRecipient());
        assertEquals(1, log.getStatus()); // sent
        assertEquals(0, log.getRetryCount());
    }

    @Test
    void send_failureAfter3Retries_logsAsFailed() {
        doThrow(new EmailSendException("SMTP down")).when(provider).send(any(), any(), any());

        assertThrows(EmailSendException.class,
            () -> service.send("a@b.com", "S", "<p>b</p>"));

        verify(provider, times(3)).send(any(), any(), any());
        ArgumentCaptor<NotificationEmailLog> captor = ArgumentCaptor.forClass(NotificationEmailLog.class);
        verify(mapper).insert(captor.capture());
        NotificationEmailLog log = captor.getValue();
        assertEquals(2, log.getStatus()); // failed
        assertEquals(3, log.getRetryCount());
    }

    @Test
    void send_retriesThenSucceeds() {
        doThrow(new EmailSendException("transient"))
            .doThrow(new EmailSendException("transient"))
            .doNothing()
            .when(provider).send(any(), any(), any());

        service.send("a@b.com", "S", "<p>b</p>");

        verify(provider, times(3)).send(any(), any(), any());
    }
}
