package com.ruoyi.opc.insight;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OPC 数据洞察 Agent 启动类
 * <p>
 * M4 INSIGHT MVP：聚合账单、凭证、报税、流水数据，
 * 通过 AI 中台生成自然语言业务洞察与建议。
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class OpcInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcInsightApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC 数据洞察 Agent 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
