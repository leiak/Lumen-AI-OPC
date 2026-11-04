package com.ruoyi.job.util;

/**
 * 「后台点执行一次」的标记。
 *
 * <p>RuoYi 的手动执行走 {@code scheduler.triggerJob(jobKey, dataMap)} —— 同一个
 * CronTrigger、同一个 invoke_target，跟定时触发在 Quartz 侧完全一样，
 * 只有在 JobDataMap 里额外塞一个标记才能区分开。
 *
 * <p>标记的传递链：
 * {@code SysJobServiceImpl.run()} 塞进 JobDataMap →
 * {@code AbstractQuartzJob.before()} 读出来置 ThreadLocal →
 * 业务任务（{@code WorkflowCronJob}）读 ThreadLocal 决定 triggerSource →
 * {@code AbstractQuartzJob.execute()} 的 finally 里清掉（Quartz 工作线程是池化复用的，
 * 泄漏了会让下一个任务被误记成手动触发）。
 *
 * @author OAC
 */
public final class ManualTriggerMarker
{
    /** JobDataMap 里放标记的键名 */
    public static final String KEY = "opc_manual_trigger";

    private static final ThreadLocal<Boolean> MANUAL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ManualTriggerMarker()
    {
    }

    /**
     * 标记当前线程正在执行的是「后台手动触发」的任务
     */
    public static void markManual()
    {
        MANUAL.set(Boolean.TRUE);
    }

    /**
     * 清掉标记 —— 必须用 remove() 而不是 set(FALSE)，不留引用
     */
    public static void clear()
    {
        MANUAL.remove();
    }

    /**
     * 当前线程是否是「后台手动触发」
     */
    public static boolean isManual()
    {
        return Boolean.TRUE.equals(MANUAL.get());
    }
}
