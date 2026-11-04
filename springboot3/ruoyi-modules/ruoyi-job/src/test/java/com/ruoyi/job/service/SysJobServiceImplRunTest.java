package com.ruoyi.job.service;

import com.ruoyi.common.core.constant.ScheduleConstants;
import com.ruoyi.job.domain.SysJob;
import com.ruoyi.job.mapper.SysJobMapper;
import com.ruoyi.job.util.ManualTriggerMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 7 —— 后台「执行一次」（{@code SysJobServiceImpl.run}）必须往 JobDataMap 里
 * 塞手动标记，否则下游根本分不出定时和手动。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class SysJobServiceImplRunTest
{
    @Mock
    private Scheduler scheduler;

    @Mock
    private SysJobMapper jobMapper;

    @InjectMocks
    private SysJobServiceImpl sysJobService;

    @BeforeEach
    void setUp()
    {
        ManualTriggerMarker.clear();
    }

    @AfterEach
    void tearDown()
    {
        ManualTriggerMarker.clear();
    }

    @Test
    @DisplayName("手动执行 → dataMap 里带标记，且不能把 TASK_PROPERTIES 挤掉")
    void run_manualExecution_marksDataMap() throws SchedulerException
    {
        SysJob job = job();
        when(jobMapper.selectJobById(100L)).thenReturn(properties());
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(true);

        boolean result = sysJobService.run(job);

        assertThat(result).isTrue();

        ArgumentCaptor<JobDataMap> captor = ArgumentCaptor.forClass(JobDataMap.class);
        verify(scheduler).triggerJob(any(JobKey.class), captor.capture());

        JobDataMap dataMap = captor.getValue();
        assertThat(dataMap.containsKey(ManualTriggerMarker.KEY)).isTrue();
        assertThat(dataMap.get(ManualTriggerMarker.KEY)).isEqualTo(Boolean.TRUE);
        // 原来就有的 TASK_PROPERTIES 不能被挤掉
        assertThat(dataMap.containsKey(ScheduleConstants.TASK_PROPERTIES)).isTrue();
    }

    @Test
    @DisplayName("任务不存在 → 不触发，也没有标记泄漏")
    void run_jobNotExists_doesNotTrigger() throws SchedulerException
    {
        SysJob job = job();
        when(jobMapper.selectJobById(100L)).thenReturn(properties());
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);

        boolean result = sysJobService.run(job);

        assertThat(result).isFalse();
        verify(scheduler, never()).triggerJob(any(JobKey.class), any(JobDataMap.class));
        assertThat(ManualTriggerMarker.isManual()).isFalse();
    }

    private static SysJob job()
    {
        SysJob job = new SysJob();
        job.setJobId(100L);
        job.setJobGroup("SYSTEM");
        return job;
    }

    private static SysJob properties()
    {
        SysJob job = new SysJob();
        job.setJobId(100L);
        job.setJobGroup("SYSTEM");
        job.setJobName("财务日报");
        job.setInvokeTarget("workflowCronJob.execute('finance_daily_report_v1')");
        job.setConcurrent("1");
        job.setStatus("0");
        return job;
    }
}
