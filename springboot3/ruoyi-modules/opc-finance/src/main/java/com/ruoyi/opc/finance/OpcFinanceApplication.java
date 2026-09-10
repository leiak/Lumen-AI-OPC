package com.ruoyi.opc.finance;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC 财务 Agent 启动类
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients(basePackages = { "com.ruoyi.system.api", "com.ruoyi.opc" })
@SpringBootApplication
@ComponentScan({ "com.ruoyi.opc", "com.ruoyi.finance" })
public class OpcFinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcFinanceApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC 财务 Agent 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
