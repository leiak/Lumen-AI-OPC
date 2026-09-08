package com.ruoyi.opc.insight.service.impl;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.domain.OpcInsightDailyReport;
import com.ruoyi.opc.insight.mapper.OpcInsightDailyReportMapper;
import com.ruoyi.opc.insight.service.IKpiService;
import com.ruoyi.opc.insight.vo.DailyReportVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DailyReportServiceImpl} 单元测试（M4 Task 8，10 个用例）。
 *
 * <p>覆盖矩阵：
 * <ol>
 *   <li>正常流程：1</li>
 *   <li>LLM 失败回退：1</li>
 *   <li>唯一键冲突（重复日期）：1</li>
 *   <li>参数校验（null companyId / 未来日期）：2</li>
 *   <li>部分降级快照：1</li>
 *   <li>companyId 来源（kpi 优先于参数）：1</li>
 *   <li>读侧方法（listByDateRange / getById × 2）：3</li>
 * </ol>
 *
 * <p><b>LlmGateway mock 说明</b>：opc-ai-core 的 {@link LlmGateway} 只有
 * {@code chat(messages, options, ctx)} 方法（无 {@code chatDailyReport}），
 * 测试用 {@code anyList() / any() / any()} 匹配。LLM 响应通过
 * {@link ChatResponse#builder()} 构造。</p>
 *
 * <p><b>ID 回填模拟</b>：{@code mapper.insert(...)} 用 {@code thenAnswer +
 * AtomicLong} 通过 {@code @Data} 生成的 setter 回填主键，模拟 MyBatis
 * {@code useGeneratedKeys="true" keyProperty="id"}（沿用 AnomalyServiceImplTest
 * 模式）。</p>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyReportServiceImplTest {

    @Mock
    private IKpiService kpiService;

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private OpcInsightDailyReportMapper mapper;

    @InjectMocks
    private DailyReportServiceImpl service;

    private static final Long COMPANY_ID = 1L;
    private static final String DATE = "2026-09-08";
    private static final LocalDate PERIOD = LocalDate.parse(DATE);

    /** 模拟 MyBatis useGeneratedKeys="true" keyProperty="id" */
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        NEXT_ID.set(1L);
        // mapper.insert 后回填 id（@Data 已生成 public setId）
        when(mapper.insert(any(OpcInsightDailyReport.class))).thenAnswer(inv -> {
            OpcInsightDailyReport d = inv.getArgument(0);
            d.setId(NEXT_ID.getAndIncrement());
            return 1;
        });
    }

    // ============================================================
    // 1. 正常流程
    // ============================================================

    @Test
    void generate_normalFlow_insertsReport() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("每日概览", "关注现金流"));

        service.generate(COMPANY_ID, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        OpcInsightDailyReport saved = captor.getValue();
        assertEquals(COMPANY_ID, saved.getCompanyId());
        assertEquals(PERIOD, saved.getPeriod());
        assertEquals("每日概览", saved.getSummaryMd());
        assertEquals("关注现金流", saved.getAdviceMd());
        assertEquals("deepseek-chat", saved.getLlmUsed());
        assertEquals("system", saved.getCreateBy());
        assertEquals("system", saved.getUpdateBy());
        assertNotNull(saved.getCreateTime());
        // kpi_json 必须是有效 JSON 字符串
        assertNotNull(saved.getKpiJson());
        assertTrue(saved.getKpiJson().startsWith("{") && saved.getKpiJson().contains("companyId"));
    }

    // ============================================================
    // 2. LLM 失败 → fallback 模板
    // ============================================================

    @Test
    void generate_llmFails_usesFallbackTemplate() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenThrow(new RuntimeException("LLM down"));

        service.generate(COMPANY_ID, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        OpcInsightDailyReport saved = captor.getValue();
        assertTrue(saved.getSummaryMd().startsWith("[自动聚合·未走 LLM]"),
                "fallback summary 前缀应为 [自动聚合·未走 LLM], 实际：" + saved.getSummaryMd());
        assertEquals("FALLBACK", saved.getLlmUsed());
        assertEquals("请检查数据完整性后重试，或联系平台支持。", saved.getAdviceMd());
    }

    @Test
    void generate_llmReturnsEmptyContent_fallsBack() {
        // 模拟 LLM 调用成功但 content 为空 —— 也应走 fallback
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(ChatResponse.builder().success(true).content("").model("m").build());

        service.generate(COMPANY_ID, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        assertEquals("FALLBACK", captor.getValue().getLlmUsed());
        assertTrue(captor.getValue().getSummaryMd().startsWith("[自动聚合·未走 LLM]"));
    }

    // ============================================================
    // 3. 唯一键冲突（重复日期）
    // ============================================================

    @Test
    void generate_duplicateDate_throwsDataIntegrityViolation() {
        when(kpiService.snapshot(any(), anyString())).thenReturn(baseKpi());
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("S", "A"));
        // 覆盖 @BeforeEach 的 stub，让 mapper.insert 抛 UNIQUE 冲突
        doThrow(new DataIntegrityViolationException("uk_company_period"))
                .when(mapper).insert(any(OpcInsightDailyReport.class));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.generate(COMPANY_ID, DATE));
    }

    // ============================================================
    // 4. 参数校验
    // ============================================================

    @Test
    void generate_companyIdNull_throwsOpcException() {
        OpcException ex = assertThrows(OpcException.class,
                () -> service.generate(null, DATE));
        assertEquals("companyId 不能为空", ex.getMessage());
        // 关键断言：参数校验在调用 LLM / kpiService / mapper 之前
        verifyNoInteractions(kpiService);
        verifyNoInteractions(llmGateway);
        verifyNoInteractions(mapper);
    }

    @Test
    void generate_periodFuture_throwsOpcException() {
        OpcException ex = assertThrows(OpcException.class,
                () -> service.generate(COMPANY_ID, "2099-01-01"));
        assertTrue(ex.getMessage().contains("period 不能晚于今天"),
                "expected msg to contain 'period 不能晚于今天', actual: " + ex.getMessage());
        verifyNoInteractions(kpiService);
        verifyNoInteractions(llmGateway);
        verifyNoInteractions(mapper);
    }

    @Test
    void generate_dateBlank_throwsOpcException() {
        assertThrows(OpcException.class,
                () -> service.generate(COMPANY_ID, ""));
        verifyNoInteractions(kpiService);
        verifyNoInteractions(llmGateway);
        verifyNoInteractions(mapper);
    }

    @Test
    void generate_dateInvalidFormat_throwsOpcException() {
        assertThrows(OpcException.class,
                () -> service.generate(COMPANY_ID, "2026/09/08"));
        verifyNoInteractions(kpiService);
        verifyNoInteractions(llmGateway);
        verifyNoInteractions(mapper);
    }

    // ============================================================
    // 5. 部分降级快照：summary 追加 [数据降级] 标记
    // ============================================================

    @Test
    void generate_kpiSnapshotEmpty_stillInsertsReport() {
        KpiSnapshot partial = KpiSnapshot.builder()
                .companyId(COMPANY_ID)
                .period(DATE)
                .partial(true)                  // 至少 1 个数据源降级
                .totalRevenue(new BigDecimal("100"))
                .build();
        when(kpiService.snapshot(any(), anyString())).thenReturn(partial);
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("部分数据", "建议人工核查"));

        service.generate(COMPANY_ID, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        OpcInsightDailyReport saved = captor.getValue();
        assertTrue(saved.getSummaryMd().contains("[数据降级]"),
                "partial 快照的 summary 应包含 [数据降级] 标记, 实际：" + saved.getSummaryMd());
    }

    // ============================================================
    // 6. companyId 来源：KPI 优先于入参
    // ============================================================

    @Test
    void generate_usesCompanyIdFromKpiNotParameter() {
        // KPI 返回 42L，参数传 1L —— 应以 KPI 为准
        KpiSnapshot kpi = KpiSnapshot.builder()
                .companyId(42L)
                .period(DATE)
                .partial(false)
                .build();
        when(kpiService.snapshot(any(), anyString())).thenReturn(kpi);
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("S", "A"));

        service.generate(1L, DATE);

        ArgumentCaptor<OpcInsightDailyReport> captor = ArgumentCaptor.forClass(OpcInsightDailyReport.class);
        verify(mapper, times(1)).insert(captor.capture());
        assertEquals(42L, captor.getValue().getCompanyId(),
                "KPI 的 companyId 应优先于入参");
    }

    // ============================================================
    // 7. 读侧：listByDateRange
    // ============================================================

    @Test
    void listByDateRange_passesLimitToMapper() {
        when(mapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class), eq(10)))
                .thenReturn(List.of(
                        DailyReportVo.builder().id(1L).companyId(COMPANY_ID).period(DATE).build(),
                        DailyReportVo.builder().id(2L).companyId(COMPANY_ID).period("2026-09-07").build()));

        List<DailyReportVo> result = service.listByDateRange(
                COMPANY_ID,
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"),
                10);

        verify(mapper, times(1)).selectByCompanyAndDateRange(
                COMPANY_ID,
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"),
                10);
        assertEquals(2, result.size());
    }

    // ============================================================
    // 8. 读侧：getById
    // ============================================================

    @Test
    void getById_returnsReport() {
        DailyReportVo vo = DailyReportVo.builder()
                .id(1L).companyId(COMPANY_ID).period(DATE)
                .summaryMd("S").adviceMd("A").llmUsed("deepseek-chat")
                .build();
        when(mapper.selectById(1L)).thenReturn(vo);

        DailyReportVo result = service.getById(1L);

        assertEquals(1L, result.getId());
        assertEquals(COMPANY_ID, result.getCompanyId());
        assertEquals(DATE, result.getPeriod());
        assertEquals("S", result.getSummaryMd());
    }

    @Test
    void getById_notFound_throwsOpcException() {
        when(mapper.selectById(999L)).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> service.getById(999L));
        assertTrue(ex.getMessage().contains("日报不存在"),
                "expected msg to contain '日报不存在', actual: " + ex.getMessage());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /** 默认 healthy snapshot（partial=false，所有 KPI 字段填值） */
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
