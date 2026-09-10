package com.ruoyi.opc.notification.provider;

/**
 * 邮件发送失败异常
 */
public class EmailSendException extends RuntimeException {
    public EmailSendException(String msg) {
        super(msg);
    }

    public EmailSendException(String msg, Throwable cause) {
        super(msg, cause);
    }
}