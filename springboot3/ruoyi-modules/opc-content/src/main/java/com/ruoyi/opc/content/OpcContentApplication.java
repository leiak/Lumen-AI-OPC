package com.ruoyi.opc.content;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC Content 启动类 (AI 内容创作中心 — 短剧 / 视频 / 图文 / 平台适配)
 *
 * @author OAC
 */
@EnableCustomConfig
@SpringBootApplication
@EnableDiscoveryClient
@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})
public class OpcContentApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcContentApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC Content 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
