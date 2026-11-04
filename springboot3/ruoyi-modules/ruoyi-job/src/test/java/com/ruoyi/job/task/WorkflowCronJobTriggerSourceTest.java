package com.ruoyi.job.task;

import com.ruoyi.common.core.constant.ScheduleConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.job.api.RemoteWorkflowService;
import com.ruoyi.job.domain.SysJob;
import com.ruoyi.job.util.AbstractQuartzJob;
import com.ruoyi.job.util.ManualTriggerMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 7 —— 「定时触发」与「后台点执行一次」在数据层必须分得开。
 *
 * <p>本类测的是 WorkflowCronJob 这一层：它根据 ManualTriggerMarker 决定
 * 传给 Feign 的 triggerSource 是 {@code quartz:} 还是 {@code manual:}，
 * 以及响应体里的业务 status 怎么处理。
 *
 * @author OAC
 */
class WorkflowCronJobTriggerSourceTest
{
    private static final String CODE = "finance_daily_report_v1";

    private RemoteWorkflowService remoteWorkflowService;

    private WorkflowCronJob workflowCronJob;

    @BeforeEach
    void setUp()
    {
        remoteWorkflowService = mock(RemoteWorkflowService.class);
        workflowCronJob = new WorkflowCronJob(remoteWorkflowService);
        ManualTriggerMarker.clear();
    }

    @AfterEach
    void tearDown()
    {
        // ThreadLocal 不许泄漏到同一个 JVM 里的其它测试
        ManualTriggerMarker.clear();
    }

    @Test
    @DisplayName("没有手动标记 → triggerSource 是 quartz:<code>")
    void execute_scheduledTrigger_usesQuartzPrefix()
    {
        stubStatus("SUCCESS");

        workflowCronJob.execute(CODE);

        // 这是本 Task 的唯一产出，必须直接断言传给 Feign 的第二个参数
        verify(remoteWorkflowService).trigger(eq(CODE), eq("quartz:" + CODE), any());
    }

    @Test
    @DisplayName("有手动标记 → triggerSource 是 manual:<code>")
    void execute_manualTrigger_usesManualPrefix()
    {
        stubStatus("SUCCESS");

        ManualTriggerMarker.markManual();
        workflowCronJob.execute(CODE);

        verify(remoteWorkflowService).trigger(eq(CODE), eq("manual:" + CODE), any());
    }

    @Test
    @DisplayName("跑完之后手动标记不泄漏（ThreadLocal 不许带到下一个任务）")
    void execute_clearsMarkerAfterRun()
    {
        stubStatus("SUCCESS");

        // 走真实的 AbstractQuartzJob 流程：标记的置位/清理由基类负责
        JobDataMap merged = new JobDataMap();
        merged.put(ScheduleConstants.TASK_PROPERTIES, sysJob());
        merged.put(ManualTriggerMarker.KEY, Boolean.TRUE);

        JobExecutionContext context = mockContext(merged);

        ManualTriggerMarker.markManual();
        new ProbeJob(workflowCronJob).execute(context);

        assertThat(ManualTriggerMarker.isManual()).isFalse();
    }

    @Test
    @DisplayName("业务失败（status=FAILED）→ 抛异常，不能只记一行日志")
    void execute_businessFailure_throws()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "FAILED");
        data.put("errorMessage", "LLM 调用失败");
        stubData(data);

        assertThatThrownBy(() -> workflowCronJob.execute(CODE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工作流业务失败")
                .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("PARTIAL（数据落表、LLM 降级）→ 不抛")
    void execute_partial_doesNotThrow()
    {
        stubStatus("PARTIAL");

        assertThatCode(() -> workflowCronJob.execute(CODE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RUNNING（命中 30 秒去重窗口）→ 不抛")
    void execute_running_doesNotThrow()
    {
        stubStatus("RUNNING");

        assertThatCode(() -> workflowCronJob.execute(CODE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("响应体里没有 status → 抛（拿不到状态 = 不知道有没有跑成）")
    void execute_nullStatus_throws()
    {
        stubData(new HashMap<>());

        assertThatThrownBy(() -> workflowCronJob.execute(CODE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法判定的状态");
    }

    @Test
    @DisplayName("响应体里 data 为 null → 抛（拿不到工作流状态 = 不能记成功）")
    void execute_nullData_throws()
    {
        // 与 execute_nullStatus_throws 是两条不同的代码路径：
        // 那一条是 data 非 null、但 data 里没有 status 键；
        // 这一条是 data 整个为 null（拿不到工作流状态）。
        // 这一条必须单独断言 —— 它坏了的话，这次失败的任务会被
        // AbstractQuartzJob 记成 sys_job_log.status='0'（成功），
        // 也就是「以为跑成了，其实没跑成」。
        stubData(null);

        assertThatThrownBy(() -> workflowCronJob.execute(CODE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法判定的状态")
                .hasMessageContaining("finance_daily_report_v1");
    }

    private void stubStatus(String status)
    {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        stubData(data);
    }

    private void stubData(Map<String, Object> data)
    {
        when(remoteWorkflowService.trigger(eq(CODE), anyString(), any())).thenReturn(R.ok(data));
    }

    private static SysJob sysJob()
    {
        SysJob job = new SysJob();
        job.setJobId(100L);
        job.setJobGroup("SYSTEM");
        job.setJobName("财务日报");
        job.setInvokeTarget("workflowCronJob.execute('" + CODE + "')");
        job.setConcurrent("1");
        job.setStatus("0");
        return job;
    }

    private static JobExecutionContext mockContext(JobDataMap merged)
    {
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(merged);
        return context;
    }

    /**
     * 走真实的 AbstractQuartzJob 基类流程，但把 after() 覆写成空操作 ——
     * 单测不连数据库、不写 sys_job_log。
     */
    static class ProbeJob extends AbstractQuartzJob
    {
        private final WorkflowCronJob delegate;

        ProbeJob(WorkflowCronJob delegate)
        {
            this.delegate = delegate;
        }

        @Override
        protected void doExecute(JobExecutionContext context, SysJob sysJob)
        {
            delegate.execute(CODE);
        }

        @Override
        protected void after(JobExecutionContext context, SysJob sysJob, Exception e)
        {
            // 不落库
        }
    }
}
