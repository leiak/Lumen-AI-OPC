package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;
import com.ruoyi.opc.agent.service.IWorkflowTriggerService;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OpcWorkflowTriggerController} 集成测试（@WebMvcTest）— W8.2
 *
 * <p>覆盖维度：
 * <ul>
 *   <li><b>HTTP status</b>：200 正常 + 200 业务异常（{@code OpcException} → {@code handleServiceException}）</li>
 *   <li><b>JSON 序列化</b>：{@code R<Map>} 的 {@code code/data/runCode/status/durationMs/errorMessage}</li>
 *   <li><b>@InnerAuth 契约</b>：{@code @WebMvcTest(addFilters=false)} 不加载 {@code InnerAuthInterceptor}，由 gateway 前置拦截；
 *       此处只验证 controller 业务逻辑不依赖 SecurityContextHolder（无用户名/用户ID 注入）</li>
 *   <li><b>W7 修复回归</b>：{@code OpcException(code, msg)} 自定义 code 通过 {@code handleServiceException} 正确传递</li>
 * </ul>
 *
 * <p>关键差异（对比 {@code OpcFinanceControllerMvcTest}）：
 * <ul>
 *   <li>响应类型是 {@code R<Map>}，不是 {@code AjaxResult} — 但 JSON 结构相同：{@code $.code / $.msg / $.data}</li>
 *   <li>无 {@code SecurityContextHolder.setUserName} 调用 — controller 不读鉴权上下文</li>
 *   <li>无 {@code @MockBean SecurityUtils} — 该 controller 不依赖它</li>
 * </ul>
 *
 * @author OAC
 */
@WebMvcTest(controllers = OpcWorkflowTriggerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
class OpcWorkflowTriggerControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IWorkflowTriggerService triggerService;

    private static final String WORKFLOW_CODE = "finance_daily_report_v1";
    private static final String TRIGGER_SOURCE = "quartz:test";

    private OpcAgentWorkflowRun successRun() {
        OpcAgentWorkflowRun r = new OpcAgentWorkflowRun();
        r.setId(1000L);
        r.setRunCode("R20260907000001");
        r.setWorkflowId(1L);
        r.setCompanyId(0L);
        r.setTriggerType("CRON");
        r.setTriggerSource(TRIGGER_SOURCE);
        r.setStatus("SUCCESS");
        r.setErrorMessage(null);
        r.setDurationMs(123);
        return r;
    }

    // ==================== POST /{workflowCode}/trigger — 正常路径 ====================

    @Test
    @DisplayName("trigger — service 返 SUCCESS run → HTTP 200 + JSON $.code=200 + data.runCode")
    void trigger_success_returns200WithRunCodeInJson() throws Exception {
        when(triggerService.trigger(eq(WORKFLOW_CODE), eq(TRIGGER_SOURCE))).thenReturn(successRun());

        mockMvc.perform(post("/opc/agent/workflows/{code}/trigger", WORKFLOW_CODE)
                        .param("triggerSource", TRIGGER_SOURCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.runCode").value("R20260907000001"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.durationMs").value(123))
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());

        verify(triggerService).trigger(WORKFLOW_CODE, TRIGGER_SOURCE);
    }

    @Test
    @DisplayName("trigger — FAILED run 的 errorMessage 正确序列化进 JSON")
    void trigger_failedRun_errorMessagePropagatesToJson() throws Exception {
        OpcAgentWorkflowRun failed = new OpcAgentWorkflowRun();
        failed.setRunCode("R20260907000002");
        failed.setStatus("FAILED");
        failed.setErrorMessage("LLM 调用超时");
        failed.setDurationMs(5000);
        when(triggerService.trigger(any(), any())).thenReturn(failed);

        mockMvc.perform(post("/opc/agent/workflows/{code}/trigger", WORKFLOW_CODE)
                        .param("triggerSource", TRIGGER_SOURCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("LLM 调用超时"))
                .andExpect(jsonPath("$.data.durationMs").value(5000));
    }

    @Test
    @DisplayName("trigger — triggerSource 省略（可选参数 required=false）也能成功")
    void trigger_withoutTriggerSourceParam_stillSucceeds() throws Exception {
        when(triggerService.trigger(eq(WORKFLOW_CODE), eq(null))).thenReturn(successRun());

        mockMvc.perform(post("/opc/agent/workflows/{code}/trigger", WORKFLOW_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(triggerService).trigger(WORKFLOW_CODE, null);
    }

    @Test
    @DisplayName("trigger — RUNNING 状态（30s 去重命中）→ 仍 HTTP 200 + status=RUNNING + durationMs=null")
    void trigger_runningDedupe_returns200WithRunningStatus() throws Exception {
        OpcAgentWorkflowRun running = new OpcAgentWorkflowRun();
        running.setRunCode("R20260905000001");
        running.setStatus("RUNNING");
        running.setDurationMs(null);
        running.setErrorMessage(null);
        when(triggerService.trigger(any(), any())).thenReturn(running);

        mockMvc.perform(post("/opc/agent/workflows/{code}/trigger", WORKFLOW_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.durationMs").doesNotExist());
    }

    // ==================== POST /{workflowCode}/trigger — 异常路径 ====================

    @Test
    @DisplayName("trigger — service 抛 OpcException(400, '工作流不存在') → HTTP 200 + JSON code=400（W7 修复）")
    void trigger_serviceThrowsOpcException400_returns200WithCode400() throws Exception {
        when(triggerService.trigger(eq("nope"), any()))
                .thenThrow(new OpcException(400, "工作流不存在: nope"));

        mockMvc.perform(post("/opc/agent/workflows/{code}/trigger", "nope")
                        .param("triggerSource", TRIGGER_SOURCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("工作流不存在: nope"));
    }

    @Test
    @DisplayName("trigger — service 抛 OpcException(默认 code=500) → HTTP 200 + JSON code=500（W7 修复）")
    void trigger_serviceThrowsOpcExceptionDefault_returns200WithCode500() throws Exception {
        when(triggerService.trigger(any(), any()))
                .thenThrow(new OpcException("工作流已停用: " + WORKFLOW_CODE));

        mockMvc.perform(post("/opc/agent/workflows/{code}/trigger", WORKFLOW_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("工作流已停用: " + WORKFLOW_CODE));
    }

    @Test
    @DisplayName("trigger — service 抛 OpcException(403, SECURITY, PromptGuard 场景) → HTTP 200 + JSON code=403")
    void trigger_serviceThrowsOpcException403Security_returns200WithCode403() throws Exception {
        when(triggerService.trigger(any(), any()))
                .thenThrow(new OpcException(403, "检测到潜在的 Prompt 注入", "SECURITY"));

        mockMvc.perform(post("/opc/agent/workflows/{code}/trigger", WORKFLOW_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("检测到潜在的 Prompt 注入"));
    }

    // ==================== POST /{workflowCode}/trigger — 边界 ====================

    @Test
    @DisplayName("trigger — 缺 @PathVariable workflowCode → HTTP 500/404（路径不匹配，无 service 调用）")
    void trigger_missingPathVar_returnsError() throws Exception {
        mockMvc.perform(post("/opc/agent/workflows//trigger"))
                .andExpect(status().is4xxClientError());
    }
}