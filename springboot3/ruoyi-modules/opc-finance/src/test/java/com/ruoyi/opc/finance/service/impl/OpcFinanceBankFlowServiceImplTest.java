package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.mapper.OpcFinanceBankFlowMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link OpcFinanceBankFlowServiceImpl} 单元测试 — W2.4
 *
 * <p>覆盖 4 个公共方法：
 * <ul>
 *   <li>{@code uploadBatch} — null / 空集合 / 正常多 flow（每个 flow 都初始化 4 字段） / insertBatch 透传</li>
 *   <li>{@code listPending} — limit=null 默认 20 / 透传 / extracted 写死 0</li>
 *   <li>{@code getById} — 透传 / null</li>
 *   <li>{@code markExtracted} — 5 字段写入（extracted=1 + voucherId + status=EXTRACTED + updateBy）/
 *       影响 0 行 / voucherId=null 走 XML {@code <if>} 跳过</li>
 * </ul>
 *
 * <p>service 通过 W2.4 从 {@code OpcFinanceController.uploadFlows} 抽出来。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcFinanceBankFlowServiceImplTest {

    @Mock
    private OpcFinanceBankFlowMapper flowMapper;

    @InjectMocks
    private OpcFinanceBankFlowServiceImpl service;

    private static final Long COMPANY_ID = 1001L;
    private static final Long FLOW_ID = 7L;
    private static final String OPERATOR = "alice";

    private OpcFinanceBankFlow sampleFlow() {
        OpcFinanceBankFlow f = new OpcFinanceBankFlow();
        f.setId(FLOW_ID);
        f.setCompanyId(COMPANY_ID);
        f.setBankAccount("6225880101234567");
        f.setBankName("招商银行");
        f.setTradeTime(new Date());
        f.setDirection("IN");
        f.setAmount(new BigDecimal("1000.00"));
        f.setCurrency("CNY");
        f.setCounterParty("ACME Corp");
        f.setMemo("货款");
        f.setStatus("IMPORTED");
        f.setCreateBy(OPERATOR);
        return f;
    }

    @BeforeEach
    void setup() {
        // 默认 mapper.insertBatch 返回 list 大小；测试可显式 stub 覆盖
        // （mockito default 0，所以需要默认 stub 才能让正常路径返回合理值）
    }

    // ==================== uploadBatch ====================

    @Test
    @DisplayName("uploadBatch — flows=null → 返回 0，不调 mapper")
    void uploadBatch_nullFlows_returnsZero() {
        int n = service.uploadBatch(null, OPERATOR);

        assertEquals(0, n);
        verify(flowMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("uploadBatch — flows=空集合 → 返回 0，不调 mapper")
    void uploadBatch_emptyFlows_returnsZero() {
        int n = service.uploadBatch(Collections.emptyList(), OPERATOR);

        assertEquals(0, n);
        verify(flowMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("uploadBatch — 正常 3 个 flow：每个都初始化 flowCode / extracted=0 / status=IMPORTED / createBy")
    void uploadBatch_normalFlows_initAllFields() {
        List<OpcFinanceBankFlow> flows = Arrays.asList(
                sampleFlow(), sampleFlow(), sampleFlow()
        );
        when(flowMapper.insertBatch(anyList())).thenReturn(3);

        int n = service.uploadBatch(flows, OPERATOR);

        assertEquals(3, n, "返回值 = mapper.insertBatch 返回值");

        // 验证传入 insertBatch 的 list 内容：每个 flow 都被正确初始化
        ArgumentCaptor<List<OpcFinanceBankFlow>> captor = ArgumentCaptor.forClass(List.class);
        verify(flowMapper).insertBatch(captor.capture());
        List<OpcFinanceBankFlow> captured = captor.getValue();
        assertEquals(3, captured.size());
        for (OpcFinanceBankFlow f : captured) {
            assertNotNull(f.getFlowCode(), "flowCode 应被设置");
            assertTrue(f.getFlowCode().startsWith("F"),
                    "flowCode 应以 F 开头，实际: " + f.getFlowCode());
            assertEquals(Integer.valueOf(0), f.getExtracted(),
                    "extracted 应为 0（待提取）");
            assertEquals("IMPORTED", f.getStatus());
            assertEquals(OPERATOR, f.getCreateBy());
        }

        // flowCode 在同批次内应不同（加了 i 防 hashCode 碰撞）
        assertNotEquals(captured.get(0).getFlowCode(), captured.get(1).getFlowCode(),
                "同批次内 flowCode 应唯一");
        assertNotEquals(captured.get(1).getFlowCode(), captured.get(2).getFlowCode());
    }

    @Test
    @DisplayName("uploadBatch — mapper.insertBatch 透传：service 返回值 = mapper 返回值")
    void uploadBatch_insertBatchPassthrough() {
        when(flowMapper.insertBatch(anyList())).thenReturn(5);

        int n = service.uploadBatch(Arrays.asList(sampleFlow(), sampleFlow()), OPERATOR);

        assertEquals(5, n);
    }

    @Test
    @DisplayName("uploadBatch — operator 为 null 仍能写入（不抛异常），createBy=null")
    void uploadBatch_nullOperator() {
        when(flowMapper.insertBatch(anyList())).thenReturn(1);

        int n = service.uploadBatch(Collections.singletonList(sampleFlow()), null);

        assertEquals(1, n);
        ArgumentCaptor<List<OpcFinanceBankFlow>> captor = ArgumentCaptor.forClass(List.class);
        verify(flowMapper).insertBatch(captor.capture());
        assertNull(captor.getValue().get(0).getCreateBy(),
                "operator 为 null 时 createBy 也应为 null");
    }

    @Test
    @DisplayName("uploadBatch — 不修改原 flow 的 status/extracted（service 是 set 而不是覆盖？不，service 强制覆盖为 IMPORTED）")
    void uploadBatch_overridesStatusAndExtracted() {
        OpcFinanceBankFlow flow = sampleFlow();
        flow.setStatus("DRAFT");       // 故意设错
        flow.setExtracted(1);           // 故意设错
        flow.setFlowCode("PRESET");     // 故意预设

        when(flowMapper.insertBatch(anyList())).thenReturn(1);
        service.uploadBatch(Collections.singletonList(flow), OPERATOR);

        ArgumentCaptor<List<OpcFinanceBankFlow>> captor = ArgumentCaptor.forClass(List.class);
        verify(flowMapper).insertBatch(captor.capture());
        OpcFinanceBankFlow saved = captor.getValue().get(0);
        assertEquals("IMPORTED", saved.getStatus(),
                "service 应强制覆盖 status 为 IMPORTED");
        assertEquals(Integer.valueOf(0), saved.getExtracted(),
                "service 应强制覆盖 extracted 为 0");
        // flowCode 一定会被覆盖为新生成的
        assertNotEquals("PRESET", saved.getFlowCode());
        assertTrue(saved.getFlowCode().startsWith("F"));
    }

    // ==================== listPending ====================

    @Test
    @DisplayName("listPending — limit=null → 默认 20")
    void listPending_limitNull_defaultsTo20() {
        when(flowMapper.selectByCompany(COMPANY_ID, 0, 20))
                .thenReturn(new ArrayList<>());

        List<OpcFinanceBankFlow> list = service.listPending(COMPANY_ID, null);

        assertNotNull(list);
        verify(flowMapper).selectByCompany(COMPANY_ID, 0, 20);
    }

    @Test
    @DisplayName("listPending — limit=50 → 透传，extracted 写死 0")
    void listPending_limitProvided() {
        when(flowMapper.selectByCompany(COMPANY_ID, 0, 50))
                .thenReturn(new ArrayList<>());

        service.listPending(COMPANY_ID, 50);

        verify(flowMapper).selectByCompany(COMPANY_ID, 0, 50);
    }

    @Test
    @DisplayName("listPending — mapper 返回的 list 直接返回（passthrough）")
    void listPending_passthrough() {
        List<OpcFinanceBankFlow> mockList = Arrays.asList(sampleFlow(), sampleFlow());
        when(flowMapper.selectByCompany(COMPANY_ID, 0, 20)).thenReturn(mockList);

        List<OpcFinanceBankFlow> result = service.listPending(COMPANY_ID, null);

        assertSame(mockList, result);
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById — 透传 mapper")
    void getById_returnsFromMapper() {
        OpcFinanceBankFlow f = sampleFlow();
        when(flowMapper.selectById(FLOW_ID)).thenReturn(f);

        OpcFinanceBankFlow result = service.getById(FLOW_ID);

        assertSame(f, result);
        verify(flowMapper).selectById(FLOW_ID);
    }

    @Test
    @DisplayName("getById — mapper 返回 null → service 返回 null")
    void getById_nullId_returnsNull() {
        when(flowMapper.selectById(9999L)).thenReturn(null);

        assertNull(service.getById(9999L));
    }

    // ==================== markExtracted ====================

    @Test
    @DisplayName("markExtracted — 写入 id + extracted=1 + voucherId + status=EXTRACTED + updateBy")
    void markExtracted_setsAllFields() {
        when(flowMapper.update(any(OpcFinanceBankFlow.class))).thenReturn(1);

        int rows = service.markExtracted(FLOW_ID, 555L, "ai-extractor-task-001");

        assertEquals(1, rows);
        ArgumentCaptor<OpcFinanceBankFlow> captor = ArgumentCaptor.forClass(OpcFinanceBankFlow.class);
        verify(flowMapper).update(captor.capture());
        OpcFinanceBankFlow v = captor.getValue();
        assertEquals(FLOW_ID, v.getId());
        assertEquals(Integer.valueOf(1), v.getExtracted());
        assertEquals(555L, v.getVoucherId());
        assertEquals("EXTRACTED", v.getStatus());
        assertEquals("ai-extractor-task-001", v.getUpdateBy());
    }

    @Test
    @DisplayName("markExtracted — 影响 0 行（id 不存在） → service 返回 0")
    void markExtracted_zeroRowsReturned() {
        when(flowMapper.update(any(OpcFinanceBankFlow.class))).thenReturn(0);

        int rows = service.markExtracted(9999L, 555L, "ai-task");

        assertEquals(0, rows);
    }

    @Test
    @DisplayName("markExtracted — voucherId=null 仍写入 update（mapper XML 用 <if> 跳过 voucher_id 字段）")
    void markExtracted_voucherIdNull() {
        when(flowMapper.update(any(OpcFinanceBankFlow.class))).thenReturn(1);

        int rows = service.markExtracted(FLOW_ID, null, "ai-task");

        assertEquals(1, rows);
        ArgumentCaptor<OpcFinanceBankFlow> captor = ArgumentCaptor.forClass(OpcFinanceBankFlow.class);
        verify(flowMapper).update(captor.capture());
        OpcFinanceBankFlow v = captor.getValue();
        assertNull(v.getVoucherId(), "service 不应擅自填默认值");
        // 其他字段仍正确
        assertEquals(Integer.valueOf(1), v.getExtracted());
        assertEquals("EXTRACTED", v.getStatus());
    }

    @Test
    @DisplayName("markExtracted — 不修改调用方传入的对象（service 新建 flow 写入 update）")
    void markExtracted_doesNotMutateExternalState() {
        // 验证 service 没有副作用污染 — 这是个轻量 sanity check
        when(flowMapper.update(any(OpcFinanceBankFlow.class))).thenReturn(1);

        service.markExtracted(FLOW_ID, 555L, "ai-task");

        verify(flowMapper).update(any(OpcFinanceBankFlow.class));
        // service 不应有其他副作用（如额外 selectById 等）
        verifyNoMoreInteractions(flowMapper);
    }
}
