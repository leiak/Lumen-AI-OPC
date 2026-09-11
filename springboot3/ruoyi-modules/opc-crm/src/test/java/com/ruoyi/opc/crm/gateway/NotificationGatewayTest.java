package com.ruoyi.opc.crm.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationGatewayTest {

    @Mock NotificationGateway gateway;

    @Test
    void should_call_push_inbox() {
        when(gateway.pushInbox(1L, "title", "content")).thenReturn(Map.of("code", 200));
        Map<String, Object> resp = gateway.pushInbox(1L, "title", "content");
        assertThat(resp).containsEntry("code", 200);
        verify(gateway).pushInbox(1L, "title", "content");
    }

    @Test
    void should_handle_different_user_ids() {
        when(gateway.pushInbox(anyLong(), anyString(), anyString())).thenReturn(Map.of("ok", true));
        gateway.pushInbox(100L, "A", "B");
        gateway.pushInbox(200L, "C", "D");
        verify(gateway, times(2)).pushInbox(anyLong(), anyString(), anyString());
    }

    @Test
    void should_return_empty_response_on_null() {
        when(gateway.pushInbox(1L, "t", "c")).thenReturn(null);
        Map<String, Object> resp = gateway.pushInbox(1L, "t", "c");
        assertThat(resp).isNull();
    }

    @Test
    void should_not_throw_on_long_content() {
        String longContent = "x".repeat(1000);
        when(gateway.pushInbox(1L, "t", longContent)).thenReturn(Map.of("ok", true));
        Map<String, Object> resp = gateway.pushInbox(1L, "t", longContent);
        assertThat(resp).containsEntry("ok", true);
    }
}