package com.ruoyi.opc.notification.service.impl;

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
    private final int maxRetries;

    public SmsServiceImpl(SmsProvider smsProvider,
                          NotificationSmsLogMapper logMapper,
                          @Value("${opc.notification.sms.max-retries:3}") int maxRetries) {
        this.smsProvider = smsProvider;
        this.logMapper = logMapper;
        this.maxRetries = maxRetries;
    }

    @Override
    public void send(String phone, String templateCode, Map<String, String> vars) {
        NotificationSmsLog logEntry = new NotificationSmsLog();
        logEntry.setPhone(phone);
        logEntry.setTemplateCode(templateCode);
        logEntry.setVarsJson(vars != null ? vars.toString() : "{}");
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
            }
        }
        logEntry.setStatus(2);
        logMapper.insert(logEntry);
        throw lastError;
    }
}
