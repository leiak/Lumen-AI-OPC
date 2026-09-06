package com.ruoyi.opc.billing;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OPC 计费启动类
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class OpcBillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcBillingApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC 计费启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
