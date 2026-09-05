package com.ruoyi.opc.agent.service.impl;

import com.ruoyi.opc.agent.domain.OpcAgentWorkflow;
import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowRunMapper;
import com.ruoyi.opc.agent.workflow.WorkflowEngine;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowTriggerServiceImpl} 单元测试。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowTriggerServiceImplTest {

    private static final String CODE = "finance_daily_report_v1";

    @Mock
    private OpcAgentWorkflowMapper workflowMapper;
    @Mock
    private OpcAgentWorkflowRunMapper runMapper;
    @Mock
    private WorkflowEngine workflowEngine;

    @InjectMocks
    private WorkflowTriggerServiceImpl service;

    private OpcAgentWorkflow workflow;
    private final AtomicLong nextId = new AtomicLong(1000L);
    /** run 对象插入后会被就地修改，插入瞬间的状态必须当场抓下来 */
    private final AtomicReference<String> statusAtInsert = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        workflow = new OpcAgentWorkflow();
        workflow.setId(1L);
        workflow.setWorkflowCode(CODE);
        workflow.setName("财务日报");
        workflow.setCompanyId(null);
        workflow.setTriggerType("CRON");
        workflow.setDagJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"LLM\"}],\"edges\":[]}");
        workflow.setEnabled(1);
        workflow.setCronExpression("0 0 9 * * ?");
        workflow.setTimezone("Asia/Shanghai");
        workflow.setRunCount(0);

        // 模拟 MyBatis useGeneratedKeys="true" keyProperty="id"
        when(runMapper.insert(any())).thenAnswer(inv -> {
            OpcAgentWorkflowRun r = inv.getArgument(0);
            r.setId(nextId.getAndIncrement());
            statusAtInsert.set(r.getStatus());
            return 1;
        });
    }

    @Test
    @DisplayName("正常触发：写入 RUNNING 记录并回填 SUCCESS + next_run_at")
    void trigger_legalCode_writesRunRow() {
        when(runMapper.findRunningByCodeSince(anyString(), any())).thenReturn(null);
        when(workflowMapper.selectByCode(CODE)).thenReturn(workflow);
        when(workflowEngine.run(any(), any())).thenReturn(WorkflowEngine.WorkflowResult.builder()
                .success(true)
                .outputs(Map.of("n1", "今日收支正常"))
                .stepLogs(List.of(new WorkflowEngine.StepLog("n1", "LLM")))
                .costMs(12L)
                .build());

        OpcAgentWorkflowRun run = service.trigger(CODE, "quartz:test");

        assertEquals("RUNNING", statusAtInsert.get(), "插入时必须是 RUNNING，否则 30 秒去重查不到");
        assertEquals("SUCCESS", run.getStatus());
        assertEquals(Long.valueOf(1L), run.getWorkflowId());
        assertEquals(Long.valueOf(0L), run.getCompanyId(), "系统模板工作流 company_id 为 NULL，应归到 0");
        assertEquals("CRON", run.getTriggerType());
        assertEquals("quartz:test", run.getTriggerSource());
        assertTrue(run.getRunCode().startsWith("R"), "runCode 应由 OpcCodeGenerator.runCode() 生成");
        assertTrue(run.getOutputResult().contains("n1"));
        assertTrue(run.getStepLogs().contains("LLM"), "StepLog 必须可被 Jackson 序列化");
        assertNotNull(run.getEndTime());
        assertNotNull(run.getDurationMs());

        verify(runMapper).update(run);

        ArgumentCaptor<Date> nextRunAt = ArgumentCaptor.forClass(Date.class);
        verify(workflowMapper).updateRunStats(eq(1L), any(Date.class), nextRunAt.capture());
        assertNotNull(nextRunAt.getValue(), "Quartz 的 '?' 需被规避，cron 必须能解析出下次执行时间");
        assertTrue(nextRunAt.getValue().after(new Date()));
    }

    @Test
    @DisplayName("工作流已停用：抛出 OpcException 且不写 run 记录")
    void trigger_disabledWorkflow_returnsError() {
        workflow.setEnabled(0);
        when(runMapper.findRunningByCodeSince(anyString(), any())).thenReturn(null);
        when(workflowMapper.selectByCode(CODE)).thenReturn(workflow);

        OpcException ex = assertThrows(OpcException.class, () -> service.trigger(CODE, "quartz:test"));
        assertTrue(ex.getMessage().contains("已停用"));
        verify(runMapper, never()).insert(any());
        verify(workflowEngine, never()).run(any(), any());
    }

    @Test
    @DisplayName("工作流编码不存在：抛出 OpcException")
    void trigger_unknownCode_throws() {
        when(runMapper.findRunningByCodeSince(anyString(), any())).thenReturn(null);
        when(workflowMapper.selectByCode("nope")).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class, () -> service.trigger("nope", "quartz:test"));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(runMapper, never()).insert(any());
    }

    @Test
    @DisplayName("30 秒内重复触发：直接返回已有 RUNNING 记录，不再执行引擎")
    void trigger_duplicateWithin30s_skipped() {
        OpcAgentWorkflowRun existing = new OpcAgentWorkflowRun();
        existing.setId(999L);
        existing.setRunCode("R20260905000001");
        existing.setStatus("RUNNING");
        when(runMapper.findRunningByCodeSince(eq(CODE), any())).thenReturn(existing);

        OpcAgentWorkflowRun run = service.trigger(CODE, "quartz:test");

        assertSame(existing, run);
        verify(workflowMapper, never()).selectByCode(anyString());
        verify(runMapper, never()).insert(any());
        verify(workflowEngine, never()).run(any(), any());
        verify(workflowMapper, never()).updateRunStats(any(), any(), any());
    }

}
