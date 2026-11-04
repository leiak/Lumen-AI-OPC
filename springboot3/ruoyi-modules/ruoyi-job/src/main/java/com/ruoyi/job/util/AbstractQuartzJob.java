package com.ruoyi.job.util;

import java.util.Date;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.core.constant.ScheduleConstants;
import com.ruoyi.common.core.utils.ExceptionUtil;
import com.ruoyi.common.core.utils.SpringUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.utils.bean.BeanUtils;
import com.ruoyi.job.domain.SysJob;
import com.ruoyi.job.domain.SysJobLog;
import com.ruoyi.job.service.ISysJobLogService;

/**
 * 抽象quartz调用
 *
 * <p><b>「定时触发」与「后台执行一次」的区分放在这个基类里</b>：
 * 实际跑的 Job 类由 {@code ScheduleUtils.getQuartzJobClass()} 按 sys_job.concurrent
 * 决定（0=允许并发 → QuartzJobExecution，1=禁止并发 → QuartzDisallowConcurrentExecution），
 * 两个类都继承这里 —— 写在子类里的话以后新增 Job 类就会漏。
 * <b>新增 Job 类不需要动这里</b>，只要继承 AbstractQuartzJob 就自动带上标记逻辑。
 *
 * @author ruoyi
 */
public abstract class AbstractQuartzJob implements Job
{
    private static final Logger log = LoggerFactory.getLogger(AbstractQuartzJob.class);

    /**
     * 线程本地变量
     */
    private static ThreadLocal<Date> threadLocal = new ThreadLocal<>();

    @Override
    public void execute(JobExecutionContext context)
    {
        SysJob sysJob = new SysJob();
        BeanUtils.copyBeanProp(sysJob, context.getMergedJobDataMap().get(ScheduleConstants.TASK_PROPERTIES));
        try
        {
            before(context, sysJob);
            if (sysJob != null)
            {
                doExecute(context, sysJob);
            }
            after(context, sysJob, null);
        }
        catch (Exception e)
        {
            log.error("任务执行异常  - ：", e);
            after(context, sysJob, e);
        }
        finally
        {
            // 无论成功、失败、还是 before 抛异常，都必须清掉标记 ——
            // Quartz 的工作线程是池化复用的，标记泄漏会让下一个（可能完全无关的）
            // 任务被误判成「后台手动触发」。
            ManualTriggerMarker.clear();
        }
    }

    /**
     * 执行前
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     */
    protected void before(JobExecutionContext context, SysJob sysJob)
    {
        threadLocal.set(new Date());
        // 后台「执行一次」走 scheduler.triggerJob(jobKey, dataMap)，
        // 它塞进 JobDataMap 的标记在这里被读出来置成 ThreadLocal，
        // 业务任务据此决定自己是被定时触发还是被手动触发。
        if (context.getMergedJobDataMap().containsKey(ManualTriggerMarker.KEY))
        {
            ManualTriggerMarker.markManual();
        }
    }

    /**
     * 执行后
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     */
    protected void after(JobExecutionContext context, SysJob sysJob, Exception e)
    {
        Date startTime = threadLocal.get();
        threadLocal.remove();

        final SysJobLog sysJobLog = new SysJobLog();
        sysJobLog.setJobName(sysJob.getJobName());
        sysJobLog.setJobGroup(sysJob.getJobGroup());
        sysJobLog.setInvokeTarget(sysJob.getInvokeTarget());
        sysJobLog.setStartTime(startTime);
        sysJobLog.setEndTime(new Date());
        long runMs = sysJobLog.getEndTime().getTime() - sysJobLog.getStartTime().getTime();
        sysJobLog.setJobMessage(sysJobLog.getJobName() + " 总共耗时：" + runMs + "毫秒");
        if (e != null)
        {
            sysJobLog.setStatus("1");
            String errorMsg = StringUtils.substring(ExceptionUtil.getExceptionMessage(e), 0, 2000);
            sysJobLog.setExceptionInfo(errorMsg);
        }
        else
        {
            sysJobLog.setStatus("0");
        }

        // 写入数据库当中
        SpringUtils.getBean(ISysJobLogService.class).addJobLog(sysJobLog);
    }

    /**
     * 执行方法，由子类重载
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     * @throws Exception 执行过程中的异常
     */
    protected abstract void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception;
}
