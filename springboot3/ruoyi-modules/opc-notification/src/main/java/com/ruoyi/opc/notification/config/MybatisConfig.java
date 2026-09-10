package com.ruoyi.opc.notification.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis config: scans mapper interfaces under com.ruoyi.opc.notification.mapper.
 *
 * <p>XML location is wired by application.yml (mybatis.mapper-locations),
 * introduced in Task 11. This MapperScan only enables Mapper interface injection.</p>
 *
 * @author OAC
 */
@Configuration
@MapperScan("com.ruoyi.opc.notification.mapper")
public class MybatisConfig {
}
