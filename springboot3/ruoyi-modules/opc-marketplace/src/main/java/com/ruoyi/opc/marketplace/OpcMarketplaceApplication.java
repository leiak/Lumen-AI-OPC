package com.ruoyi.opc.marketplace;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC Marketplace 启动类（作者入驻 / 商品上架 / 安装 / 评价 / 类目 / 标签）
 *
 * @author OAC
 */
@EnableCustomConfig
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})
public class OpcMarketplaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcMarketplaceApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC MARKETPLACE 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}