package com.ruoyi.opc.user;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OPC 用户中心启动类
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class OpcUserCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcUserCenterApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC 用户中心启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
