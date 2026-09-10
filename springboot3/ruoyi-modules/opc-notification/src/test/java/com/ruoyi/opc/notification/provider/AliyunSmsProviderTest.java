package com.ruoyi.opc.notification.provider;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AliyunSmsProviderTest {

    private Client client;
    private AliyunSmsProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = mock(Client.class);
        provider = new AliyunSmsProvider(client, "OPCNOTIFY", objectMapper);
    }

    @Test
    void send_invokesAliyunClient() throws Exception {
        SendSmsResponseBody body = new SendSmsResponseBody();
        body.setCode("OK");
        body.setMessage("OK");
        SendSmsResponse response = new SendSmsResponse();
        response.setBody(body);
        when(client.sendSmsWithOptions(any(SendSmsRequest.class), any(RuntimeOptions.class)))
            .thenReturn(response);

        Map<String, String> vars = new HashMap<>();
        vars.put("orderNo", "X001");
        vars.put("amount", "99.00");

        provider.send("13800000000", "SMS_123", vars);

        ArgumentCaptor<SendSmsRequest> captor = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).sendSmsWithOptions(captor.capture(), any(RuntimeOptions.class));
        SendSmsRequest req = captor.getValue();
        assertEquals("13800000000", req.getPhoneNumbers());
        assertEquals("SMS_123", req.getTemplateCode());
        assertEquals("OPCNOTIFY", req.getSignName());
        assertNotNull(req.getTemplateParam());
        assertTrue(req.getTemplateParam().contains("X001"));
        assertTrue(req.getTemplateParam().contains("99.00"));
    }

    @Test
    void send_alreadyError_throwsSmsSendException() throws Exception {
        SendSmsResponseBody body = new SendSmsResponseBody();
        body.setCode("isv.BUSINESS_LIMIT_CONTROL");
        body.setMessage("trigger flow control");
        SendSmsResponse response = new SendSmsResponse();
        response.setBody(body);
        when(client.sendSmsWithOptions(any(SendSmsRequest.class), any(RuntimeOptions.class)))
            .thenReturn(response);

        SmsSendException ex = assertThrows(SmsSendException.class,
            () -> provider.send("13800000000", "SMS_123", Map.of("k", "v")));
        assertTrue(ex.getMessage().contains("isv.BUSINESS_LIMIT_CONTROL"));
    }
}
