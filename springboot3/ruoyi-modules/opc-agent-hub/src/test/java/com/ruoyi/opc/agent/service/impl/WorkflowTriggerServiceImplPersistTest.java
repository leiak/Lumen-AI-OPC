package com.ruoyi.opc.agent.service.impl;

import com.ruoyi.opc.agent.domain.OpcAgentWorkflow;
import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowRunMapper;
import com.ruoyi.opc.agent.workflow.WorkflowEngine;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowTriggerServiceImpl} 的 run 状态三态测试 —— Task 5
 *
 * <p>以前 run 的状态只有「成功 / 失败」两种。<b>但真实世界有第三种</b>：
 * TOOL 取到数、LLM 写文案挂了 —— 数字是真的，只是缺了解读。
 * 以前这种情形整条链一起报废，已经取到的真实数据被一起丢掉；
 * 现在产出物照样落表（{@code report_status='PARTIAL'}），run 记 {@code PARTIAL}。</p>
 *
 * <p>另一条边界：<b>persist 失败 → run 必须记 FAILED</b> ——
 * 产出物没落表 = 这件事没做成，不能记成功。</p>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowTriggerServiceImpl run 状态三态 单测")
class WorkflowTriggerServiceImplPersistTest {

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
    private final AtomicLong nextId = new AtomicLong(2000L);

    @BeforeEach
    void setUp() {
        workflow = new OpcAgentWorkflow();
        workflow.setId(1L);
        workflow.setWorkflowCode(CODE);
        workflow.setName("财务日报");
        workflow.setCompanyId(2L);
        workflow.setTriggerType("CRON");
        workflow.setDagJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"TOOL\"}],\"edges\":[]}");
        workflow.setEnabled(1);
        workflow.setCronExpression("0 0 9 * * ?");
        workflow.setTimezone("Asia/Shanghai");
        workflow.setRunCount(0);

        when(runMapper.findRunningByCodeSince(anyString(), any())).thenReturn(null);
        when(workflowMapper.selectByCode(CODE)).thenReturn(workflow);
        // 模拟 MyBatis useGeneratedKeys="true" keyProperty="id"
        when(runMapper.insert(any())).thenAnswer(inv -> {
            OpcAgentWorkflowRun r = inv.getArgument(0);
            r.setId(nextId.getAndIncrement());
            return 1;
        });
    }

    @Test
    @DisplayName("persist 失败 → run 记 FAILED（产出物没落表 = 这件事没做成）")
    void trigger_persistFails_marksRunFailed() {
        when(workflowEngine.run(any(), any())).thenReturn(WorkflowEngine.WorkflowResult.builder()
                .success(false)
                .errorMessage("日报落表失败: insight 没有返回结果")
                .outputs(Map.of("n1", "{\"query_bank_flow\":{}}"))
                .stepLogs(List.of(new WorkflowEngine.StepLog("n1", "TOOL")))
                .build());

        OpcAgentWorkflowRun run = service.trigger(CODE, "quartz:test");

        assertEquals("FAILED", run.getStatus());
        assertTrue(run.getErrorMessage().contains("日报落表失败"),
                "错误信息里要能看出是落表环节炸的");
    }

    @Test
    @DisplayName("LLM 挂了但 TOOL 取到数、persist 成功 → run 记 PARTIAL（不是 FAILED）")
    void trigger_llmFailedButDataFetched_marksRunPartial() {
        when(workflowEngine.run(any(), any())).thenReturn(WorkflowEngine.WorkflowResult.builder()
                .success(true)
                .partial(true)
                .llmError("provider 超时")
                .outputs(Map.of("n1", "{\"query_bank_flow\":{}}"))
                .stepLogs(List.of(new WorkflowEngine.StepLog("n1", "TOOL")))
                .build());

        OpcAgentWorkflowRun run = service.trigger(CODE, "quartz:test");

        assertEquals("PARTIAL", run.getStatus(),
                "数字是真的、只是缺 LLM 解读 —— 产出物已落表，不能记 FAILED");
    }

    @Test
    @DisplayName("全部成功 → run 记 SUCCESS")
    void trigger_allGood_marksRunSuccess() {
        when(workflowEngine.run(any(), any())).thenReturn(WorkflowEngine.WorkflowResult.builder()
                .success(true)
                .partial(false)
                .outputs(Map.of("n1", "{\"query_bank_flow\":{}}"))
                .stepLogs(List.of(new WorkflowEngine.StepLog("n1", "TOOL")))
                .build());

        OpcAgentWorkflowRun run = service.trigger(CODE, "quartz:test");

        assertEquals("SUCCESS", run.getStatus());
    }

    @Test
    @DisplayName("R-100：run id 传进工作流定义（落表时要带上它溯源）")
    void trigger_passesRunIdIntoDefinition() {
        when(workflowEngine.run(any(), any())).thenReturn(WorkflowEngine.WorkflowResult.builder()
                .success(true)
                .build());

        OpcAgentWorkflowRun run = service.trigger(CODE, "quartz:test");

        ArgumentCaptor<WorkflowEngine.WorkflowDefinition> captor =
                ArgumentCaptor.forClass(WorkflowEngine.WorkflowDefinition.class);
        verify(workflowEngine).run(captor.capture(), any());
        assertNotNull(run.getId(), "MyBatis 的 useGeneratedKeys 回填后 id 才有值");
        assertEquals(run.getId(), captor.getValue().getRunId(),
                "run id 必须在 insert 之后取 —— 少这一步 source_workflow_run_id 就是 null");
    }

}
