package com.ruoyi.opc.notification;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC 通知中心启动类（邮件 / 短信 / 站内信 / WebSocket）
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients(basePackages = { "com.ruoyi.system.api", "com.ruoyi.opc" })
@SpringBootApplication
@ComponentScan({ "com.ruoyi.opc", "com.ruoyi.system" })
public class OpcNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcNotificationApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC 通知中心启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
