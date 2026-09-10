package com.ruoyi.opc.notification.service;

/**
 * 邮件发送服务
 */
public interface EmailService {
    /**
     * Send an email with internal retry (default 3x).
     * Persists attempt to opc_notification_email_log table.
     *
     * @throws com.ruoyi.opc.notification.provider.EmailSendException after all retries exhausted
     */
    void send(String to, String subject, String htmlBody);
}
