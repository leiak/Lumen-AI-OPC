package com.ruoyi.opc.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;

@SpringBootApplication
@ComponentScan(basePackages = {"com.ruoyi.system", "com.ruoyi.opc"})
@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
public class OpcNotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcNotificationApplication.class, args);
    }
}
