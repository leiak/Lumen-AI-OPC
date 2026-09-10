package com.ruoyi.opc.notification.provider;

/**
 * 短信发送失败异常
 */
public class SmsSendException extends RuntimeException {
    public SmsSendException(String msg) {
        super(msg);
    }

    public SmsSendException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
