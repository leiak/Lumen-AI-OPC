package com.ruoyi.opc.insight;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.opc.insight.client.RemoteBillingService;
import com.ruoyi.opc.insight.client.RemoteFinanceService;
import com.ruoyi.opc.insight.client.RemoteUserCenterService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * OPC 数据洞察 Agent 启动类
 * <p>
 * M4 INSIGHT MVP：聚合账单、凭证、报税、流水数据，
 * 通过 AI 中台生成自然语言业务洞察与建议。
 *
 * <p><b>Feign 客户端用 {@code clients = ...} 显式列表</b>，避免基于
 * basePackages 的扫描（class 扫描时 {@code @EnableFeignClients(basePackages = ...)}
 * 与 {@code @SpringBootApplication(scanBasePackages = ...)} 在某些场景下会
 * 重复注册同名 FeignClientSpecification Bean，导致
 * {@code BeanDefinitionOverrideException}）。本类用 explicit clients
 * 列表彻底规避该问题。</p>
 *
 * @author OAC
 */
@EnableCustomConfig
@EnableFeignClients(clients = {RemoteBillingService.class, RemoteFinanceService.class, RemoteUserCenterService.class})
@SpringBootApplication(scanBasePackages = {"com.ruoyi.opc.insight", "com.ruoyi.opc.ai"})
@ComponentScan(
    basePackages = {"com.ruoyi.opc.insight", "com.ruoyi.opc.ai"},
    useDefaultFilters = true,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com.ruoyi.system.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com.ruoyi.gen.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com.ruoyi.job.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com.ruoyi.file.*"),
        // 关键：排除 @FeignClient 接口，仅由 @EnableFeignClients 注册
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = FeignClient.class)
    }
)
public class OpcInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcInsightApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC 数据洞察 Agent 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
