package com.ruoyi.opc.notification.provider;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailProviderTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailProvider provider;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        provider = new SmtpEmailProvider(mailSender);
    }

    @Test
    void send_callsJavaMailSender() throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(msg);

        provider.send("to@test.com", "Subject", "<p>body</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertEquals("to@test.com", sent.getRecipients(jakarta.mail.Message.RecipientType.TO)[0].toString());
        assertEquals("Subject", sent.getSubject());
        assertTrue(sent.isMimeType("text/html") || sent.getContentType().startsWith("text/html"));
    }

    @Test
    void send_providerError_throwsEmailSendException() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new RuntimeException("SMTP connection refused")).when(mailSender).send(any(MimeMessage.class));

        try {
            provider.send("to@test.com", "Subject", "<p>body</p>");
            org.junit.jupiter.api.Assertions.fail("Expected EmailSendException");
        } catch (EmailSendException e) {
            assertTrue(e.getMessage().contains("SMTP send failed"));
            assertTrue(e.getCause() != null);
        }
    }
}