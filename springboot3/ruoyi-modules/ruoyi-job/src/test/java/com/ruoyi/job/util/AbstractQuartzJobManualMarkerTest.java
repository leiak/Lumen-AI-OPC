package com.ruoyi.job.util;

import com.ruoyi.common.core.constant.ScheduleConstants;
import com.ruoyi.common.core.utils.SpringUtils;
import com.ruoyi.job.domain.SysJob;
import com.ruoyi.job.service.ISysJobLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Task 7 —— AbstractQuartzJob 基类负责把 JobDataMap 里的手动标记读成 ThreadLocal，
 * 并在 execute() 的 finally 里清掉（Quartz 的工作线程是池化复用的，泄漏了会污染下一个任务）。
 *
 * <p>这里用的是真实路径：mergedJobDataMap → 标记 → JobInvokeUtil → 业务 bean 的方法。
 * 业务 bean 通过 {@link SpringUtils} 的静态 beanFactory 注入一个假 bean 实现。
 *
 * @author OAC
 */
class AbstractQuartzJobManualMarkerTest
{
    /** 假的业务 bean：记录「被调用的那一刻」标记是不是手动 */
    static class ProbeBean
    {
        Boolean manualDuringInvoke;

        public void execute()
        {
            manualDuringInvoke = ManualTriggerMarker.isManual();
        }
    }

    private static final ProbeBean PROBE = new ProbeBean();

    @BeforeAll
    static void setUpBeanFactory()
    {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("manualMarkerProbe", PROBE);
        // after() 会写 sys_job_log，单测不连数据库 → 给一个空实现
        factory.registerSingleton("sysJobLogService", mock(ISysJobLogService.class));
        new SpringUtils().postProcessBeanFactory(factory);
    }

    @BeforeEach
    void setUp()
    {
        PROBE.manualDuringInvoke = null;
        ManualTriggerMarker.clear();
    }

    @AfterEach
    void tearDown()
    {
        ManualTriggerMarker.clear();
    }

    @Test
    @DisplayName("dataMap 带标记 → 任务执行期间 isManual() == true")
    void execute_withManualMarker_setsMarkerDuringInvocation()
    {
        JobDataMap merged = dataMap(true);

        new QuartzDisallowConcurrentExecution().execute(context(merged));

        assertThat(PROBE.manualDuringInvoke).isTrue();
    }

    @Test
    @DisplayName("dataMap 不带标记 → 任务执行期间 isManual() == false")
    void execute_withoutManualMarker_markerIsFalse()
    {
        JobDataMap merged = dataMap(false);

        new QuartzDisallowConcurrentExecution().execute(context(merged));

        assertThat(PROBE.manualDuringInvoke).isFalse();
    }

    @Test
    @DisplayName("跑完之后标记一定被清掉（finally）")
    void execute_markerClearedAfterRun()
    {
        JobDataMap merged = dataMap(true);

        new QuartzDisallowConcurrentExecution().execute(context(merged));

        assertThat(ManualTriggerMarker.isManual()).isFalse();
    }

    private static JobDataMap dataMap(boolean manual)
    {
        SysJob job = new SysJob();
        job.setJobId(100L);
        job.setJobGroup("SYSTEM");
        job.setJobName("财务日报");
        job.setInvokeTarget("manualMarkerProbe.execute()");
        job.setConcurrent("1");
        job.setStatus("0");

        JobDataMap merged = new JobDataMap();
        merged.put(ScheduleConstants.TASK_PROPERTIES, job);
        if (manual)
        {
            merged.put(ManualTriggerMarker.KEY, Boolean.TRUE);
        }
        return merged;
    }

    private static JobExecutionContext context(JobDataMap merged)
    {
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(merged);
        return context;
    }
}
