package com.ruoyi.job.task;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.job.api.RemoteWorkflowService;
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
        R<Map<String, Object>> result = remoteWorkflowService.trigger(
                workflowCode, "quartz:" + workflowCode, SecurityConstants.INNER);

        // Feign 降级也会返回对象，这里必须显式判失败并抛出，
        // 否则 AbstractQuartzJob 会把失败记成 sys_job_log.status='0'
        if (result == null || result.getCode() != R.SUCCESS) {
            throw new IllegalStateException("触发工作流失败 code=" + workflowCode
                    + ", msg=" + (result == null ? "空响应" : result.getMsg()));
        }

        Map<String, Object> data = result.getData();
        log.info("[WorkflowCronJob] 完成 code={} runCode={} status={}",
                workflowCode,
                data == null ? null : data.get("runCode"),
                data == null ? null : data.get("status"));
    }

}
