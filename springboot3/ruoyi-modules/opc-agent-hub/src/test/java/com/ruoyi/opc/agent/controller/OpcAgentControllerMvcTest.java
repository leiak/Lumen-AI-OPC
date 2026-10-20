package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.agent.domain.OpcAgentDefinition;
import com.ruoyi.opc.agent.domain.OpcAgentInstance;
import com.ruoyi.opc.agent.mapper.OpcAgentDefinitionMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentInstanceMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentTaskMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentTokenUsageMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentWorkflowRunMapper;
import com.ruoyi.opc.agent.service.IOpcAgentDefinitionService;
import com.ruoyi.opc.agent.service.IOpcAgentInstanceService;
import com.ruoyi.opc.agent.service.IOpcAgentTaskService;
import com.ruoyi.opc.agent.service.IOpcTokenUsageService;
import com.ruoyi.opc.agent.workflow.WorkflowEngine;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OpcAgentController} 集成测试（@WebMvcTest）— W8.3
 *
 * <p>覆盖 3 个核心 endpoint（agent-hub 共 12 个 endpoint，本测试聚焦高频路径）：
 * <ul>
 *   <li>GET /opc/agent/market — 市场列表（无 SecurityUtils）</li>
 *   <li>GET /opc/agent/detail/{id} — 详情（null → OpcException → 500）</li>
 *   <li>POST /opc/agent/hire — 雇佣（SecurityUtils.getUserId 注入 + service.hire 调通）</li>
 * </ul>
 *
 * <p>覆盖维度：
 * <ul>
 *   <li><b>HTTP status</b>：200 正常 + 200 service 业务异常（W7 修复）+ 400 缺请求体</li>
 *   <li><b>JSON 序列化</b>：{@code $.data[].code/name/category}（列表）、{@code $.data.code}（详情）、{@code $.data.id}（hire）</li>
 *   <li><b>401 鉴权</b>：{@code hire} 匿名 → SecurityUtils.getUserId() null → controller 抛 OpcException("未登录")</li>
 *   <li><b>W7 修复回归</b>：{@code OpcException(404, "Agent 不存在")} → HTTP 200 + JSON code=404</li>
 * </ul>
 *
 * <p>关键设计点：
 * <ul>
 *   <li>{@link OpcAgentController#detail(Long)} 在 service 返回 null 时主动抛 {@code OpcException("Agent 不存在")} —
 *       验证 controller 层的 fallback 行为（单元测试用 mockStatic 不易覆盖）</li>
 *   <li>{@link OpcAgentController#hire(HireRequest)} 通过 {@code SecurityUtils.getUserId()} 注入 userId,
 *       并通过 {@code SecurityUtils.getCompanyId()} 取租户 (W80 修复后不再信请求体的 companyId) —
 *       验证 SecurityContextHolder 与 controller 的 HTTP 链路集成</li>
 * </ul>
 *
 * @author OAC
 */
// excludeFilters 单独用【不够】: {@code OpcAgentHubApplication} 上有显式
// {@code @ComponentScan({"com.ruoyi.opc","com.ruoyi.agent"})}, 它会覆盖 {@code @WebMvcTest}
// 的类型排除过滤器 —— 于是 {@code controllers = OpcAgentController.class} 形同虚设,
// 同包的 OpcWorkflowTriggerController 照样被加载,再经 WorkflowTriggerServiceImpl
// 拖进 {@code OpcAgentWorkflowMapper},而 @WebMvcTest 不启 MyBatis,直接抛
// "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required",整类 11/11 全红。
//
// 而 excludeFilters 之所以也留着: {@code @MapperScan} 注册的 bean 不走组件扫描,
// 类型过滤器同样管不到它 —— 所以真正让 context 起来的是下面那个 {@code @MockBean}。
// 两者缺一不可(与 opc-marketplace 的 CategoryControllerTest 同一套路)。
@WebMvcTest(controllers = OpcAgentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.ruoyi\\.opc\\.agent\\.mapper\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
class OpcAgentControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IOpcAgentDefinitionService definitionService;

    @MockBean
    private IOpcAgentInstanceService instanceService;

    @MockBean
    private IOpcAgentTaskService taskService;

    @MockBean
    private IOpcTokenUsageService tokenUsageService;

    /**
     * 让被同包 {@code @ComponentScan} 拖进来的 OpcWorkflowTriggerController 有 mapper 可注入。
     *
     * <p>必须把 {@code mapper} 包【全部】占位,而不是只补报错的那一个 ——
     * {@code WorkflowTriggerServiceImpl} 构造器同时注入 {@code OpcAgentWorkflowMapper} 和
     * {@code OpcAgentWorkflowRunMapper},逐个补会变成打地鼠。
     */
    @MockBean private OpcAgentDefinitionMapper agentDefinitionMapper;
    @MockBean private OpcAgentInstanceMapper agentInstanceMapper;
    @MockBean private OpcAgentTaskMapper agentTaskMapper;
    @MockBean private OpcAgentTokenUsageMapper agentTokenUsageMapper;
    @MockBean private OpcAgentWorkflowMapper agentWorkflowMapper;
    @MockBean private OpcAgentWorkflowRunMapper agentWorkflowRunMapper;

    /**
     * 截断 {@code WorkflowTriggerServiceImpl} 的第三条依赖链。
     *
     * <p>上面 6 个 mapper 解决的是 constructor param 0/1;param 2 是
     * {@code WorkflowEngine},它会一路拖进 opc-ai-core 的
     * {@code AgentRuntime → LlmGateway → LlmCache → RedisService}。
     * 与其继续补 RedisService,不如直接把 WorkflowEngine 本身占位 ——
     * 断在链条入口,后面整条链都不再需要 bean。
     */
    @MockBean
    private WorkflowEngine workflowEngine;

    /**
     * 最后一片叶子。{@code @ComponentScan({"com.ruoyi.opc"})} 会把 opc-ai-core jar 里的
     * {@code OpcLlmController} 也扫进来,它的 {@code LlmGateway → LlmCache} 又要 RedisService。
     * RedisService 在纯 Web 切片里没有理由存在(更不该连真 Redis),直接占位。
     */
    @MockBean
    private RedisService redisService;

    private static final Long USER_ID = 3003L;
    private static final Long COMPANY_ID = 1001L;
    private static final Long DEFINITION_ID = 42L;

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.setUserId(String.valueOf(USER_ID));
        // hire 现在从登录态取租户 (W80 修复),所以上下文里必须真有 LoginUser。
        // 只 setUserId 的话 SecurityUtils.getCompanyId() 走 getLoginUser() 拿不到,
        // 会抛 ServiceException —— 那正是这个 bug 之前藏得住的原因:
        // 旧实现只读请求体的 companyId,压根没碰过登录态的租户。
        LoginUser loginUser = new LoginUser();
        loginUser.setCompanyId(COMPANY_ID);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.remove();
    }

    private OpcAgentDefinition sampleDefinition() {
        OpcAgentDefinition d = new OpcAgentDefinition();
        d.setId(DEFINITION_ID);
        // agentCode / priceMonthly 与 DB 列 agent_code / price_monthly 对齐（schema 对齐重命名）
        d.setAgentCode("finance_daily_v1");
        d.setName("财务日报 Agent");
        d.setCategory("finance");
        d.setStatus("PUBLISHED");
        d.setPriceMonthly(new BigDecimal("99.00"));
        return d;
    }

    private OpcAgentInstance sampleInstance() {
        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setId(99L);
        inst.setCompanyId(COMPANY_ID);
        // opc_agent_instance 无 user_id 列：实例归属靠 opc_company_member join，操作者落到 create_by 审计列
        inst.setCreateBy(String.valueOf(USER_ID));
        inst.setDefinitionId(DEFINITION_ID);
        inst.setHireType("MONTHLY");
        inst.setStatus("RUNNING");
        inst.setStartTime(new Date());
        return inst;
    }

    // ==================== GET /opc/agent/market ====================

    @Test
    @DisplayName("market — service 返回列表 → HTTP 200 + JSON $.data 是数组 + 第 1 项 code/name/category")
    void market_returnsListSerializedInJson() throws Exception {
        when(definitionService.listPublished(any())).thenReturn(Arrays.asList(sampleDefinition()));

        mockMvc.perform(get("/opc/agent/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].agentCode").value("finance_daily_v1"))
                .andExpect(jsonPath("$.data[0].name").value("财务日报 Agent"))
                .andExpect(jsonPath("$.data[0].category").value("finance"));

        verify(definitionService).listPublished(null);
    }

    @Test
    @DisplayName("market — category=finance 透传给 service")
    void market_categoryParamPassesToService() throws Exception {
        when(definitionService.listPublished("finance")).thenReturn(Arrays.asList(sampleDefinition()));

        mockMvc.perform(get("/opc/agent/market").param("category", "finance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("finance"));

        verify(definitionService).listPublished("finance");
    }

    @Test
    @DisplayName("market — service 返回空列表 → HTTP 200 + JSON $.data = []")
    void market_emptyList_returns200WithEmptyArray() throws Exception {
        when(definitionService.listPublished(any())).thenReturn(Arrays.asList());

        mockMvc.perform(get("/opc/agent/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("market — service 抛 OpcException(500) → HTTP 200 + JSON code=500")
    void market_serviceThrows_returns200WithCode500() throws Exception {
        when(definitionService.listPublished(any()))
                .thenThrow(new OpcException("查询 agent_definition 失败：DB 连接超时"));

        mockMvc.perform(get("/opc/agent/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("查询 agent_definition 失败：DB 连接超时"));
    }

    // ==================== GET /opc/agent/detail/{id} ====================

    @Test
    @DisplayName("detail — service 返回 definition → HTTP 200 + JSON $.data.code")
    void detail_returnsDefinitionSerializedInJson() throws Exception {
        when(definitionService.getById(DEFINITION_ID)).thenReturn(sampleDefinition());

        mockMvc.perform(get("/opc/agent/detail/{id}", DEFINITION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.agentCode").value("finance_daily_v1"))
                .andExpect(jsonPath("$.data.name").value("财务日报 Agent"));

        verify(definitionService).getById(DEFINITION_ID);
    }

    @Test
    @DisplayName("detail — service 返回 null → controller 抛 OpcException → HTTP 200 + JSON code=500")
    void detail_serviceReturnsNull_throwsOpcExceptionViaController() throws Exception {
        when(definitionService.getById(anyLong())).thenReturn(null);

        mockMvc.perform(get("/opc/agent/detail/{id}", 9999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("Agent 不存在"));
    }

    @Test
    @DisplayName("detail — service 抛 OpcException(404, '已下架') → HTTP 200 + JSON code=404（W7 修复）")
    void detail_serviceThrowsOpcExceptionWithCode_returns200WithCodeInJson() throws Exception {
        when(definitionService.getById(anyLong()))
                .thenThrow(new OpcException(404, "Agent 已下架"));

        mockMvc.perform(get("/opc/agent/detail/{id}", DEFINITION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("Agent 已下架"));
    }

    // ==================== POST /opc/agent/hire ====================

    @Test
    @DisplayName("hire — SecurityContextHolder 有 userId → service.hire 被调 + JSON $.data.id=99")
    void hire_authenticated_returnsInstanceSerializedInJson() throws Exception {
        when(instanceService.hire(eq(COMPANY_ID), eq(DEFINITION_ID), anyString(), any(), any()))
                .thenReturn(sampleInstance());

        mockMvc.perform(post("/opc/agent/hire")
                        .contentType("application/json")
                        .content("{\"companyId\":1001,\"definitionId\":42,\"hireType\":\"MONTHLY\",\"duration\":30,\"nickname\":\"我的财务助理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.companyId").value(COMPANY_ID))
                .andExpect(jsonPath("$.data.createBy").value(String.valueOf(USER_ID)))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        verify(instanceService).hire(COMPANY_ID, DEFINITION_ID, "MONTHLY", 30, "我的财务助理");
    }

    @Test
    @DisplayName("hire — 缺 @RequestBody → GlobalExceptionHandler 兜住 → HTTP 200 + code=500(不是 Spring 默认 400)")
    void hire_emptyBody_returns200WithCode500() throws Exception {
        // 断言曾是 isBadRequest(),但那条断言从来没被执行过 —— 本类此前整体
        // 加载不了 context(11/11 全红),没人发现它与 GlobalExceptionHandler
        // 的约定不符:@ExceptionHandler(Exception.class) → AjaxResult.error()
        // 是 HTTP 200 + body 里 code=500,不是 Spring 的 400。
        mockMvc.perform(post("/opc/agent/hire")
                        .contentType("application/json")
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("hire — 匿名调用 → SecurityUtils.getUserId() null → controller 抛 OpcException → HTTP 200 + code=500 + msg=未登录")
    void hire_anonymous_userIdNull_throwsOpcException() throws Exception {
        SecurityContextHolder.remove();

        mockMvc.perform(post("/opc/agent/hire")
                        .contentType("application/json")
                        .content("{\"companyId\":1001,\"definitionId\":42,\"hireType\":\"MONTHLY\",\"duration\":30,\"nickname\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("未登录"));
    }

    @Test
    @DisplayName("hire — service 抛 OpcException(400, '余额不足') → HTTP 200 + JSON code=400")
    void hire_serviceThrowsOpcException400_returns200WithCode400() throws Exception {
        when(instanceService.hire(anyLong(), any(), anyString(), any(), anyString()))
                .thenThrow(new OpcException(400, "余额不足，请先充值代金券"));

        mockMvc.perform(post("/opc/agent/hire")
                        .contentType("application/json")
                        .content("{\"companyId\":1001,\"definitionId\":42,\"hireType\":\"MONTHLY\",\"duration\":30,\"nickname\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("余额不足，请先充值代金券"));
    }
}