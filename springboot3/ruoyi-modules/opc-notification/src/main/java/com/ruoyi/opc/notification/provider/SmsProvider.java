package com.ruoyi.opc.notification.provider;

import java.util.Map;

/**
 * 短信发送提供者抽象
 */
public interface SmsProvider {
    /**
     * Send SMS using provider template code + variable map.
     *
     * @param phone        手机号
     * @param templateCode 短信模板 code
     * @param vars         模板变量
     * @throws SmsSendException on failure
     */
    void send(String phone, String templateCode, Map<String, String> vars) throws SmsSendException;
}
