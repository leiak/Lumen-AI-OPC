package com.ruoyi.opc.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private OpcAgentWorkflowRun newRun(OpcAgentWorkflow workflow, String triggerSource) {
        OpcAgentWorkflowRun run = new OpcAgentWorkflowRun();
        run.setRunCode(OpcCodeGenerator.runCode());
        run.setWorkflowId(workflow.getId());
        // company_id 非空；系统模板工作流（company_id 为 NULL）统一归到 0
        run.setCompanyId(workflow.getCompanyId() == null ? 0L : workflow.getCompanyId());
        run.setTriggerType(workflow.getTriggerType());
        run.setTriggerSource(triggerSource);
        run.setStatus(OpcConstants.TASK_RUNNING);
        run.setStartTime(new Date());
        run.setCreateBy(OpcConstants.SYSTEM_CODE);
        run.setUpdateBy(OpcConstants.SYSTEM_CODE);
        return run;
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
