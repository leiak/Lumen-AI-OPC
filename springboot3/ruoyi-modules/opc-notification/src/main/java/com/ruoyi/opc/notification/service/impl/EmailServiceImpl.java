package com.ruoyi.opc.notification.service.impl;

import com.ruoyi.opc.notification.domain.NotificationEmailLog;
import com.ruoyi.opc.notification.mapper.NotificationEmailLogMapper;
import com.ruoyi.opc.notification.provider.EmailProvider;
import com.ruoyi.opc.notification.provider.EmailSendException;
import com.ruoyi.opc.notification.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 邮件发送服务实现（含重试 + 日志）
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final EmailProvider emailProvider;
    private final NotificationEmailLogMapper logMapper;
    private final int maxRetries;

    public EmailServiceImpl(EmailProvider emailProvider,
                            NotificationEmailLogMapper logMapper,
                            @Value("${opc.notification.email.max-retries:3}") int maxRetries) {
        this.emailProvider = emailProvider;
        this.logMapper = logMapper;
        this.maxRetries = maxRetries;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        NotificationEmailLog logEntry = new NotificationEmailLog();
        logEntry.setRecipient(to);
        logEntry.setSubject(subject);
        logEntry.setBody(htmlBody);
        logEntry.setStatus(0);
        logEntry.setRetryCount(0);
        logEntry.setProvider(emailProvider.getClass().getSimpleName());
        logEntry.setCreatedAt(LocalDateTime.now());

        EmailSendException lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                emailProvider.send(to, subject, htmlBody);
                logEntry.setStatus(1);
                logEntry.setSentAt(LocalDateTime.now());
                logEntry.setRetryCount(attempt - 1);
                logMapper.insert(logEntry);
                log.info("[email] sent to={} subject={} attempt={}", to, subject, attempt);
                return;
            } catch (EmailSendException e) {
                lastError = e;
                logEntry.setRetryCount(attempt);
                logEntry.setErrorMsg(e.getMessage());
                log.warn("[email] attempt {}/{} failed for to={}: {}",
                    attempt, maxRetries, to, e.getMessage());
            }
        }
        logEntry.setStatus(2);
        logMapper.insert(logEntry);
        throw lastError;
    }
}
