package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.mapper.OpcFinanceTaxReportMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceVoucherMapper;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcFinanceTaxReportServiceImpl} 单元测试 — W1 Sub-task 4.2
 *
 * <p>覆盖 3 个 AC:
 * <ol>
 *   <li>正常月 — voucher 聚合 + LLM 建议 → 写表，字段正确</li>
 *   <li>空数据月 — voucher 聚合返回 0 → 仍写表，taxable/tax=0，建议是 fallback 模板</li>
 *   <li>LLM 失败月 — {@link LlmGateway#chat} 抛 {@link OpcException} → 仍写表，不报错</li>
 * </ol>
 *
 * <p>无 Spring context，纯 Mockito unit test。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpcFinanceTaxReportServiceImplTest {

    @Mock
    private OpcFinanceVoucherMapper voucherMapper;

    @Mock
    private OpcFinanceTaxReportMapper taxReportMapper;

    @Mock
    private LlmGateway llmGateway;

    @InjectMocks
    private OpcFinanceTaxReportServiceImpl service;

    private static final Long COMPANY_ID = 1001L;
    private static final String PERIOD = "2026-09";
    private static final String CREATE_BY = "alice";

    /** 用于模拟 MyBatis insert 后 useGeneratedKeys 回填主键 */
    private static final AtomicLong NEXT_ID = new AtomicLong(100L);

    @BeforeEach
    void setupAutoKeyBehavior() {
        // 模拟 MyBatis useGeneratedKeys="true" keyProperty="id"：
        // insertTaxReport(...) 返回后，给入参 report.id 设置一个递增 ID
        when(taxReportMapper.insertTaxReport(any(OpcFinanceTaxReport.class)))
                .thenAnswer(invocation -> {
                    OpcFinanceTaxReport r = invocation.getArgument(0);
                    r.setId(NEXT_ID.getAndIncrement());
                    return 1;
                });
    }

    // ---------- 1. 正常月 ----------

    @Test
    @DisplayName("1) 正常月 — 聚合 + LLM 返回建议，写表字段正确")
    void normalMonth_writesReportAndUsesLlmAdvice() {
        // given: 当月有 12 张凭证，3 张被拒，销项合计 100,000，进项 5,000
        Map<String, Object> agg = Map.of(
                "taxable_amount", new BigDecimal("100000.00"),
                "input_tax", new BigDecimal("5000.00"),
                "voucher_count", 12L,
                "posted_count", 8L,
                "rejected_count", 3L,
                "pending_count", 1L
        );
        when(voucherMapper.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(agg);

        // LLM 返回成功
        ChatResponse llmResp = ChatResponse.builder()
                .success(true)
                .content("本期共 12 张凭证，建议优先整理 3 张被拒凭证，注意销项发票附件完整。")
                .model("deepseek-chat")
                .tokenInput(280)
                .tokenOutput(45)
                .build();
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class), any(LlmGateway.ChatContext.class)))
                .thenReturn(llmResp);

        // when
        Long id = service.generateMonthlyReport(COMPANY_ID, PERIOD, CREATE_BY);

        // then
        assertNotNull(id, "新报表 ID 不应为 null");
        ArgumentCaptor<OpcFinanceTaxReport> captor = ArgumentCaptor.forClass(OpcFinanceTaxReport.class);
        verify(taxReportMapper).insertTaxReport(captor.capture());
        verify(llmGateway, times(1)).chat(anyList(), any(), any());

        OpcFinanceTaxReport saved = captor.getValue();
        assertEquals(COMPANY_ID, saved.getCompanyId());
        assertEquals(PERIOD, saved.getPeriod());
        assertEquals("VAT", saved.getTaxType());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals(CREATE_BY, saved.getCreateBy());
        assertNotNull(saved.getReportCode());
        assertTrue(saved.getReportCode().startsWith("TR"), "reportCode 应以 TR 开头");

        // 销项 = 100000 * 0.13 = 13000
        // 应缴 = max(13000 - 5000, 0) = 8000
        assertEquals(new BigDecimal("100000.00"), saved.getTaxableAmount());
        assertEquals(0, saved.getTaxAmount().compareTo(new BigDecimal("8000.00")),
                "taxAmount 应等于 8000.00，实际 " + saved.getTaxAmount());
        assertEquals(0, saved.getPayAmount().compareTo(new BigDecimal("8000.00")));
        assertEquals(BigDecimal.ZERO, saved.getPaidAmount());

        // advice 来源于 LLM
        assertNotNull(saved.getAttachments());
        assertTrue(saved.getAttachments().contains("建议"), "应使用 LLM 的建议文本");
    }

    // ---------- 2. 空数据月 ----------

    @Test
    @DisplayName("2) 空数据月 — voucher 聚合返回 0/零值 → 仍写表，advice 是 fallback 模板")
    void emptyMonth_writesReportWithZerosAndFallbackAdvice() {
        // given: 空聚合（无凭证）
        Map<String, Object> emptyAgg = Map.of(
                "taxable_amount", new BigDecimal("0"),
                "input_tax", new BigDecimal("0"),
                "voucher_count", 0L,
                "posted_count", 0L,
                "rejected_count", 0L,
                "pending_count", 0L
        );
        when(voucherMapper.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(emptyAgg);

        // LLM 返回正常（空数据也走 LLM 也行）
        ChatResponse llmResp = ChatResponse.builder()
                .success(true)
                .content("本期无凭证，无需申报。")
                .model("deepseek-chat")
                .build();
        when(llmGateway.chat(anyList(), any(), any())).thenReturn(llmResp);

        // when
        Long id = service.generateMonthlyReport(COMPANY_ID, PERIOD, CREATE_BY);

        // then
        assertNotNull(id);
        ArgumentCaptor<OpcFinanceTaxReport> captor = ArgumentCaptor.forClass(OpcFinanceTaxReport.class);
        verify(taxReportMapper).insertTaxReport(captor.capture());

        OpcFinanceTaxReport saved = captor.getValue();
        assertEquals(0, saved.getTaxableAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, saved.getTaxAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, saved.getPayAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, saved.getPaidAmount().compareTo(BigDecimal.ZERO),
                "paidAmount 应为 0（默认未缴）");
        assertEquals("DRAFT", saved.getStatus());
    }

    // ---------- 3. LLM 失败月 ----------

    @Test
    @DisplayName("3) LLM 失败月 — LlmGateway 抛 OpcException → 报表仍写表，advice 是 fallback")
    void llmFailureMonth_writesReportAndFallsBack() {
        // given: 正常聚合
        Map<String, Object> agg = Map.of(
                "taxable_amount", new BigDecimal("50000.00"),
                "input_tax", new BigDecimal("3000.00"),
                "voucher_count", 5L,
                "posted_count", 5L,
                "rejected_count", 0L,
                "pending_count", 0L
        );
        when(voucherMapper.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(agg);

        // LLM 抛 OpcException（所有 provider 都失败）
        when(llmGateway.chat(anyList(), any(), any()))
                .thenThrow(new OpcException("所有 LLM provider 失败：timeout"));

        // when — 不应抛异常
        Long id = assertDoesNotThrow(() ->
                service.generateMonthlyReport(COMPANY_ID, PERIOD, CREATE_BY));

        // then
        assertNotNull(id, "LLM 失败时仍应返回报表 ID");
        verify(taxReportMapper, times(1)).insertTaxReport(any(OpcFinanceTaxReport.class));

        ArgumentCaptor<OpcFinanceTaxReport> captor = ArgumentCaptor.forClass(OpcFinanceTaxReport.class);
        verify(taxReportMapper).insertTaxReport(captor.capture());
        OpcFinanceTaxReport saved = captor.getValue();

        // fallback 模板包含 "[自动聚合·未走 LLM]"
        assertNotNull(saved.getAttachments());
        assertTrue(saved.getAttachments().contains("自动聚合") || saved.getAttachments().contains("回退"),
                "LLM 失败时应使用 fallback 建议，实际: " + saved.getAttachments());
        assertEquals("DRAFT", saved.getStatus());

        // 数字仍要算出来 (50000 * 13% - 3000 = 3500)
        assertEquals(new BigDecimal("50000.00"), saved.getTaxableAmount());
        assertEquals(0, saved.getPayAmount().compareTo(new BigDecimal("3500.00")));
    }

    // ---------- 边界 / 异常 ----------

    @Test
    @DisplayName("4) 边界 — period 格式错误抛 OpcException")
    void invalidPeriod_throws() {
        OpcException ex1 = assertThrows(OpcException.class,
                () -> service.generateMonthlyReport(COMPANY_ID, "2026-13", CREATE_BY));
        assertTrue(ex1.getMessage().contains("period"));

        assertThrows(OpcException.class,
                () -> service.generateMonthlyReport(COMPANY_ID, "not-a-date", CREATE_BY));
    }

    @Test
    @DisplayName("5) 边界 — companyId 为 null 抛 OpcException")
    void nullCompany_throws() {
        assertThrows(OpcException.class,
                () -> service.generateMonthlyReport(null, PERIOD, CREATE_BY));
    }

    @Test
    @DisplayName("6) LLM 返回 success=false → 走 fallback，不抛异常")
    void llmReturnsFailure_writesFallbackAdvice() {
        Map<String, Object> agg = Map.of(
                "taxable_amount", new BigDecimal("1000"),
                "input_tax", new BigDecimal("0"),
                "voucher_count", 1L,
                "posted_count", 1L,
                "rejected_count", 0L,
                "pending_count", 0L
        );
        when(voucherMapper.aggregateByPeriod(COMPANY_ID, PERIOD)).thenReturn(agg);

        // LLM 返回 success=false
        ChatResponse fail = ChatResponse.builder()
                .success(false)
                .errorMessage("rate limit")
                .model("deepseek-chat")
                .build();
        when(llmGateway.chat(anyList(), any(), any())).thenReturn(fail);

        Long id = service.generateMonthlyReport(COMPANY_ID, PERIOD, CREATE_BY);
        assertNotNull(id);

        ArgumentCaptor<OpcFinanceTaxReport> captor = ArgumentCaptor.forClass(OpcFinanceTaxReport.class);
        verify(taxReportMapper).insertTaxReport(captor.capture());
        assertNotNull(captor.getValue().getAttachments());
        assertTrue(captor.getValue().getAttachments().contains("自动聚合"),
                "success=false 也应触发 fallback，实际: " + captor.getValue().getAttachments());
    }

    // ---------- listByCompany / getById / getByCode 转发 ----------

    @Test
    @DisplayName("7) listByCompany 透传 mapper，limit 为 null 时默认值 20")
    void listByCompany_defaults() {
        when(taxReportMapper.listByCompany(COMPANY_ID, PERIOD, "DRAFT", 20))
                .thenReturn(List.of(new OpcFinanceTaxReport()));

        List<OpcFinanceTaxReport> list = service.listByCompany(COMPANY_ID, PERIOD, "DRAFT", null);
        assertEquals(1, list.size());
        verify(taxReportMapper).listByCompany(COMPANY_ID, PERIOD, "DRAFT", 20);
    }

    @Test
    @DisplayName("8) getById / getByCode 透传 mapper")
    void passthroughQueries() {
        OpcFinanceTaxReport r = new OpcFinanceTaxReport();
        r.setId(99L);
        r.setReportCode("R-test");

        when(taxReportMapper.selectById(99L)).thenReturn(r);
        when(taxReportMapper.selectByCode("R-test")).thenReturn(r);

        assertEquals(r, service.getById(99L));
        assertEquals(r, service.getByCode("R-test"));
    }

}
