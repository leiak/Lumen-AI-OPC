package com.ruoyi.opc.ai;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC AI 中台启动类
 * 提供 LLM Gateway、Agent Runtime、Memory、Token 计量
 *
 * @author OAC
 *
 * 注意:TokenUsageRecorderImpl 由 opc-agent-hub 提供 (@Component 在 com.ruoyi.opc.agent.gateway),
 * ai-core 启动类必须扩大 ComponentScan 到 com.ruoyi.opc 才能注入。
 */
@EnableCustomConfig
@EnableRyFeignClients(basePackages = { "com.ruoyi.system.api", "com.ruoyi.opc" })
@SpringBootApplication
@ComponentScan({ "com.ruoyi.opc", "com.ruoyi.ai" })
public class OpcAiCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcAiCoreApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC AI 中台启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
