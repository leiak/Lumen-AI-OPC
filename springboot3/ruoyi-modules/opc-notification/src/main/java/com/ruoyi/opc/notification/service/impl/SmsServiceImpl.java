package com.ruoyi.opc.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.notification.domain.NotificationSmsLog;
import com.ruoyi.opc.notification.mapper.NotificationSmsLogMapper;
import com.ruoyi.opc.notification.provider.SmsProvider;
import com.ruoyi.opc.notification.provider.SmsSendException;
import com.ruoyi.opc.notification.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 短信发送服务实现（含重试 + 日志）
 */
@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    private final SmsProvider smsProvider;
    private final NotificationSmsLogMapper logMapper;
    private final ObjectMapper objectMapper;
    private final int maxRetries;
    private final long retryBackoffMs;

    public SmsServiceImpl(SmsProvider smsProvider,
                          NotificationSmsLogMapper logMapper,
                          ObjectMapper objectMapper,
                          @Value("${opc.notification.sms.max-retries:3}") int maxRetries,
                          @Value("${opc.notification.sms.retry-backoff-ms:100}") long retryBackoffMs) {
        this.smsProvider = smsProvider;
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
    }

    @Override
    public void send(String phone, String templateCode, Map<String, String> vars) {
        NotificationSmsLog logEntry = new NotificationSmsLog();
        logEntry.setPhone(phone);
        logEntry.setTemplateCode(templateCode);
        try {
            logEntry.setVarsJson(objectMapper.writeValueAsString(vars != null ? vars : Map.of()));
        } catch (JsonProcessingException e) {
            logEntry.setVarsJson("{}");
        }
        logEntry.setContent(templateCode + " " + (vars != null ? vars : Map.of()));
        logEntry.setStatus(0);
        logEntry.setRetryCount(0);
        logEntry.setProvider(smsProvider.getClass().getSimpleName());
        logEntry.setCreatedAt(LocalDateTime.now());

        SmsSendException lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                smsProvider.send(phone, templateCode, vars);
                logEntry.setStatus(1);
                logEntry.setSentAt(LocalDateTime.now());
                logEntry.setRetryCount(attempt - 1);
                logMapper.insert(logEntry);
                log.info("[sms] sent to={} template={} attempt={}", phone, templateCode, attempt);
                return;
            } catch (SmsSendException e) {
                lastError = e;
                logEntry.setRetryCount(attempt);
                logEntry.setErrorMsg(e.getMessage());
                log.warn("[sms] attempt {}/{} failed for phone={}: {}",
                    attempt, maxRetries, phone, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryBackoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SmsSendException("Retry interrupted", ie);
                    }
                }
            }
        }
        logEntry.setStatus(2);
        if (lastError == null) {
            logEntry.setErrorMsg("max-retries=0, no attempts made");
            logMapper.insert(logEntry);
            throw new SmsSendException("max-retries=0, no attempts made");
        }
        logMapper.insert(logEntry);
        throw lastError;
    }
}
