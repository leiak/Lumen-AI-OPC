package com.ruoyi.opc.notification.provider;

/**
 * 邮件发送提供者抽象
 */
public interface EmailProvider {
    /**
     * Send an email. Throw EmailSendException on failure.
     *
     * @param to       收件人邮箱
     * @param subject  主题
     * @param htmlBody HTML 正文
     * @throws EmailSendException on failure
     */
    void send(String to, String subject, String htmlBody) throws EmailSendException;
}