package com.ruoyi.opc.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 邮件发送器配置（基于 Nacos / application.yml 中的 mail.* 配置）。
 *
 * <p>兜底默认值写在 application.yml，生产凭据由 Nacos 的
 * opc-notification-prod.yml 用 Jasypt ENC(...) 覆盖。</p>
 *
 * @author OAC
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(
            @Value("${mail.host}") String host,
            @Value("${mail.port}") int port,
            @Value("${mail.username}") String username,
            @Value("${mail.password}") String password,
            @Value("${mail.properties.mail.smtp.auth:true}") boolean smtpAuth,
            @Value("${mail.properties.mail.smtp.starttls.enable:false}") boolean startTls,
            @Value("${mail.properties.mail.smtp.ssl.enable:false}") boolean sslEnable) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", startTls);
        props.put("mail.smtp.ssl.enable", sslEnable);
        if (sslEnable) {
            // SSL 直连（465）时必须显式指定 socket factory，否则部分 SMTP 服务器握手失败
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        }
        return sender;
    }
}
