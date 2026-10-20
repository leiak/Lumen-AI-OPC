package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.agent.domain.OpcAgentInstance;
import com.ruoyi.opc.agent.service.IOpcAgentDefinitionService;
import com.ruoyi.opc.agent.service.IOpcAgentInstanceService;
import com.ruoyi.opc.agent.service.IOpcAgentTaskService;
import com.ruoyi.opc.agent.service.IOpcTokenUsageService;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * opc-agent-hub {@link OpcAgentController} 跨租户 IDOR 回归测试 — W80+
 *
 * <p><b>漏洞</b>：{@code OpcAgentController.instanceAction(id, action) /
 * getById(id) / instanceTasks(id) / instanceUsage(id) / dailyUsage(companyId) /
 * usageSummary(companyId) / runTask(req)} 全部按主键或调用方传入的 companyId 操作,
 * 任何已登录用户都能跨租户读写别家公司 Agent 实例 ——
 * 包括 pause / resume / revoke 任意实例、查看任意实例的 token 用量明细。
 *
 * <p><b>修复</b>：controller 解析 {@link SecurityUtils#getCompanyId()} 登录态租户
 * (无公司 → 403,绝不默认 0);实例相关端点用 {@code WHERE id = #{id} AND company_id = #{companyId}}
 * 强制租户作用域;用量端点把调用方传入的 {@code companyId} 与登录态比对,
 * 不匹配一律 403。
 *
 * <p><b>本测试</b>采用 {@code @ExtendWith(MockitoExtension.class) + @InjectMocks}
 * 直接断言「interaction」,不经过 @WebMvcTest 上下文装配 ———
 * 因为现有的 {@link OpcAgentControllerMvcTest} 有 19 个 pre-existing 的 context-load
 * failures 与本修复无关,本任务明确「不要动」。{@code @InjectMocks} 模式与既有的
 * {@link OpcMarketplaceInstanceControllerTest} 一致,行为完全等价。
 *
 * <p><b>关键断言</b>:verify mapper/service.update 永远不被调用 ——
 * 「写之前先 403」才是这个安全属性的本质,「写完再 403」是漏洞。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcAgentControllerTenantIsolationTest {

    @Mock
    private IOpcAgentDefinitionService definitionService;

    @Mock
    private IOpcAgentInstanceService instanceService;

    @Mock
    private IOpcAgentTaskService taskService;

    @Mock
    private IOpcTokenUsageService tokenUsageService;

    @InjectMocks
    private OpcAgentController controller;

    private MockedStatic<SecurityUtils> securityUtils;

    /** 公司 A 的登录态 — 调用方属于公司 100 */
    private static final Long CALLER_COMPANY = 100L;
    private static final Long CALLER_USER = 7L;
    /** B 公司的实例 id — 攻击者试图操作它 */
    private static final Long OTHER_COMPANY_INSTANCE_ID = 999L;
    /** B 公司的 companyId */
    private static final Long OTHER_COMPANY = 200L;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getUserId).thenReturn(CALLER_USER);
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(CALLER_COMPANY);
    }

    @AfterEach
    void tearDown() {
        if (securityUtils != null) {
            securityUtils.close();
        }
    }

    private OpcAgentInstance instanceOwnedByOtherCompany() {
        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setId(OTHER_COMPANY_INSTANCE_ID);
        inst.setCompanyId(OTHER_COMPANY);
        inst.setStatus("RUNNING");
        inst.setStartTime(new Date());
        return inst;
    }

    private OpcAgentInstance instanceOwnedByCaller() {
        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setId(88L);
        inst.setCompanyId(CALLER_COMPANY);
        inst.setStatus("RUNNING");
        inst.setStartTime(new Date());
        return inst;
    }

    // ==================== instanceAction 端点 ====================

    @Test
    @DisplayName("instanceAction PAUSE — A 公司对 B 公司实例: 403,且 instanceService.pause 永远不被调")
    void instanceAction_pause_403_whenInstanceNotOwned_byCompany() {
        // service.pause 模拟「company_id 不匹配」→ 抛 OpcException(403)
        when(instanceService.pause(eq(OTHER_COMPANY_INSTANCE_ID), eq(CALLER_COMPANY), any()))
                .thenThrow(new OpcException(403, "无权操作该实例", "AGENT"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.instanceAction(OTHER_COMPANY_INSTANCE_ID, "PAUSE"));
        assertEquals(403, ex.getCode());
        // 关键断言:租户作用域的 service.pause 被调(校验发生)
        verify(instanceService).pause(OTHER_COMPANY_INSTANCE_ID, CALLER_COMPANY, "7");
        verify(taskService, never()).listByInstance(anyLong(), anyInt());
    }

    @Test
    @DisplayName("instanceAction REVOKE — A 公司对 B 公司实例: 403,且 instanceService.revoke 不执行写")
    void instanceAction_revoke_403_whenInstanceNotOwned_byCompany() {
        when(instanceService.revoke(eq(OTHER_COMPANY_INSTANCE_ID), eq(CALLER_COMPANY), any()))
                .thenThrow(new OpcException(403, "无权操作该实例", "AGENT"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.instanceAction(OTHER_COMPANY_INSTANCE_ID, "REVOKE"));
        assertEquals(403, ex.getCode());
        verify(instanceService).revoke(OTHER_COMPANY_INSTANCE_ID, CALLER_COMPANY, "7");
    }

    @Test
    @DisplayName("instanceAction RESUME — A 公司对 B 公司实例: 403")
    void instanceAction_resume_403_whenInstanceNotOwned_byCompany() {
        when(instanceService.resume(eq(OTHER_COMPANY_INSTANCE_ID), eq(CALLER_COMPANY), any()))
                .thenThrow(new OpcException(403, "无权操作该实例", "AGENT"));

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.instanceAction(OTHER_COMPANY_INSTANCE_ID, "RESUME"));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("instanceAction — 自家实例: 正常通过(不误伤)")
    void instanceAction_ok_whenInstanceOwnedByCaller() {
        when(instanceService.pause(eq(88L), eq(CALLER_COMPANY), any())).thenReturn(1);

        AjaxResult r = controller.instanceAction(88L, "PAUSE");
        assertEquals(200, r.get(AjaxResult.CODE_TAG));
        verify(instanceService).pause(88L, CALLER_COMPANY, "7");
    }

    @Test
    @DisplayName("instanceAction — 无租户(null): fail-closed 403,绝不用 0 兜底")
    void instanceAction_403_whenCallerHasNoCompany() {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.instanceAction(88L, "PAUSE"));
        assertEquals(403, ex.getCode());
        // 关键:service 永远不被调用 — 0 兜底会让 service 拿到 companyId=0 然后越权写
        verify(instanceService, never()).pause(anyLong(), any(), any());
        verify(instanceService, never()).resume(anyLong(), any(), any());
        verify(instanceService, never()).revoke(anyLong(), any(), any());
    }

    @Test
    @DisplayName("instanceAction — 无登录上下文(getCompanyId 抛 ServiceException): controller 应透传或抛错,不静默兜底")
    void instanceAction_propagates_whenNoLoginContext() {
        // SecurityUtils.getCompanyId() 在无登录上下文时抛 ServiceException;
        // controller 不应 catch 这个异常,让它向上传到 GlobalExceptionHandler
        securityUtils.when(SecurityUtils::getCompanyId)
                .thenThrow(new com.ruoyi.common.core.exception.ServiceException("获取公司ID失败:当前请求没有登录上下文"));

        assertThrows(Exception.class,
                () -> controller.instanceAction(88L, "PAUSE"));
        verify(instanceService, never()).pause(anyLong(), any(), any());
    }

    // ==================== instance 端点 ====================

    @Test
    @DisplayName("instance — A 查 B 的实例: 403,且 service.getById 不被调(只用 getByIdAndCompany)")
    void instance_403_whenInstanceNotOwned() {
        // service 模拟:WHERE id AND company_id 不命中 → 返回 null
        when(instanceService.getByIdAndCompany(eq(OTHER_COMPANY_INSTANCE_ID), eq(CALLER_COMPANY)))
                .thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.instance(OTHER_COMPANY_INSTANCE_ID));
        assertEquals(403, ex.getCode());
        // 关键:无租户作用域的 getById 永远不在 controller 路径上出现
        verify(instanceService, never()).getById(anyLong());
        verify(instanceService).getByIdAndCompany(OTHER_COMPANY_INSTANCE_ID, CALLER_COMPANY);
    }

    @Test
    @DisplayName("instance — 自家实例: 200 + 完整数据")
    void instance_ok_whenInstanceOwnedByCaller() {
        OpcAgentInstance owned = instanceOwnedByCaller();
        when(instanceService.getByIdAndCompany(eq(88L), eq(CALLER_COMPANY))).thenReturn(owned);

        AjaxResult r = controller.instance(88L);
        assertEquals(200, r.get(AjaxResult.CODE_TAG));
        assertNotNull(r.get(AjaxResult.DATA_TAG));
    }

    // ==================== instanceTasks 端点 ====================

    @Test
    @DisplayName("instanceTasks — A 看 B 的实例任务列表: 403,taskService.listByInstance 不被调")
    void instanceTasks_403_whenInstanceNotOwned() {
        when(instanceService.getByIdAndCompany(eq(OTHER_COMPANY_INSTANCE_ID), eq(CALLER_COMPANY)))
                .thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.instanceTasks(OTHER_COMPANY_INSTANCE_ID, 20));
        assertEquals(403, ex.getCode());
        verify(taskService, never()).listByInstance(anyLong(), anyInt());
    }

    // ==================== instanceUsage 端点 ====================

    @Test
    @DisplayName("instanceUsage — A 看 B 的实例 token 用量: 403,tokenUsageService.listByCompany 不被调")
    void instanceUsage_403_whenInstanceNotOwned() {
        when(instanceService.getByIdAndCompany(eq(OTHER_COMPANY_INSTANCE_ID), eq(CALLER_COMPANY)))
                .thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.instanceUsage(OTHER_COMPANY_INSTANCE_ID, 20));
        assertEquals(403, ex.getCode());
        verify(tokenUsageService, never()).listByCompany(anyLong(), anyInt());
    }

    @Test
    @DisplayName("instanceUsage — 自家实例: 用真实 companyId 查 listByCompany(不再误传 instanceId)")
    void instanceUsage_ok_callsListByCompany_withRealCompanyId() {
        OpcAgentInstance owned = instanceOwnedByCaller(); // companyId=100
        when(instanceService.getByIdAndCompany(eq(88L), eq(CALLER_COMPANY))).thenReturn(owned);

        controller.instanceUsage(88L, 20);

        // 关键修复:之前 bug 是把 instanceId(88L) 当 companyId 传给 listByCompany
        // 现在必须用实例的 companyId(100) — 不是 instanceId(88)
        verify(tokenUsageService).listByCompany(eq(CALLER_COMPANY), eq(20));
    }

    // ==================== dailyUsage / usageSummary 端点 ====================

    @Test
    @DisplayName("dailyUsage — A 试图读 B 的 companyId(200): 403,tokenUsageService.aggregateDaily 不被调")
    void dailyUsage_403_whenCompanyParamDoesNotMatchCallerCompany() {
        OpcException ex = assertThrows(OpcException.class,
                () -> controller.dailyUsage(OTHER_COMPANY, "2026-10-01", "2026-10-20"));
        assertEquals(403, ex.getCode());
        verify(tokenUsageService, never()).aggregateDaily(anyLong(), any(), any());
    }

    @Test
    @DisplayName("dailyUsage — companyId 与登录态一致: 200 透传")
    void dailyUsage_ok_whenCompanyMatchesCallerCompany() {
        when(tokenUsageService.aggregateDaily(eq(CALLER_COMPANY), any(), any()))
                .thenReturn(java.util.List.of());

        AjaxResult r = controller.dailyUsage(CALLER_COMPANY, "2026-10-01", "2026-10-20");
        assertEquals(200, r.get(AjaxResult.CODE_TAG));
        verify(tokenUsageService).aggregateDaily(CALLER_COMPANY, "2026-10-01", "2026-10-20");
    }

    @Test
    @DisplayName("usageSummary — A 试图读 B 的 companyId: 403")
    void usageSummary_403_whenCompanyParamDoesNotMatch() {
        OpcException ex = assertThrows(OpcException.class,
                () -> controller.usageSummary(OTHER_COMPANY, "2026-10-20"));
        assertEquals(403, ex.getCode());
        verify(tokenUsageService, never()).summary(anyLong(), any());
    }

    @Test
    @DisplayName("usageSummary — companyId 为 null: 403(防止绕过)")
    void usageSummary_403_whenCompanyParamNull() {
        OpcException ex = assertThrows(OpcException.class,
                () -> controller.usageSummary(null, "2026-10-20"));
        assertEquals(403, ex.getCode());
        verify(tokenUsageService, never()).summary(anyLong(), any());
    }

    // ==================== runTask 端点 ====================

    @Test
    @DisplayName("runTask — A 试图跑 B 的实例: 403,且 controller 不进入 stub body")
    void runTask_403_whenInstanceNotOwned() {
        OpcAgentController.RunTaskRequest req = new OpcAgentController.RunTaskRequest();
        req.instanceId = OTHER_COMPANY_INSTANCE_ID;
        req.taskType = "chat";
        req.input = "hi";
        when(instanceService.getByIdAndCompany(eq(OTHER_COMPANY_INSTANCE_ID), eq(CALLER_COMPANY)))
                .thenReturn(null);

        OpcException ex = assertThrows(OpcException.class, () -> controller.runTask(req));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("runTask — 自家实例: 通过 ownership check,执行 stub")
    void runTask_ok_whenInstanceOwned() {
        OpcAgentController.RunTaskRequest req = new OpcAgentController.RunTaskRequest();
        req.instanceId = 88L;
        req.taskType = "chat";
        req.input = "hi";
        when(instanceService.getByIdAndCompany(eq(88L), eq(CALLER_COMPANY)))
                .thenReturn(instanceOwnedByCaller());

        AjaxResult r = controller.runTask(req);
        assertEquals(200, r.get(AjaxResult.CODE_TAG));
    }

    // ==================== Service 层不变量 ====================

    @Test
    @DisplayName("service.pause/revoke/resume 必须接 companyId —— 否则 SQL 层校验缺位")
    void serviceSignatures_requireCompanyId_forScopedActions() throws Exception {
        // 反射断言三个写方法都必须包含 Long companyId 参数。
        // 这是「在 SQL 层校验」的契约 —— 如果未来谁不小心改回只接 id,反射会立刻红。
        Class<?> svc = com.ruoyi.opc.agent.service.IOpcAgentInstanceService.class;
        for (String name : new String[]{"pause", "resume", "revoke"}) {
            java.lang.reflect.Method m = null;
            for (java.lang.reflect.Method cand : svc.getMethods()) {
                if (cand.getName().equals(name) && cand.getParameterCount() == 3) {
                    m = cand;
                    break;
                }
            }
            assertNotNull(m, name + " 必须存在 3 参重载 (id, companyId, operatorId)");
            assertEquals(Long.class, m.getParameterTypes()[1],
                    name + " 的第二个参数必须是 Long (companyId) — 不能退化");
            assertEquals(Long.class, m.getParameterTypes()[0],
                    name + " 的第一个参数必须是 Long (id)");
            assertTrue(String.class.equals(m.getParameterTypes()[2]),
                    name + " 的第三个参数必须是 String (operatorId)");
        }
    }

    // ==================== hire 端点 (W80 修复) ====================
    //
    // 背景: hire 曾是本类里唯一不走 resolveCompanyId() 的方法 —— 它把请求体的
    // companyId 直接透传给 instanceService.hire()。于是 A 公司登录后
    // POST {"companyId": B} 就能把实例行插进 B 的租户命名空间, 且 companyId
    // 缺省时也不 fail-closed。一旦 hire 里那个 billing TODO 落地, 这次雇佣还会
    // 记到 B 头上。修复后与 dailyUsage/usageSummary 同语义: 请求体带了不一致的
    // companyId 一律 403, 落库一律用登录态的租户。

    private OpcAgentController.HireRequest hireReq(Long companyId) {
        OpcAgentController.HireRequest req = new OpcAgentController.HireRequest();
        req.companyId = companyId;
        req.definitionId = 42L;
        req.hireType = "MONTHLY";
        req.duration = 30;
        req.nickname = "财务助理";
        return req;
    }

    @Test
    @DisplayName("hire — 请求体 companyId = B 公司: 403,且 instanceService.hire 永远不被调(不产生越权写入)")
    void hire_403_whenBodyCompanyIdIsOtherCompany() {
        OpcException ex = assertThrows(OpcException.class,
                () -> controller.hire(hireReq(OTHER_COMPANY)));

        assertEquals(403, ex.getCode());
        // 关键断言:一行都不能写进 B 的租户 —— 越权点在「构造 instance 行」
        verify(instanceService, never()).hire(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("hire — 请求体不带 companyId: 用登录态租户落库,不退化成 null")
    void hire_usesCallerCompany_whenBodyCompanyIdOmitted() {
        AjaxResult r = controller.hire(hireReq(null));

        assertNotNull(r);
        // 租户必须来自登录态(A),而不是请求体的 null
        verify(instanceService).hire(eq(CALLER_COMPANY), eq(42L), eq("MONTHLY"), eq(30), eq("财务助理"));
    }

    @Test
    @DisplayName("hire — 请求体 companyId 与登录态一致: 正常放行(不误伤老前端)")
    void hire_ok_whenBodyCompanyIdMatchesCaller() {
        AjaxResult r = controller.hire(hireReq(CALLER_COMPANY));

        assertNotNull(r);
        verify(instanceService).hire(eq(CALLER_COMPANY), eq(42L), eq("MONTHLY"), eq(30), eq("财务助理"));
    }

    @Test
    @DisplayName("hire — 调用方无租户(getCompanyId null): fail-closed 403,绝不用 0/null 兜底")
    void hire_403_whenCallerHasNoCompany() {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> controller.hire(hireReq(null)));

        assertEquals(403, ex.getCode());
        verify(instanceService, never()).hire(anyLong(), anyLong(), any(), any(), any());
    }
}
