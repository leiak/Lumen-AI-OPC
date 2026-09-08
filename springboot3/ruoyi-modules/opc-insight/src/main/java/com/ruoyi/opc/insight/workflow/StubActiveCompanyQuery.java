package com.ruoyi.opc.insight.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Stub {@link IActiveCompanyQuery} for opc-insight.
 *
 * <p><b>为什么需要</b>：{@code InsightDailyReportJob} 通过构造器注入
 * {@code IActiveCompanyQuery}。生产环境该接口应由 Feign 适配器调用 opc-user-center
 * 的真实接口实现。当前 MVP 阶段不暴露日报 cron 调度入口（Spring Bean 仍然装配，
 * 但不会被 Quartz 触发），所以提供空列表 stub 让容器装配通过。</p>
 *
 * <p><b>如果未来 opc-insight 需要真实日报调度</b>，应实现 Feign 客户端调用
 * opc-user-center 的「查询活跃公司」接口，并把本类标记为 {@code @Primary} 或者删除。</p>
 *
 * @author OAC
 */
@Slf4j
@Component
public class StubActiveCompanyQuery implements IActiveCompanyQuery {

    @Override
    public List<Long> activeCompanyIds() {
        log.info("[StubActiveCompanyQuery] 返回空活跃公司列表（占位实现）");
        return Collections.emptyList();
    }
}