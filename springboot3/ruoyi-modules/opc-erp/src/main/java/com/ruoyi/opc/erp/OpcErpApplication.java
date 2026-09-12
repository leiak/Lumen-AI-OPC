package com.ruoyi.opc.erp;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC ERP 启动类（供应商 / 商品 / SKU / 批次 / 采购 / 销售 / 退货 / 库存）
 *
 * @author OAC
 */
@EnableCustomConfig
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})
public class OpcErpApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcErpApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC ERP 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
