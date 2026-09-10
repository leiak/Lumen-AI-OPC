package com.ruoyi.opc.notification.config;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.notification.provider.AliyunSmsProvider;
import com.ruoyi.opc.notification.provider.SmsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短信 / 邮件 Provider 配置
 */
@Slf4j
@Configuration
public class ProviderConfig {

    /**
     * 阿里云短信 Provider
     * - 如果未配置 access-key，返回 NoOpProvider（仅记录日志，不抛异常）
     */
    @Bean
    public SmsProvider smsProvider(
            @Value("${opc.notification.sms.sign-name:OPCNOTIFY}") String signName,
            @Value("${opc.notification.aliyun.access-key:}") String accessKey,
            @Value("${opc.notification.aliyun.access-secret:}") String accessSecret,
            ObjectMapper objectMapper) {
        if (accessKey.isEmpty() || accessSecret.isEmpty()) {
            log.warn("[sms] Aliyun access-key/secret not configured, SmsProvider bean is no-op");
            return (phone, tpl, vars) -> log.info("[sms:noop] to={} tpl={} vars={}", phone, tpl, vars);
        }
        try {
            Config config = new Config()
                .setAccessKeyId(accessKey)
                .setAccessKeySecret(accessSecret);
            config.endpoint = "dysmsapi.aliyuncs.com";
            Client client = new Client(config);
            return new AliyunSmsProvider(client, signName, objectMapper);
        } catch (Exception e) {
            log.error("[sms] Failed to init Aliyun client: {}", e.getMessage());
            return (phone, tpl, vars) -> log.info("[sms:noop] to={} tpl={}", phone, tpl);
        }
    }
}
