package com.ruoyi.opc.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * R-15：金额跨服务一跳不能从 {@link BigDecimal} 退化成 {@link Double}。
 *
 * <p><b>问题</b>：工具侧的 Feign 声明的是 {@code R<Map<String,Object>>} —— 无类型数字，
 * Jackson 默认解成 {@code Double}，{@code 51892.00} 一跳就变成 {@code 51892.0}：
 * 精度掉了，scale 也掉了。日报里那个数字就不再跟库里那一行对得上。
 *
 * <p><b>定死</b>：opc-agent-hub 的<b>本地</b> {@code application.yml} 里加
 * {@code spring.jackson.deserialization.use-big-decimal-for-floats: true}。
 * 为什么本地 yml 就够：Nacos 的 {@code opc-dev} 下没有 {@code opc-agent-hub-dev.yml}
 * （那 10 份 dataId 里没有它）→ agent-hub 只吃 jar 里的这份本地配置。
 *
 * <p><b>这条测试在默认 Jackson 下必须是红的</b> —— 所以它刻意用
 * <b>Spring 注入的那个 ObjectMapper</b>（读真实的 application.yml 走
 * Spring Boot 自己的 {@link JacksonAutoConfiguration}），而不是 {@code new ObjectMapper()}。
 * 用 {@code new ObjectMapper()} 的话，测的是 Jackson 的默认行为，护不住任何东西。
 *
 * @author OAC
 */
class ToolJacksonBigDecimalTest {

    /** finance 端 {@code FlowRangeVo} 的真实 JSON 形状 */
    private static final String FINANCE_FLOW_JSON = """
            {
              "companyId": 2,
              "fromDate": "2026-10-07",
              "toDate": "2026-11-01",
              "count": 6,
              "totalIn": 51892.00,
              "totalOut": 32430.00,
              "net": 19462.00,
              "yesterdayCount": 0,
              "yesterdayIn": 0.00,
              "yesterdayOut": 0.00,
              "items": []
            }
            """;

    /** 读真实 application.yml + 真实 JacksonAutoConfiguration —— 与线上跑的是同一套 */
    private final ApplicationContextRunner appConfigured = new ApplicationContextRunner()
            .withInitializer(ctx -> loadApplicationYml(ctx.getEnvironment()))
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    /** 对照组：什么都没配 —— 默认 Jackson */
    private final ApplicationContextRunner plainJackson = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    private static void loadApplicationYml(ConfigurableEnvironment env) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
            sources.forEach(env.getPropertySources()::addLast);
        } catch (IOException e) {
            throw new IllegalStateException("读不到 agent-hub 的 application.yml", e);
        }
    }

    @Test
    @DisplayName("Spring 注入的 ObjectMapper —— 金额解成 BigDecimal（scale=2），不是 Double")
    void springObjectMapper_decodesFinanceAmountsAsBigDecimal() {
        appConfigured.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            Map<String, Object> map = mapper.readValue(FINANCE_FLOW_JSON,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });

            Object totalIn = map.get("totalIn");
            assertInstanceOf(BigDecimal.class, totalIn,
                    "金额必须是 BigDecimal —— 解成 Double 就说明 use-big-decimal-for-floats 没生效");
            assertEquals(0, new BigDecimal("51892.00").compareTo((BigDecimal) totalIn),
                    "金额数值必须与 finance 端返回的一致");
            assertEquals("51892.00", ((BigDecimal) totalIn).toString(),
                    "scale 必须保持 2 —— 退化成 51892.0 就说明精度掉了");
        });
    }

    @Test
    @DisplayName("对照：默认 Jackson（不配 use-big-decimal-for-floats）会把金额解成 Double")
    void defaultJackson_decodesFinanceAmountsAsDouble() {
        plainJackson.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            Map<String, Object> map = mapper.readValue(FINANCE_FLOW_JSON,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });

            assertInstanceOf(Double.class, map.get("totalIn"),
                    "默认 Jackson 就是会把金额解成 Double —— 这正是上一条测试要防的退化");
        });
    }

}
