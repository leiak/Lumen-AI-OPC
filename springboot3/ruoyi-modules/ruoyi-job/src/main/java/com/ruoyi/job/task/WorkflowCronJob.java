package com.ruoyi.job.task;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.job.api.RemoteWorkflowService;
import com.ruoyi.job.util.ManualTriggerMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OPC 工作流定时触发任务。
 *
 * <p>由 sys_job 的 invoke_target 反射调用，例如：
 * {@code workflowCronJob.execute('finance_daily_report_v1')}。
 *
 * <p>必须位于 {@code com.ruoyi.job.task} 包下，否则会被
 * {@code Constants.JOB_WHITELIST_STR} 白名单拦截。
 *
 * @author OAC
 */
@Component("workflowCronJob")
public class WorkflowCronJob {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCronJob.class);

    private final RemoteWorkflowService remoteWorkflowService;

    public WorkflowCronJob(RemoteWorkflowService remoteWorkflowService) {
        this.remoteWorkflowService = remoteWorkflowService;
    }

    public void execute(String workflowCode) {
        log.info("[WorkflowCronJob] 触发工作流 code={}", workflowCode);

        // 「定时触发」与「后台点执行一次」在 Quartz 侧完全一样（同一个 CronTrigger、
        // 同一个 invoke_target），靠 AbstractQuartzJob 从 JobDataMap 里读出来的
        // 手动标记区分。前缀 quartz: / manual: 逐字对应 agent-hub 侧的
        // resolveTriggerType（quartz: → CRON，其它 → MANUAL）。
        String source = ManualTriggerMarker.isManual()
                ? "manual:" + workflowCode
                : "quartz:" + workflowCode;

        R<Map<String, Object>> result = remoteWorkflowService.trigger(
                workflowCode, source, SecurityConstants.INNER);

        // Feign 降级也会返回对象，这里必须显式判失败并抛出，
        // 否则 AbstractQuartzJob 会把失败记成 sys_job_log.status='0'
        if (result == null || result.getCode() != R.SUCCESS) {
            throw new IllegalStateException("触发工作流失败 code=" + workflowCode
                    + ", msg=" + (result == null ? "空响应" : result.getMsg()));
        }

        Map<String, Object> data = result.getData();
        String status = data == null ? null : String.valueOf(data.get("status"));

        log.info("[WorkflowCronJob] 完成 code={} runCode={} status={}",
                workflowCode,
                data == null ? null : data.get("runCode"),
                status);

        // 业务失败不能只记一行日志 —— 那会让 sys_job_log 记成成功。
        // 但不是所有非 SUCCESS 都要抛：PARTIAL / RUNNING 是系统的正常行为。
        if ("FAILED".equals(status)) {
            throw new IllegalStateException("工作流业务失败 code=" + workflowCode
                    + ", status=" + status + ", msg=" + (data == null ? "" : data.get("errorMessage")));
        }
        if ("PARTIAL".equals(status)) {
            // 数据已经落表，只是 LLM 那一步降级了 —— 记成定时任务失败会让
            // 「LLM 挂了」和「定时器没跑」两个问题互相污染。
            log.warn("[WorkflowCronJob] 工作流降级完成（数据已落表，LLM 未成功） code={} status={}", workflowCode, status);
        } else if ("RUNNING".equals(status)) {
            // 30 秒去重窗口：已有一条在跑，本次没有真的执行 —— 系统行为正确，不是失败
            log.warn("[WorkflowCronJob] 命中 30 秒去重窗口，已有运行中的任务 code={}", workflowCode);
        } else if (!"SUCCESS".equals(status)) {
            // status 为 null（响应体里没有状态）或不认识的状态 → 不知道有没有跑成 → 不能记成功
            throw new IllegalStateException("工作流返回了无法判定的状态 code=" + workflowCode + ", status=" + status);
        }
    }

}
