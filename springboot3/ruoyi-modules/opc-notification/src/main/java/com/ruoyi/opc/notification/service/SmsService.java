package com.ruoyi.opc.notification.service;

import java.util.Map;

/**
 * 短信发送服务
 */
public interface SmsService {
    /**
     * Send SMS with internal retry (default 3x).
     * Persists attempt to opc_notification_sms_log table.
     *
     * @throws com.ruoyi.opc.notification.provider.SmsSendException after all retries exhausted
     */
    void send(String phone, String templateCode, Map<String, String> vars);
}
