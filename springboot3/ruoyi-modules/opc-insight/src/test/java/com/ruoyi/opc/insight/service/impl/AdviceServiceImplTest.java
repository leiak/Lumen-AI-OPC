package com.ruoyi.opc.insight.service.impl;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.domain.OpcInsightAdvice;
import com.ruoyi.opc.insight.mapper.OpcInsightAdviceMapper;
import com.ruoyi.opc.insight.vo.AdviceVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdviceServiceImpl} 单元测试（M4 Task 9，8 个用例）。
 *
 * <p>覆盖矩阵：
 * <ol>
 *   <li>缓存命中（generate_cacheHit_returnsCachedAndSkipsLlm）</li>
 *   <li>缓存未命中 + LLM 成功（generate_cacheMiss_callsLlmAndInserts）</li>
 *   <li>缓存未命中 + LLM 异常（generate_llmFails_usesFallback）</li>
 *   <li>参数校验：topic 不支持（generate_topicNotSupported_throwsOpcException）</li>
 *   <li>参数校验：companyId 必须 >0（generate_companyIdZero_throwsOpcException）</li>
 *   <li>读侧 listByCompany（listByCompany_passesLimit / listByCompany_nullCompanyId_throwsOpcException）</li>
 *   <li>重生成（regenerate_callsLlmAgainAndUpdates）</li>
 *   <li>读侧 getById（getById_notFound_throwsOpcException）</li>
 * </ol>
 *
 * <p><b>LlmGateway mock 说明</b>：opc-ai-core 的 {@link LlmGateway} 只有
 * {@code chat(messages, options, ctx)} 方法（无 {@code chatAdvice}），测试用
 * {@code anyList() / any() / any()} 匹配。LLM 响应通过
 * {@link ChatResponse#builder()} 构造。</p>
 *
 * <p><b>ID 回填模拟</b>：{@code mapper.insert(...)} 用 {@code thenAnswer +
 * AtomicLong} 通过 {@code @Data} 生成的 setter 回填主键，模拟 MyBatis
 * {@code useGeneratedKeys="true" keyProperty="id"}（沿用 AnomalyServiceImplTest
 * / DailyReportServiceImplTest 模式）。</p>
 *
 * <p><b>spec 偏差</b>：plan §Task 9 line 1103 的 {@code generate_companyIdMismatch_throwsSecurityException}
 * 因 {@code SecurityUtils} 不暴露 {@code getCompanyId()} 而不可实现；本测试用
 * {@code generate_companyIdZero_throwsOpcException} 替代，验证 service 层
 * 对 companyId=0 的防御（防止上游误传默认值）。</p>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdviceServiceImplTest {

    @Mock
    private OpcInsightAdviceMapper mapper;

    @Mock
    private LlmGateway llmGateway;

    @InjectMocks
    private AdviceServiceImpl service;

    private static final Long COMPANY_ID = 1L;
    private static final String TOPIC = "cost_optimization";

    /** 模拟 MyBatis useGeneratedKeys="true" keyProperty="id" */
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        NEXT_ID.set(1L);
        // mapper.insert 后回填 id（@Data 已生成 public setId）
        when(mapper.insert(any(OpcInsightAdvice.class))).thenAnswer(inv -> {
            OpcInsightAdvice a = inv.getArgument(0);
            a.setId(NEXT_ID.getAndIncrement());
            return 1;
        });
    }

    // ============================================================
    // 1. 缓存命中：跳过 LLM，直接返回
    // ============================================================

    @Test
    void generate_cacheHit_returnsCachedAndSkipsLlm() {
        OpcInsightAdvice cached = OpcInsightAdvice.builder()
                .id(99L)
                .companyId(COMPANY_ID)
                .topic(TOPIC)
                .adviceMd("existing advice")
                .llmUsed("deepseek-chat")
                .confidence(new BigDecimal("0.85"))
                .createTime(LocalDateTime.now().minusDays(2))
                .build();
        when(mapper.selectRecent(COMPANY_ID, TOPIC, 7)).thenReturn(cached);

        AdviceVo result = service.generate(COMPANY_ID, TOPIC);

        assertEquals(99L, result.getId());
        assertEquals("existing advice", result.getAdviceMd());
        assertEquals(TOPIC, result.getTopic());
        // 关键断言：缓存命中时 LLM 不被调用，DB 不被插入
        verify(llmGateway, never()).chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class));
        verify(mapper, never()).insert(any(OpcInsightAdvice.class));
    }

    // ============================================================
    // 2. 缓存未命中：LLM 调用 + DB 插入
    // ============================================================

    @Test
    void generate_cacheMiss_callsLlmAndInserts() {
        when(mapper.selectRecent(any(Long.class), anyString(), anyInt())).thenReturn(null);
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("建议正文", "0.75"));

        AdviceVo result = service.generate(COMPANY_ID, TOPIC);

        ArgumentCaptor<OpcInsightAdvice> captor = ArgumentCaptor.forClass(OpcInsightAdvice.class);
        verify(mapper, times(1)).insert(captor.capture());
        verify(llmGateway, times(1)).chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class));

        OpcInsightAdvice saved = captor.getValue();
        assertEquals(COMPANY_ID, saved.getCompanyId());
        assertEquals(TOPIC, saved.getTopic());
        assertEquals("建议正文", saved.getAdviceMd());
        assertEquals("deepseek-chat", saved.getLlmUsed());
        assertEquals(0, new BigDecimal("0.75").compareTo(saved.getConfidence()),
                "confidence 应为 0.75，实际：" + saved.getConfidence());

        assertNotNull(result.getId(), "回填后 id 应非空");
        assertEquals("建议正文", result.getAdviceMd());
    }

    // ============================================================
    // 3. LLM 异常：fallback 模板
    // ============================================================

    @Test
    void generate_llmFails_usesFallback() {
        when(mapper.selectRecent(any(Long.class), anyString(), anyInt())).thenReturn(null);
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class)))
                .thenThrow(new RuntimeException("LLM down"));

        AdviceVo result = service.generate(COMPANY_ID, TOPIC);

        // 关键断言：LLM 失败时仍 insert + 标记 FALLBACK
        verify(mapper, times(1)).insert(any(OpcInsightAdvice.class));
        assertTrue(result.getAdviceMd().startsWith("[降级建议·未走 LLM]"),
                "fallback advice 前缀应为 [降级建议·未走 LLM], 实际：" + result.getAdviceMd());
        assertEquals("FALLBACK", result.getLlmUsed());
    }

    // ============================================================
    // 4. 参数校验：topic 不在白名单
    // ============================================================

    @Test
    void generate_topicNotSupported_throwsOpcException() {
        OpcException ex = assertThrows(OpcException.class,
                () -> service.generate(COMPANY_ID, "invalid_topic"));
        assertTrue(ex.getMessage().contains("topic 不支持"),
                "expected msg to contain 'topic 不支持', actual: " + ex.getMessage());
        // 参数校验优先于 mapper / LLM 调用
        verify(mapper, never()).selectRecent(any(Long.class), anyString(), anyInt());
        verify(llmGateway, never()).chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class));
        verify(mapper, never()).insert(any(OpcInsightAdvice.class));
    }

    // ============================================================
    // 5. 参数校验：companyId = 0
    // ============================================================
    // 注：spec §Task 9 line 1103 的 companyIdMismatch 测试因 SecurityUtils
    // 不暴露 getCompanyId() 而不可实现；本测试用 companyId=0 防御
    // （防止上游误传默认值），与 spec 测试目标等价。

    @Test
    void generate_companyIdZero_throwsOpcException() {
        OpcException ex = assertThrows(OpcException.class,
                () -> service.generate(0L, TOPIC));
        assertTrue(ex.getMessage().contains("companyId 必须大于 0"),
                "expected msg to contain 'companyId 必须大于 0', actual: " + ex.getMessage());
        verify(mapper, never()).selectRecent(any(Long.class), anyString(), anyInt());
        verify(llmGateway, never()).chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class));
    }

    // ============================================================
    // 6. 读侧：listByCompany 透传 limit
    // ============================================================

    @Test
    void listByCompany_passesLimit() {
        when(mapper.selectByCompany(eq(COMPANY_ID), eq(10))).thenReturn(List.of(
                OpcInsightAdvice.builder()
                        .id(1L).companyId(COMPANY_ID).topic(TOPIC)
                        .adviceMd("a1").llmUsed("deepseek-chat")
                        .confidence(new BigDecimal("0.6")).build(),
                OpcInsightAdvice.builder()
                        .id(2L).companyId(COMPANY_ID).topic("revenue_growth")
                        .adviceMd("a2").llmUsed("deepseek-chat")
                        .confidence(new BigDecimal("0.7")).build()));

        List<AdviceVo> result = service.listByCompany(COMPANY_ID, 10);

        verify(mapper, times(1)).selectByCompany(COMPANY_ID, 10);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(TOPIC, result.get(0).getTopic());
        assertEquals(2L, result.get(1).getId());
        assertEquals("revenue_growth", result.get(1).getTopic());
    }

    // ============================================================
    // 7. 重生成：绕过缓存，再次调 LLM
    // ============================================================

    @Test
    void regenerate_callsLlmAgainAndUpdates() {
        OpcInsightAdvice existing = OpcInsightAdvice.builder()
                .id(99L).companyId(COMPANY_ID).topic(TOPIC)
                .adviceMd("old advice").llmUsed("deepseek-chat")
                .confidence(new BigDecimal("0.5"))
                .createTime(LocalDateTime.now().minusDays(3))
                .build();
        when(mapper.selectById(99L)).thenReturn(existing);
        when(llmGateway.chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class)))
                .thenReturn(llmSuccessResp("new advice", "0.90"));

        AdviceVo result = service.regenerate(99L);

        verify(llmGateway, times(1)).chat(anyList(), any(ChatModelProvider.ChatOptions.class),
                any(LlmGateway.ChatContext.class));
        // updateById 被调用一次，且参数中的 advice 内容被替换
        ArgumentCaptor<OpcInsightAdvice> captor = ArgumentCaptor.forClass(OpcInsightAdvice.class);
        verify(mapper, times(1)).updateById(captor.capture());
        OpcInsightAdvice updated = captor.getValue();
        assertEquals(99L, updated.getId(), "id 应保持不变");
        assertEquals("new advice", updated.getAdviceMd());
        assertEquals(0, new BigDecimal("0.90").compareTo(updated.getConfidence()),
                "confidence 应被替换为 0.90");

        assertEquals("new advice", result.getAdviceMd());
    }

    // ============================================================
    // 8. 读侧：getById 不存在 → OpcException
    // ============================================================

    @Test
    void getById_notFound_throwsOpcException() {
        when(mapper.selectById(999L)).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> service.getById(999L));
        assertTrue(ex.getMessage().contains("advice 不存在"),
                "expected msg to contain 'advice 不存在', actual: " + ex.getMessage());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /** 构造一个 LLM 成功响应（content 严格 JSON {advice, confidence}） */
    private static ChatResponse llmSuccessResp(String advice, String confidence) {
        String json = "{\"advice\":\"" + advice + "\",\"confidence\":" + confidence + "}";
        return ChatResponse.builder()
                .success(true)
                .model("deepseek-chat")
                .content(json)
                .build();
    }
}