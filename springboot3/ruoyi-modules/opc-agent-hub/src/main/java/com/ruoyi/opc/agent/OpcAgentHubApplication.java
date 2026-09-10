package com.ruoyi.opc.agent;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * OPC Agent Hub 启动类
 *
 * @author OAC
 *
 * 注意:scanBasePackages 必须包含 "com.ruoyi.opc" 才能让 WorkflowEngine 注入
 * opc-ai-core 模块的 AgentRuntime。但 @EnableRyFeignClients 默认扫 "com.ruoyi"
 * 会与 ComponentScan 重复注册 RemoteFileService 等 FeignClient。
 * 因此用 @ComponentScan 显式限定为 opc 包,避免冲突。
 */
@EnableCustomConfig
@EnableRyFeignClients(basePackages = { "com.ruoyi.system.api", "com.ruoyi.opc" })
@SpringBootApplication
@ComponentScan({ "com.ruoyi.opc", "com.ruoyi.agent" })
public class OpcAgentHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcAgentHubApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC Agent Hub 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
