package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;
import com.ruoyi.opc.agent.service.IWorkflowTriggerService;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcWorkflowTriggerController} 单元测试 — W4
 *
 * <p>覆盖 1 个 endpoint：{@code POST /opc/agent/workflows/{workflowCode}/trigger}。
 *
 * <p>关键模式：
 * <ul>
 *   <li>Controller 返回 {@link R}（RuoYi 统一响应），不是 {@code AjaxResult}（与 W2/W3 controller 不同）</li>
 *   <li>Controller 标注 {@link InnerAuth}（要求 {@code from-source: inner} header，非 SecurityUtils 鉴权）</li>
 *   <li>无 SecurityUtils mock — 该 controller 是内部调用，无 JWT 登录态</li>
 *   <li>Service 单测（{@code WorkflowTriggerServiceImplTest}）已覆盖 4 例，controller 单测专注 HTTP 层契约</li>
 * </ul>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcWorkflowTriggerControllerTest {

    @Mock
    private IWorkflowTriggerService triggerService;

    @InjectMocks
    private OpcWorkflowTriggerController controller;

    private static final String CODE = "finance_daily_report_v1";
    private static final String SOURCE = "quartz:test";

    private OpcAgentWorkflowRun successRun() {
        OpcAgentWorkflowRun r = new OpcAgentWorkflowRun();
        r.setId(1000L);
        r.setRunCode("R20260907000001");
        r.setWorkflowId(1L);
        r.setCompanyId(0L);
        r.setTriggerType("CRON");
        r.setTriggerSource(SOURCE);
        r.setStatus("SUCCESS");
        r.setErrorMessage(null);
        r.setDurationMs(123);
        return r;
    }

    private OpcAgentWorkflowRun failedRun() {
        OpcAgentWorkflowRun r = new OpcAgentWorkflowRun();
        r.setId(1001L);
        r.setRunCode("R20260907000002");
        r.setStatus("FAILED");
        r.setErrorMessage("LLM 调用超时");
        r.setDurationMs(5000);
        return r;
    }

    // ==================== POST /opc/agent/workflows/{workflowCode}/trigger ====================

    @Test
    @DisplayName("trigger — 正常：调 service.trigger，R.ok({runCode, status, durationMs, errorMessage})")
    void trigger_returnsROkWithAllFields() {
        OpcAgentWorkflowRun run = successRun();
        when(triggerService.trigger(CODE, SOURCE)).thenReturn(run);

        R<Map<String, Object>> result = controller.trigger(CODE, SOURCE);

        assertEquals(R.SUCCESS, result.getCode(),
                "R.ok() 应返回 code=200");
        assertNotNull(result.getData(), "data 不应为 null");
        Map<String, Object> data = result.getData();
        assertEquals("R20260907000001", data.get("runCode"));
        assertEquals("SUCCESS", data.get("status"));
        assertEquals(123, data.get("durationMs"));
        assertNull(data.get("errorMessage"), "成功路径 errorMessage 应为 null");

        verify(triggerService).trigger(CODE, SOURCE);
    }

    @Test
    @DisplayName("trigger — 失败：errorMessage 透传到 R body，前端可读")
    void trigger_failedRun_errorMessagePropagates() {
        OpcAgentWorkflowRun run = failedRun();
        when(triggerService.trigger(CODE, SOURCE)).thenReturn(run);

        R<Map<String, Object>> result = controller.trigger(CODE, SOURCE);

        assertEquals(R.SUCCESS, result.getCode(),
                "HTTP 仍 200（业务失败非系统异常）");
        Map<String, Object> data = result.getData();
        assertEquals("FAILED", data.get("status"));
        assertEquals("LLM 调用超时", data.get("errorMessage"),
                "Controller 不应吞掉 errorMessage");
        assertEquals(5000, data.get("durationMs"));
    }

    @Test
    @DisplayName("trigger — triggerSource 可选参数为 null 也能透传（service 层兼容）")
    void trigger_triggerSourceNull_alsoWorks() {
        OpcAgentWorkflowRun run = successRun();
        when(triggerService.trigger(CODE, null)).thenReturn(run);

        R<Map<String, Object>> result = controller.trigger(CODE, null);

        assertEquals(R.SUCCESS, result.getCode());
        verify(triggerService).trigger(CODE, null);
    }

    @Test
    @DisplayName("trigger — service 抛 OpcException → 透传给全局 @RestControllerAdvice")
    void trigger_serviceThrowsPropagates() {
        when(triggerService.trigger("nope", SOURCE))
                .thenThrow(new OpcException("工作流不存在: nope"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.trigger("nope", SOURCE));
        assertTrue(ex.getMessage().contains("工作流不存在"));
        // Controller 不应包装成 R.fail
        verifyNoMoreInteractions(triggerService);
    }

    @Test
    @DisplayName("trigger — service 抛工作流已停用 OpcException → 透传")
    void trigger_serviceThrowsDisabledPropagates() {
        when(triggerService.trigger(CODE, SOURCE))
                .thenThrow(new OpcException("工作流已停用: " + CODE));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.trigger(CODE, SOURCE));
        assertTrue(ex.getMessage().contains("已停用"));
    }

    @Test
    @DisplayName("trigger — service 抛 30 秒去重场景（返回已有 RUNNING 记录）→ controller 不区分，正常包装")
    void trigger_dedupeReturnsExisting_wrappedAsSuccess() {
        OpcAgentWorkflowRun existing = new OpcAgentWorkflowRun();
        existing.setId(999L);
        existing.setRunCode("R20260905000001");
        existing.setStatus("RUNNING");
        existing.setDurationMs(null);  // 30s 内的 RUNNING 还没结束
        existing.setErrorMessage(null);
        when(triggerService.trigger(CODE, SOURCE)).thenReturn(existing);

        R<Map<String, Object>> result = controller.trigger(CODE, SOURCE);

        assertEquals(R.SUCCESS, result.getCode(),
                "30s 去重命中不抛错，仍 R.ok(200)");
        Map<String, Object> data = result.getData();
        assertEquals("RUNNING", data.get("status"));
        assertEquals("R20260905000001", data.get("runCode"));
        assertNull(data.get("durationMs"), "未完成的 RUNNING durationMs 应透传 null");
    }

    // ==================== Reflection: 验证注解契约 ====================

    @Test
    @DisplayName("trigger 方法标注 @InnerAuth（内部鉴权，要求 from-source: inner header）")
    void triggerMethod_isAnnotatedWithInnerAuth() throws Exception {
        Method m = OpcWorkflowTriggerController.class.getMethod(
                "trigger", String.class, String.class);
        InnerAuth annotation = m.getAnnotation(InnerAuth.class);
        assertNotNull(annotation, "trigger 方法必须标注 @InnerAuth");
        assertFalse(annotation.isUser(), "默认 isUser=false — 内部调用无需 user 信息");
    }

    @Test
    @DisplayName("trigger 方法 URL 契约：POST + /{workflowCode}/trigger，参数 @PathVariable + @RequestParam")
    void triggerMethod_urlAndParamAnnotations() throws Exception {
        Method m = OpcWorkflowTriggerController.class.getMethod(
                "trigger", String.class, String.class);

        // 类级别 @RequestMapping
        RequestMapping classMapping = OpcWorkflowTriggerController.class
                .getAnnotation(RequestMapping.class);
        assertNotNull(classMapping, "类级别必须标注 @RequestMapping");
        assertArrayEquals(new String[]{"/opc/agent/workflows"}, classMapping.value(),
                "类级别路径必须为 /opc/agent/workflows");

        // 方法级别 @PostMapping
        PostMapping postMapping = m.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "方法必须标注 @PostMapping");
        assertArrayEquals(new String[]{"/{workflowCode}/trigger"}, postMapping.value(),
                "方法路径必须为 /{workflowCode}/trigger");

        // 参数注解
        Parameter[] params = m.getParameters();
        assertEquals(2, params.length, "trigger 方法应有 2 个参数");

        PathVariable pathVar = params[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVar, "第一个参数 workflowCode 必须标注 @PathVariable");
        assertEquals("workflowCode", pathVar.value(),
                "@PathVariable name 应与 URL 占位符一致");

        RequestParam reqParam = params[1].getAnnotation(RequestParam.class);
        assertNotNull(reqParam, "第二个参数 triggerSource 必须标注 @RequestParam");
        assertEquals("triggerSource", reqParam.value());
        assertTrue(reqParam.required() == false,
                "triggerSource 是可选参数（required=false）");
    }
}