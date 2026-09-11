package com.ruoyi.opc.hr;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC HR 启动类（招聘需求 / 候选人 / 投递 / 面试 / Offer / 匹配打分）
 *
 * @author OAC
 */
@EnableCustomConfig
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})
public class OpcHrApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcHrApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC HR 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
