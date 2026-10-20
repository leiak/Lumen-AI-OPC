package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.agent.domain.OpcAgentInstance;
import com.ruoyi.opc.agent.service.IOpcAgentInstanceService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OpcMarketplaceInstanceController} 单元测试 — W80 P1 Task 6 第二段 commit
 *
 * <p>覆盖 2 个 endpoint（{@code POST /opc/agent/marketplace-instance/{create,revoke/{id}}}）：
 * <ul>
 *   <li><b>create</b>：request body 必填校验 + 调 instanceService.hire(companyId, defId, "MONTHLY", 1, nickname)
 *       + 返回 id/instanceCode/companyId/definitionId/status</li>
 *   <li><b>revoke</b>：调 instanceService.revoke(id) + 返回 {id, rowsAffected}</li>
 * </ul>
 *
 * <p>关键模式：同 {@link OpcWorkflowTriggerControllerTest} ——
 * 标 {@link InnerAuth}（内部鉴权），无 SecurityUtils mock，service 单测覆盖业务逻辑,
 * 本类专注 HTTP 层契约 + 注解契约。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcMarketplaceInstanceControllerTest {

    @Mock
    private IOpcAgentInstanceService instanceService;

    @InjectMocks
    private OpcMarketplaceInstanceController controller;

    private OpcAgentInstance sampleInstance(long id, long companyId, long definitionId) {
        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setId(id);
        inst.setInstanceCode("I2026102000000" + id);
        inst.setCompanyId(companyId);
        inst.setDefinitionId(definitionId);
        inst.setHireType("MONTHLY");
        inst.setStatus("RUNNING");
        return inst;
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("agentDefinitionId", 100L);
        body.put("tenantCompanyId", 200L);
        body.put("userId", 7L);
        body.put("nickname", "市场安装实例");
        return body;
    }

    // ==================== POST /opc/agent/marketplace-instance/create ====================

    @Test
    @DisplayName("create — 正常路径: hire(company, defId, \"MONTHLY\", 1, nickname) + 响应含 id/instanceCode/companyId/definitionId/status")
    void create_returnsAllFields() {
        OpcAgentInstance inst = sampleInstance(12345L, 200L, 100L);
        when(instanceService.hire(eq(200L), eq(100L), eq("MONTHLY"), eq(1), eq("市场安装实例")))
                .thenReturn(inst);

        R<Map<String, Object>> result = controller.create(validBody());

        assertEquals(R.SUCCESS, result.getCode(), "R.ok() 应返回 code=200");
        assertNotNull(result.getData());
        Map<String, Object> data = result.getData();
        assertEquals(12345L, data.get("id"));
        assertEquals("I202610200000012345", data.get("instanceCode"));
        assertEquals(200L, data.get("companyId"));
        assertEquals(100L, data.get("definitionId"));
        assertEquals("RUNNING", data.get("status"));
        verify(instanceService).hire(200L, 100L, "MONTHLY", 1, "市场安装实例");
        verifyNoMoreInteractions(instanceService);
    }

    @Test
    @DisplayName("create — nickname 缺省也能通(默认用 Agent 定义的 name —— 由 service.hire 兜底,本接口只透传)")
    void create_nicknameOptional() {
        Map<String, Object> body = validBody();
        body.remove("nickname");

        OpcAgentInstance inst = sampleInstance(12345L, 200L, 100L);
        when(instanceService.hire(eq(200L), eq(100L), eq("MONTHLY"), eq(1), any()))
                .thenReturn(inst);

        R<Map<String, Object>> result = controller.create(body);

        assertEquals(R.SUCCESS, result.getCode());
        // nickname 为 null 也要透传(service.hire 内部会回退到 def.getName())
        verify(instanceService).hire(200L, 100L, "MONTHLY", 1, null);
    }

    @Test
    @DisplayName("create — 缺 agentDefinitionId → 400 业务异常 + service.hire 不被调")
    void create_400_whenDefinitionIdMissing() {
        Map<String, Object> body = validBody();
        body.remove("agentDefinitionId");

        OpcException ex = assertThrows(OpcException.class, () -> controller.create(body));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("agentDefinitionId"), "错误消息应说明缺哪个字段");
        verifyNoMoreInteractions(instanceService);
    }

    @Test
    @DisplayName("create — 缺 tenantCompanyId → 400(防止 tenantCompanyId=0 兜底写脏数据)")
    void create_400_whenTenantCompanyIdMissing() {
        Map<String, Object> body = validBody();
        body.remove("tenantCompanyId");

        OpcException ex = assertThrows(OpcException.class, () -> controller.create(body));
        assertEquals(400, ex.getCode());
        verifyNoMoreInteractions(instanceService);
    }

    @Test
    @DisplayName("create — service.hire 抛 Agent 不存在 → 透传(非熔断,业务真实错误)")
    void create_serviceThrowsPropagates() {
        when(instanceService.hire(anyLong(), anyLong(), any(), anyInt(), any()))
                .thenThrow(new OpcException("Agent 定义不存在"));

        // 用 999 这种必然不存在的 id 模拟
        Map<String, Object> body = validBody();
        body.put("agentDefinitionId", 999L);

        OpcException ex = assertThrows(OpcException.class, () -> controller.create(body));
        assertTrue(ex.getMessage().contains("Agent 定义不存在"));
    }

    // ==================== POST /opc/agent/marketplace-instance/revoke/{id} ====================

    @Test
    @DisplayName("revoke — 正常路径: instanceService.revoke(id) + 响应 {id, rowsAffected}")
    void revoke_returnsOkWithRowsAffected() {
        when(instanceService.revoke(12345L)).thenReturn(1);

        R<Map<String, Object>> result = controller.revoke(12345L);

        assertEquals(R.SUCCESS, result.getCode(), "HTTP 仍 200(RuoYi 业务码),这是 best-effort 清理");
        assertNotNull(result.getData());
        Map<String, Object> data = result.getData();
        assertEquals(12345L, data.get("id"));
        assertEquals(1, data.get("rowsAffected"));
        verify(instanceService).revoke(12345L);
        verifyNoMoreInteractions(instanceService);
    }

    @Test
    @DisplayName("revoke — id 为 null → 400(controller 不应让 service.revoke(null) 落到 NPE)")
    void revoke_400_whenIdNull() {
        OpcException ex = assertThrows(OpcException.class, () -> controller.revoke(null));
        assertEquals(400, ex.getCode());
        verifyNoMoreInteractions(instanceService);
    }

    @Test
    @DisplayName("revoke — service.revoke 抛异常 → 透传(上层 marketplace 据此判定清理失败)")
    void revoke_serviceThrowsPropagates() {
        when(instanceService.revoke(999L)).thenThrow(new RuntimeException("DB connection lost"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> controller.revoke(999L));
        assertTrue(ex.getMessage().contains("DB connection lost"));
    }

    // ==================== Reflection: 验证注解契约 ====================

    @Test
    @DisplayName("create 方法标注 @InnerAuth(内部鉴权,要求 from-source: inner header)")
    void createMethod_isAnnotatedWithInnerAuth() throws Exception {
        Method m = OpcMarketplaceInstanceController.class.getMethod("create", Map.class);
        InnerAuth annotation = m.getAnnotation(InnerAuth.class);
        assertNotNull(annotation, "create 方法必须标注 @InnerAuth");
        assertTrue(!annotation.isUser(), "默认 isUser=false — 内部调用无需 user 信息");
    }

    @Test
    @DisplayName("create 方法 URL 契约: POST /marketplace-instance/create")
    void createMethod_urlAnnotations() throws Exception {
        Method m = OpcMarketplaceInstanceController.class.getMethod("create", Map.class);

        RequestMapping classMapping = OpcMarketplaceInstanceController.class
                .getAnnotation(RequestMapping.class);
        assertNotNull(classMapping, "类级别必须标注 @RequestMapping");
        assertArrayEquals(new String[]{"/opc/agent/marketplace-instance"}, classMapping.value());

        PostMapping postMapping = m.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "方法必须标注 @PostMapping");
        assertArrayEquals(new String[]{"/create"}, postMapping.value());
    }

    @Test
    @DisplayName("revoke 方法 URL 契约: POST /marketplace-instance/revoke/{id}")
    void revokeMethod_urlAnnotations() throws Exception {
        Method m = OpcMarketplaceInstanceController.class.getMethod("revoke", Long.class);

        PostMapping postMapping = m.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertArrayEquals(new String[]{"/revoke/{id}"}, postMapping.value());

        PathVariable pathVar = m.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVar, "参数 id 必须标注 @PathVariable");
        assertEquals("id", pathVar.value(), "@PathVariable name 应与 URL 占位符一致");

        InnerAuth inner = m.getAnnotation(InnerAuth.class);
        assertNotNull(inner, "revoke 方法也必须标注 @InnerAuth");
    }

    @Test
    @DisplayName("create 方法参数注解: 单个 Map<String,Object> @RequestBody")
    void createMethod_paramAnnotation() throws Exception {
        Method m = OpcMarketplaceInstanceController.class.getMethod("create", Map.class);
        assertEquals(1, m.getParameterCount(), "create 只有一个 body 参数");
        RequestBody body = m.getParameters()[0].getAnnotation(RequestBody.class);
        assertNotNull(body, "参数必须标注 @RequestBody");
    }

    @Test
    @DisplayName("revoke 响应不泄漏 owner 信息(无 SecurityUtils 登录态校验 —— orphan 无 mp_install 归属)")
    void revoke_doesNotRequireLogin() throws Exception {
        // 验证 revoke 方法在 SecurityContextHolder 无 user 时也能跑 —— 它就是不需要 user
        // 由 @InnerAuth 守门,from-source 头校验交给 gateway + InnerAuthAspect
        when(instanceService.revoke(anyLong())).thenReturn(1);
        R<Map<String, Object>> result = controller.revoke(999L);
        assertEquals(R.SUCCESS, result.getCode());
        // 没 login 也不会抛 "未登录" 之类的错(不像 OpcAgentController.hire)
        assertNull(result.getMsg() == null ? null : "msg-leak-check", "revoke 路径不应有 user/owner 信息泄露");
    }
}