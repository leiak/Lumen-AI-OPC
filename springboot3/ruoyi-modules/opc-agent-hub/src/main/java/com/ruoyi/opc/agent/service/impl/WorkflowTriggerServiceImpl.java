package com.ruoyi.opc.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.agent.domain.OpcAgentWorkflow;
import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowRunMapper;
import com.ruoyi.opc.agent.service.IWorkflowTriggerService;
import com.ruoyi.opc.agent.workflow.WorkflowEngine;
import com.ruoyi.opc.common.constant.OpcConstants;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;

/**
 * 工作流触发服务实现。
 *
 * <p>刻意不加 {@code @Transactional}：
 * <ul>
 *   <li>RUNNING 记录必须立刻提交，否则并发触发时 30 秒去重窗口查不到它；</li>
 *   <li>工作流执行失败时也要保留 run 记录用于排错，不能被回滚吃掉。</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTriggerServiceImpl implements IWorkflowTriggerService {

    /** 重复触发去重窗口 */
    private static final long DEDUPE_WINDOW_MS = 30_000L;

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /**
     * 定时器触发的前缀 —— ruoyi-job 的 WorkflowCronJob 调用时传的是
     * {@code "quartz:" + workflowCode}。见 ruoyi-job 的 WorkflowCronJob.execute()。
     */
    private static final String QUARTZ_TRIGGER_PREFIX = "quartz:";

    private final OpcAgentWorkflowMapper workflowMapper;
    private final OpcAgentWorkflowRunMapper runMapper;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public OpcAgentWorkflowRun trigger(String workflowCode, String triggerSource) {
        if (workflowCode == null || workflowCode.isBlank()) {
            throw new OpcException("workflowCode 不能为空");
        }

        OpcAgentWorkflowRun running = runMapper.findRunningByCodeSince(
                workflowCode, new Date(System.currentTimeMillis() - DEDUPE_WINDOW_MS));
        if (running != null) {
            log.warn("[workflow-trigger] {} 秒内已有执行中的任务，跳过本次触发 code={} runCode={}",
                    DEDUPE_WINDOW_MS / 1000, workflowCode, running.getRunCode());
            return running;
        }

        OpcAgentWorkflow workflow = workflowMapper.selectByCode(workflowCode);
        if (workflow == null) {
            throw new OpcException("工作流不存在: " + workflowCode);
        }
        if (!Integer.valueOf(1).equals(workflow.getEnabled())) {
            throw new OpcException("工作流已停用: " + workflowCode);
        }

        OpcAgentWorkflowRun run = newRun(workflow, triggerSource);
        runMapper.insert(run);

        long startedAt = System.currentTimeMillis();
        try {
            WorkflowEngine.WorkflowDefinition def = new WorkflowEngine.WorkflowDefinition();
            def.setDagJson(workflow.getDagJson());
            // 归属带进引擎 —— 工作流是定时触发的, 没有登录态,
            // 不显式带上 companyId, LLM 节点的 token/成本就记不进任何账。
            def.setCompanyId(workflow.getCompanyId() == null ? 0L : workflow.getCompanyId());
            def.setUserId(resolveTriggerUserId());
            WorkflowEngine.WorkflowResult result = workflowEngine.run(def, Collections.emptyMap());

            run.setStatus(result.isSuccess() ? OpcConstants.TASK_SUCCESS : OpcConstants.TASK_FAILED);
            run.setOutputResult(toJson(result.getOutputs()));
            run.setStepLogs(toJson(result.getStepLogs()));
            run.setErrorMessage(result.getErrorMessage());
        } catch (Exception e) {
            // WorkflowEngine.run 内部已兜底，这里只防御引擎自身抛出的非受检异常
            log.error("[workflow-trigger] 执行异常 code={} runCode={}", workflowCode, run.getRunCode(), e);
            run.setStatus(OpcConstants.TASK_FAILED);
            run.setErrorMessage(e.getMessage());
        } finally {
            run.setEndTime(new Date());
            run.setDurationMs((int) (System.currentTimeMillis() - startedAt));
            runMapper.update(run);
        }

        workflowMapper.updateRunStats(workflow.getId(), run.getEndTime(), nextRunAt(workflow));
        log.info("[workflow-trigger] 完成 code={} runCode={} status={} durationMs={}",
                workflowCode, run.getRunCode(), run.getStatus(), run.getDurationMs());
        return run;
    }

    /**
     * 触发者用户 ID。
     *
     * <p>触发入口是 {@code @InnerAuth} 的内部端点（只有 Quartz 能调），所以通常
     * <b>没有登录用户</b> → 记 0（系统）。若将来加了手动触发，这里会自动取到真实用户。</p>
     */
    private Long resolveTriggerUserId() {
        try {
            Long userId = SecurityUtils.getUserId();
            return userId != null ? userId : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private OpcAgentWorkflowRun newRun(OpcAgentWorkflow workflow, String triggerSource) {
        OpcAgentWorkflowRun run = new OpcAgentWorkflowRun();
        run.setRunCode(OpcCodeGenerator.runCode());
        run.setWorkflowId(workflow.getId());
        // company_id 非空；系统模板工作流（company_id 为 NULL）统一归到 0
        run.setCompanyId(workflow.getCompanyId() == null ? 0L : workflow.getCompanyId());
        run.setTriggerType(resolveTriggerType(triggerSource));
        run.setTriggerSource(triggerSource);
        run.setStatus(OpcConstants.TASK_RUNNING);
        run.setStartTime(new Date());
        run.setCreateBy(OpcConstants.SYSTEM_CODE);
        run.setUpdateBy(OpcConstants.SYSTEM_CODE);
        return run;
    }

    /**
     * 运行记录的 trigger_type 记的是**这次到底怎么触发的**，不是工作流配置里声明的类型。
     *
     * <p>以前这里直接 {@code run.setTriggerType(workflow.getTriggerType())} ——
     * 而工作流行上的 trigger_type 是它「打算」怎么被触发（CRON）。
     * 结果是：健康检查手动打的工作流、健康检查里触发的、运维在后台点「执行一次」的，
     * 全部在运行记录里写着 'CRON'。
     * 迁移 V20261033 之前那 32 条运行记录里没有一条是真的定时器触发的
     * （那时根本没有调度器），但表里写着 'CRON' —— 类型字段冒充事实。
     *
     * <p>现在按实际来源区分：
     * <ul>
     *   <li>{@code quartz:xxx} —— ruoyi-job 的 Quartz 定时器触发 → CRON</li>
     *   <li>其他（healthcheck / 人工 / 后台点「执行一次」）→ MANUAL</li>
     * </ul>
     * 健康检查第 25 节据此断言「确实有定时器触发过」。
     */
    private String resolveTriggerType(String triggerSource) {
        return triggerSource != null && triggerSource.startsWith(QUARTZ_TRIGGER_PREFIX)
                ? "CRON" : "MANUAL";
    }

    /**
     * 依据 cron_expression + timezone 推算下次执行时间，仅用于展示。
     * 表达式非法不应阻断执行，故失败时返回 null。
     */
    private Date nextRunAt(OpcAgentWorkflow workflow) {
        String cron = workflow.getCronExpression();
        if (cron == null || cron.isBlank()) {
            return null;
        }
        try {
            String zone = workflow.getTimezone() == null || workflow.getTimezone().isBlank()
                    ? DEFAULT_TIMEZONE : workflow.getTimezone();
            // Spring 的 CronExpression 不认 Quartz 的 '?'（"不指定"）；语义上等价于 '*'
            ZonedDateTime next = CronExpression.parse(cron.replace('?', '*'))
                    .next(ZonedDateTime.now(ZoneId.of(zone)));
            return next == null ? null : Date.from(next.toInstant());
        } catch (Exception e) {
            log.warn("[workflow-trigger] cron 解析失败 code={} cron={} : {}",
                    workflow.getWorkflowCode(), cron, e.getMessage());
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[workflow-trigger] 序列化失败: {}", e.getMessage());
            return null;
        }
    }

}
