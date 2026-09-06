package com.ruoyi.opc.agent;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OPC Agent Hub 启动类
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class OpcAgentHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcAgentHubApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC Agent Hub 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
