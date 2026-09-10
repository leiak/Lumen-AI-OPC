package com.ruoyi.opc.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.notification.domain.NotificationSmsLog;
import com.ruoyi.opc.notification.mapper.NotificationSmsLogMapper;
import com.ruoyi.opc.notification.provider.SmsProvider;
import com.ruoyi.opc.notification.provider.SmsSendException;
import com.ruoyi.opc.notification.service.impl.SmsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmsServiceTest {

    private SmsProvider provider;
    private NotificationSmsLogMapper mapper;
    private SmsServiceImpl service;

    @BeforeEach
    void setUp() {
        provider = mock(SmsProvider.class);
        mapper = mock(NotificationSmsLogMapper.class);
        service = new SmsServiceImpl(provider, mapper, new ObjectMapper(), 3, 0L);
    }

    @Test
    void send_success_logsAsSent() {
        service.send("13800000000", "SMS_123", Map.of("k", "v"));

        ArgumentCaptor<NotificationSmsLog> captor = ArgumentCaptor.forClass(NotificationSmsLog.class);
        verify(mapper).insert(captor.capture());
        NotificationSmsLog log = captor.getValue();
        assertEquals("13800000000", log.getPhone());
        assertEquals("SMS_123", log.getTemplateCode());
        assertEquals(1, log.getStatus());
    }

    @Test
    void send_failureAfter3Retries_logsAsFailed() {
        doThrow(new SmsSendException("rate limit")).when(provider).send(any(), any(), any());

        assertThrows(SmsSendException.class,
            () -> service.send("13800000000", "SMS_123", Map.of("k", "v")));

        verify(provider, times(3)).send(any(), any(), any());
        ArgumentCaptor<NotificationSmsLog> captor = ArgumentCaptor.forClass(NotificationSmsLog.class);
        verify(mapper).insert(captor.capture());
        NotificationSmsLog log = captor.getValue();
        assertEquals(2, log.getStatus());
        assertEquals(3, log.getRetryCount());
    }
}
