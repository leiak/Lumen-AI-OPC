package com.ruoyi.opc.content;

import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * OPC Content 启动类 (AI 内容创作中心 — 短剧 / 视频 / 图文 / 平台适配)
 *
 * @author OAC
 */
@SpringBootApplication(scanBasePackages = {"com.ruoyi.opc", "com.ruoyi.system"})
@EnableDiscoveryClient
@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@MapperScan("com.ruoyi.opc.content.mapper")
public class OpcContentApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcContentApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC Content 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
