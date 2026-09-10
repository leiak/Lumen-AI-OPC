package com.ruoyi.opc.billing;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC 计费启动类
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients(basePackages = { "com.ruoyi.system.api", "com.ruoyi.opc" })
@SpringBootApplication
@ComponentScan({ "com.ruoyi.opc", "com.ruoyi.billing" })
public class OpcBillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcBillingApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC 计费启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
