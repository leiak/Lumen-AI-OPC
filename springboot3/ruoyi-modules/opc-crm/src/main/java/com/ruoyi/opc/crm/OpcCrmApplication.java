package com.ruoyi.opc.crm;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC 客户关系管理启动类（客户档案 / 联系人 / 跟进 / 商机 / 合同 / 订单）
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients(basePackages = { "com.ruoyi.system.api", "com.ruoyi.opc" })
@SpringBootApplication
@ComponentScan({ "com.ruoyi.opc", "com.ruoyi.system" })
public class OpcCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcCrmApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC CRM 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}