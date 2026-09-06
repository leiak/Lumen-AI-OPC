package com.ruoyi.opc.ai;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OPC AI 中台启动类
 * 提供 LLM Gateway、Agent Runtime、Memory、Token 计量
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class OpcAiCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcAiCoreApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC AI 中台启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
