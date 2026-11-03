package com.ruoyi.opc.insight.service.impl;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.controller.InsightInternalController;
import com.ruoyi.opc.insight.domain.OpcInsightDailyReport;
import com.ruoyi.opc.insight.mapper.OpcInsightDailyReportMapper;
import com.ruoyi.opc.insight.service.IDailyReportService;
import com.ruoyi.opc.insight.service.IKpiService;
import com.ruoyi.opc.insight.vo.DailyReportUpsertRequest;
import com.ruoyi.opc.insight.vo.DailyReportVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DailyReportServiceImpl} 的「工作流写入路径」与「来源可分辨」测试。
 *
 * <p><b>这张表有三个写入方</b>（手工生成 / 工作流 / 种子），都往同一个
 * {@code (company_id, period)} 上写，互相覆盖 —— 谁写的根本分不清。
 * 本测试类把「谁写的」「写到哪一步」「能不能反查回那次运行」钉成可断言的事实。</p>
 *
 * <p>覆盖矩阵：
 * <ol>
 *   <li>工作流写入（upsertFromWorkflow_*）</li>
 *   <li>手工生成拒绝覆盖数字员工的行（generate_manualRefusesToOverwriteAgentRow）</li>
 *   <li>手工生成覆盖手工行 / 落库来源与状态（generate_manualOverwritesManualRow /
 *       generate_writesManualSource / generate_llmFails_writesFallbackStatus /
 *       generate_kpiPartial_llmOk_writesPartialStatus）</li>
 *   <li>业务时区（businessToday_*，R-87）</li>
 * </ol>
 *
 * <p><b>ID 回填模拟</b>：{@code mapper.insert(...)} 用 {@code thenAnswer + AtomicLong}
 * 通过 {@code @Data} 生成的 setter 回填主键，模拟 MyBatis
 * {@code useGeneratedKeys="true" keyProperty="id"}（沿用 {@code DailyReportServiceImplTest} 模式）。</p>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyReportUpsertTest {

    @Mock
    private IKpiService kpiService;

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private OpcInsightDailyReportMapper mapper;

    @Mock
    private IDailyReportService dailyReportService;

    @InjectMocks
    private DailyReportServiceImpl service;

    private static final Long COMPANY_ID = 2L;
    private static final String DATE = "2026-09-08";
    private static final LocalDate PERIOD = LocalDate.parse(DATE);

    /** 模拟 MyBatis useGeneratedKeys="true" keyProperty="id" */
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        NEXT_ID.set(1L);
        when(mapper.insert(any(OpcInsightDailyReport.class))).thenAnswer(inv -> {
            OpcInsightDailyReport d = inv.getArgument(0);
            d.setId(NEXT_ID.getAndIncrement());
            return 1;
        });
    }

    // ============================================================
    // 1. 工作流写入路径
    // ============================================================

    @Test
    @DisplayName("upsertFromWorkflow — 落库行带 AGENT_WORKFLOW 来源、FULL 状态和运行记录 id")
    void upsertFromWorkflow_insertsWithAgentSource() {
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setCompanyId(COMPANY_ID);
        req.setSourceWorkflowRunId(42L);
        req.setReportStatus("FULL");

        Long id = service.upsertFromWorkflow(req);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        OpcInsightDailyReport saved = captor.getValue();
        assertEquals("AGENT_WORKFLOW", saved.getReportSource(),
                "工作流写入的行来源必须写死为 AGENT_WORKFLOW —— 不由调用方声明");
        assertEquals("FULL", saved.getReportStatus());
        assertEquals(42L, saved.getSourceWorkflowRunId(),
                "日报必须能反查回产出它的那次工作流运行");
        assertEquals(COMPANY_ID, saved.getCompanyId());
        assertEquals(PERIOD, saved.getPeriod());
        assertNotNull(id, "upsertFromWorkflow 必须返回落库后的行 id");
    }

    @Test
    @DisplayName("upsertFromWorkflow — kpiJson 原样落库（引擎写进来的结构化数字不被改写）")
    void upsertFromWorkflow_keepsKpiJsonAsGiven() {
        String raw = "{\"revenue\":123.45,\"nested\":{\"a\":1},\"list\":[1,2,3]}";
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setKpiJson(raw);

        service.upsertFromWorkflow(req);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        assertEquals(raw, captor.getValue().getKpiJson(),
                "kpiJson 必须原样落库，不许重新序列化");
    }

    @Test
    @DisplayName("upsertFromWorkflow — companyId 为空 → OpcException，且不碰 mapper")
    void upsertFromWorkflow_companyIdNull_throws() {
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setCompanyId(null);

        assertThrows(OpcException.class, () -> service.upsertFromWorkflow(req));
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("upsertFromWorkflow — period 格式非法 → OpcException")
    void upsertFromWorkflow_periodInvalid_throws() {
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setPeriod("2026/09/08");

        assertThrows(OpcException.class, () -> service.upsertFromWorkflow(req));
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("upsertFromWorkflow — period 晚于今天 → OpcException")
    void upsertFromWorkflow_periodAfterToday_throws() {
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setPeriod("2099-01-01");

        assertThrows(OpcException.class, () -> service.upsertFromWorkflow(req));
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("upsertFromWorkflow — 已有同 (company_id, period) → 先删旧再插新（snapshot 语义）")
    void upsertFromWorkflow_existingRow_replacesIt() {
        DailyReportVo exist = DailyReportVo.builder()
                .id(7L).companyId(COMPANY_ID).period(DATE)
                .reportSource("MANUAL").build();
        when(mapper.selectByCompanyAndDate(COMPANY_ID, PERIOD)).thenReturn(exist);

        Long id = service.upsertFromWorkflow(baseUpsertRequest());

        verify(mapper, times(1)).deleteByPrimaryKey(7L);
        verify(mapper, times(1)).insert(any(OpcInsightDailyReport.class));
        assertNotNull(id);
    }

    @Test
    @DisplayName("upsertFromWorkflow — reportStatus 不在 FULL/PARTIAL/FALLBACK 里 → OpcException，且不碰 mapper")
    void upsertFromWorkflow_reportStatusNotInSet_throws() {
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setReportStatus("WEIRD");

        assertThrows(OpcException.class, () -> service.upsertFromWorkflow(req));
        verifyNoInteractions(mapper);
    }

    // ============================================================
    // 2. 手工生成撞上数字员工的行 → 拒绝覆盖
    // ============================================================

    @Test
    @DisplayName("generate — 库里已有 AGENT_WORKFLOW 的同日行 → 拒绝覆盖，且不调 LLM、不插库")
    void generate_manualRefusesToOverwriteAgentRow() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        DailyReportVo agentRow = DailyReportVo.builder()
                .id(7L).companyId(COMPANY_ID).period(DATE)
                .reportSource("AGENT_WORKFLOW").build();
        when(mapper.selectByCompanyAndDate(COMPANY_ID, PERIOD)).thenReturn(agentRow);

        OpcException ex = assertThrows(OpcException.class, () -> service.generate(COMPANY_ID, DATE));

        assertTrue(ex.getMessage().contains("不支持手工覆盖"),
                "expected msg to contain '不支持手工覆盖', actual: " + ex.getMessage());
        // 拒绝覆盖是确定要失败的请求 → 不该先花一次 LLM 调用
        verifyNoInteractions(llmGateway);
        verify(mapper, never()).insert(any(OpcInsightDailyReport.class));
    }

    @Test
    @DisplayName("generate — 库里已有 MANUAL 的同日行 → 正常覆盖")
    void generate_manualOverwritesManualRow() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("每日概览", "关注现金流"));
        DailyReportVo manualRow = DailyReportVo.builder()
                .id(7L).companyId(COMPANY_ID).period(DATE)
                .reportSource("MANUAL").build();
        when(mapper.selectByCompanyAndDate(COMPANY_ID, PERIOD)).thenReturn(manualRow);

        service.generate(COMPANY_ID, DATE);

        verify(mapper, times(1)).deleteByPrimaryKey(7L);
        verify(mapper, times(1)).insert(any(OpcInsightDailyReport.class));
    }

    // ============================================================
    // 3. 手工生成落库的来源与状态
    // ============================================================

    @Test
    @DisplayName("generate — 落库行 reportSource=MANUAL，LLM 成功时 reportStatus=FULL")
    void generate_writesManualSource() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("每日概览", "关注现金流"));

        service.generate(COMPANY_ID, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        OpcInsightDailyReport saved = captor.getValue();
        assertEquals("MANUAL", saved.getReportSource());
        assertEquals("FULL", saved.getReportStatus());
        assertEquals("deepseek-chat", saved.getLlmUsed());
    }

    @Test
    @DisplayName("generate — LLM 抛异常 → reportStatus=FALLBACK、llmUsed=FALLBACK")
    void generate_llmFails_writesFallbackStatus() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenThrow(new RuntimeException("LLM down"));

        service.generate(COMPANY_ID, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        OpcInsightDailyReport saved = captor.getValue();
        assertEquals("FALLBACK", saved.getReportStatus());
        assertEquals("FALLBACK", saved.getLlmUsed());
    }

    @Test
    @DisplayName("generate — KPI 降级但 LLM 成功 → reportStatus=PARTIAL")
    void generate_kpiPartial_llmOk_writesPartialStatus() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(partialKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("部分数据", "建议人工核查"));

        service.generate(COMPANY_ID, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        OpcInsightDailyReport saved = captor.getValue();
        assertEquals("PARTIAL", saved.getReportStatus(),
                "KPI 降级 + LLM 成功 → PARTIAL（不是 FULL）");
        assertTrue(!"FALLBACK".equals(saved.getLlmUsed()),
                "LLM 成功时 llmUsed 不应是 FALLBACK, 实际: " + saved.getLlmUsed());
    }

    // ============================================================
    // 4. 业务时区（R-87）—— 容器 JVM 是 UTC，用 systemDefault() 会差一天
    // ============================================================

    @Test
    @DisplayName("businessToday — 固定时钟下按 Asia/Shanghai 判定「今天」")
    void businessToday_fixedClock_returnsShanghaiDate() {
        // 2026-11-02T16:30:00Z → UTC 是 11-02，北京是 11-03
        Clock fixed = Clock.fixed(Instant.parse("2026-11-02T16:30:00Z"), ZoneId.of("UTC"));
        assertEquals(LocalDate.parse("2026-11-03"), DailyReportServiceImpl.businessToday(fixed),
                "业务「今天」必须按 Asia/Shanghai 算，不是按容器 JVM 的 UTC");
    }

    @Test
    @DisplayName("businessToday — 业务时区常量钉死在 Asia/Shanghai")
    void businessToday_businessZoneIsAsiaShanghai() {
        assertEquals("Asia/Shanghai", DailyReportServiceImpl.BUSINESS_ZONE.getId());
    }

    // ============================================================
    // 5. 其余护栏（R-85 / R-86 / R-88 / R-90 / R-94）
    // ============================================================

    @Test
    @DisplayName("upsertFromWorkflow — reportStatus 为 null → OpcException（必填，不许默默存别的值）")
    void upsertFromWorkflow_reportStatusNull_throws() {
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setReportStatus(null);

        assertThrows(OpcException.class, () -> service.upsertFromWorkflow(req));
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("upsertFromWorkflow — kpiJson 不是合法 JSON → OpcException（kpi_json 是 MySQL JSON 列）")
    void upsertFromWorkflow_kpiJsonInvalid_throws() {
        DailyReportUpsertRequest req = baseUpsertRequest();
        req.setKpiJson("{not json");

        assertThrows(OpcException.class, () -> service.upsertFromWorkflow(req));
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("R-85 — 请求 DTO 里没有 source 字段（谁写的由端点身份决定，不由调用方声明）")
    void upsertRequest_hasNoSourceField() {
        for (java.lang.reflect.Field f : DailyReportUpsertRequest.class.getDeclaredFields()) {
            assertTrue(!"source".equals(f.getName()),
                    "DailyReportUpsertRequest 不许有 source 字段 —— 「谁写的这行」由端点身份决定，"
                            + "让调用方声明等于把这个问题交回给调用方");
        }
    }

    @Test
    @DisplayName("R-88 — @InnerAuth 标在方法上，不标在类上（CompanyIdBodyAdvice 只认方法级注解）")
    void internalEndpoint_innerAuthIsOnMethodNotOnClass() throws Exception {
        java.lang.reflect.Method m = InsightInternalController.class
                .getMethod("upsert", DailyReportUpsertRequest.class);
        assertNotNull(m.getAnnotation(InnerAuth.class),
                "upsert 方法必须标注 @InnerAuth —— 它是只给服务间调用打的内部端点");
        assertNull(InsightInternalController.class.getAnnotation(InnerAuth.class),
                "@InnerAuth 绝不能标在类上：CompanyIdBodyAdvice#supports 只查方法级注解，"
                        + "标在类上 → body 里的 companyId 会被租户守卫改写");
    }

    @Test
    @DisplayName("R-94 — 端点是 POST /opc/insight/internal/daily/upsert，返回 R<Long>")
    void internalEndpoint_pathAndReturnType() throws Exception {
        RequestMapping classMapping = InsightInternalController.class.getAnnotation(RequestMapping.class);
        assertNotNull(classMapping);
        assertEquals("/opc/insight/internal", classMapping.value()[0]);

        java.lang.reflect.Method m = InsightInternalController.class
                .getMethod("upsert", DailyReportUpsertRequest.class);
        PostMapping post = m.getAnnotation(PostMapping.class);
        assertNotNull(post, "upsert 必须是 POST");
        assertEquals("/daily/upsert", post.value()[0]);
        // Feign 侧声明 R<Long>：R<Map<String,Object>> 拿到数字会抛 MismatchedInputException
        assertEquals(R.class, m.getReturnType(), "端点必须返回 R<T>");
        java.lang.reflect.ParameterizedType generic =
                (java.lang.reflect.ParameterizedType) m.getGenericReturnType();
        assertEquals(Long.class, generic.getActualTypeArguments()[0],
                "端点必须返回 R<Long> —— 泛型写成 R<Map<String,Object>> 时 Feign 侧解析数字会炸");
    }

    @Test
    @DisplayName("内部端点 — 成功时 body 是 R 的形状：code=200，data 是落库后的行 id（数字）")
    void internalEndpoint_success_returnsNumericData() throws Exception {
        when(dailyReportService.upsertFromWorkflow(any(DailyReportUpsertRequest.class))).thenReturn(123L);
        MockMvc mvc = standaloneInternalController();

        mvc.perform(post("/opc/insight/internal/daily/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":2,\"period\":\"2026-09-08\",\"reportStatus\":\"FULL\","
                                + "\"sourceWorkflowRunId\":42,\"kpiJson\":\"{\\\"a\\\":1}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(123));
    }

    @Test
    @DisplayName("内部端点 — 业务异常被 controller 级 @ExceptionHandler 收成 R<Void>，错误码 400 保留")
    void internalEndpoint_opcException_returnsBusinessCode() throws Exception {
        when(dailyReportService.upsertFromWorkflow(any(DailyReportUpsertRequest.class)))
                .thenThrow(new OpcException(400, "period 格式非法，应为 yyyy-MM-dd: 2026/09/08"));
        MockMvc mvc = standaloneInternalController();

        mvc.perform(post("/opc/insight/internal/daily/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":2,\"period\":\"2026/09/08\",\"reportStatus\":\"FULL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("period 格式非法，应为 yyyy-MM-dd: 2026/09/08"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 只挂 {@link InsightInternalController} 的 standalone MockMvc ——
     * 不挂 GlobalExceptionHandler，让 controller 级 {@code @ExceptionHandler} 生效，
     * 这正是 R-94 要验的「响应体类型是 R&lt;T&gt;，不是 AjaxResult」。
     */
    private MockMvc standaloneInternalController() {
        return MockMvcBuilders.standaloneSetup(new InsightInternalController(dailyReportService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    /** 默认 healthy snapshot（partial=false） */
    private KpiSnapshot baseKpi() {
        return KpiSnapshot.builder()
                .companyId(COMPANY_ID)
                .period(DATE)
                .totalRevenue(new BigDecimal("10000"))
                .totalExpense(new BigDecimal("8000"))
                .voucherCount(5L)
                .pendingVoucherCount(2L)
                .walletBalance(new BigDecimal("500"))
                .tokenUsage(1000L)
                .companyActiveDays(30L)
                .partial(false)
                .build();
    }

    /** 降级快照（partial=true —— 至少一个数据源降级） */    private KpiSnapshot partialKpi() {
        return KpiSnapshot.builder()
                .companyId(COMPANY_ID)
                .period(DATE)
                .totalRevenue(new BigDecimal("10000"))
                .partial(true)
                .build();
    }

    /** 一个合法的 upsert 请求（各用例只改自己关心的字段） */
    private DailyReportUpsertRequest baseUpsertRequest() {
        DailyReportUpsertRequest req = new DailyReportUpsertRequest();
        req.setCompanyId(COMPANY_ID);
        req.setPeriod(DATE);
        req.setSummaryMd("摘要");
        req.setAdviceMd("建议");
        req.setKpiJson("{\"revenue\":1}");
        req.setLlmUsed("deepseek-chat");
        req.setReportStatus("FULL");
        req.setSourceWorkflowRunId(42L);
        return req;
    }

    /** 构造一个 LLM 成功响应（content 严格 JSON {summary, advice}） */
    private static ChatResponse llmSuccessResp(String summary, String advice) {
        String json = "{\"summary\":\"" + summary + "\",\"advice\":\"" + advice + "\"}";
        return ChatResponse.builder()
                .success(true)
                .model("deepseek-chat")
                .content(json)
                .build();
    }
}
