package com.ruoyi.opc.community;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@EnableCustomConfig
@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@SpringBootApplication
@ComponentScan("com.ruoyi.opc")
public class OpcCommunityApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcCommunityApplication.class, args);
    }
}
