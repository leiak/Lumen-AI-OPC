package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcErpInventoryServiceImpl 单测 (10 cases, Task 7)
 *
 * <p>覆盖:
 * <ul>
 *   <li>getRealtimeStock (2): 找到返回 / null 抛</li>
 *   <li>listLowStock (2): 多 SKU / 空列表</li>
 *   <li>getInventoryLog (2): 30 天窗口 / 无流水</li>
 *   <li>notifyLowStock (2): 有告警返回 count / 无告警返回 0</li>
 *   <li>入参校验 (2): skuId/companyId null 抛</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpInventoryService 单测 (10 cases)")
class OpcErpInventoryServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long SKU_ID = 500L;

    @Mock
    private OpcErpProductSkuMapper skuMapper;
    @Mock
    private OpcErpInventoryLogMapper inventoryLogMapper;

    @InjectMocks
    private OpcErpInventoryServiceImpl service;

    private OpcErpProductSku sampleSku;

    @BeforeEach
    void setUp() {
        sampleSku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001-黑-M")
                .stock(42).threshold(10).version(0L).status("ACTIVE")
                .build();
    }

    // ============================================================
    // getRealtimeStock (2)
    // ============================================================

    /** Test 1 */
    @Test
    @DisplayName("getRealtimeStock - 找到返回 SKU")
    void getRealtimeStock_found() {
        when(skuMapper.selectById(SKU_ID, COMPANY_ID)).thenReturn(sampleSku);

        OpcErpProductSku result = service.getRealtimeStock(SKU_ID, COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(SKU_ID);
        assertThat(result.getStock()).isEqualTo(42);
        verify(skuMapper).selectById(SKU_ID, COMPANY_ID);
    }

    /** Test 2 */
    @Test
    @DisplayName("getRealtimeStock - SKU 不存在抛异常")
    void getRealtimeStock_notFound_throws() {
        when(skuMapper.selectById(SKU_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getRealtimeStock(SKU_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SKU 不存在");
    }

    // ============================================================
    // listLowStock (2)
    // ============================================================

    /** Test 3 */
    @Test
    @DisplayName("listLowStock - 返回多个低于阈值的 SKU")
    void listLowStock_filtersCorrectly() {
        OpcErpProductSku low1 = OpcErpProductSku.builder()
                .id(501L).companyId(COMPANY_ID).skuCode("S1")
                .stock(2).threshold(10).build();
        OpcErpProductSku low2 = OpcErpProductSku.builder()
                .id(502L).companyId(COMPANY_ID).skuCode("S2")
                .stock(5).threshold(20).build();
        when(skuMapper.selectLowStock(eq(COMPANY_ID), isNull())).thenReturn(Arrays.asList(low1, low2));

        List<OpcErpProductSku> result = service.listLowStock(COMPANY_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OpcErpProductSku::getId).containsExactly(501L, 502L);
        verify(skuMapper).selectLowStock(COMPANY_ID, null);
    }

    /** Test 4 */
    @Test
    @DisplayName("listLowStock - 无低库存返回空列表")
    void listLowStock_empty() {
        when(skuMapper.selectLowStock(eq(COMPANY_ID), isNull())).thenReturn(Collections.emptyList());

        List<OpcErpProductSku> result = service.listLowStock(COMPANY_ID);

        assertThat(result).isEmpty();
    }

    // ============================================================
    // getInventoryLog (2)
    // ============================================================

    /** Test 5 */
    @Test
    @DisplayName("getInventoryLog - 查询窗口 = today - 30 days 00:00")
    void getInventoryLog_returnsLast30Days() {
        OpcErpInventoryLog log1 = OpcErpInventoryLog.builder()
                .id(1L).companyId(COMPANY_ID).skuId(SKU_ID)
                .change(10).type("PURCHASE_IN").build();
        OpcErpInventoryLog log2 = OpcErpInventoryLog.builder()
                .id(2L).companyId(COMPANY_ID).skuId(SKU_ID)
                .change(-3).type("SALE_OUT").build();
        when(inventoryLogMapper.selectBySkuId(eq(COMPANY_ID), eq(SKU_ID), any(LocalDateTime.class),
                eq(0), eq(Integer.MAX_VALUE)))
                .thenReturn(Arrays.asList(log1, log2));

        List<OpcErpInventoryLog> result = service.getInventoryLog(SKU_ID, COMPANY_ID);

        assertThat(result).hasSize(2);

        // 验证 sinceDate = today - 30 days at start of day
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(inventoryLogMapper).selectBySkuId(eq(COMPANY_ID), eq(SKU_ID), sinceCaptor.capture(),
                eq(0), eq(Integer.MAX_VALUE));
        LocalDateTime expected = LocalDate.now().minusDays(30).atStartOfDay();
        assertThat(sinceCaptor.getValue()).isEqualTo(expected);
    }

    /** Test 6 */
    @Test
    @DisplayName("getInventoryLog - 无流水返回空列表")
    void getInventoryLog_noLogs() {
        when(inventoryLogMapper.selectBySkuId(anyLong(), anyLong(), any(LocalDateTime.class),
                anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<OpcErpInventoryLog> result = service.getInventoryLog(SKU_ID, COMPANY_ID);

        assertThat(result).isEmpty();
    }

    // ============================================================
    // notifyLowStock (2)
    // ============================================================

    /** Test 7 */
    @Test
    @DisplayName("notifyLowStock - 返回低库存 SKU 数量")
    void notifyLowStock_returnsCount() {
        OpcErpProductSku low1 = OpcErpProductSku.builder().id(501L).stock(2).build();
        OpcErpProductSku low2 = OpcErpProductSku.builder().id(502L).stock(5).build();
        OpcErpProductSku low3 = OpcErpProductSku.builder().id(503L).stock(0).build();
        when(skuMapper.selectLowStock(eq(COMPANY_ID), isNull()))
                .thenReturn(Arrays.asList(low1, low2, low3));

        int count = service.notifyLowStock(COMPANY_ID);

        assertThat(count).isEqualTo(3);
    }

    /** Test 8 */
    @Test
    @DisplayName("notifyLowStock - 无低库存返回 0")
    void notifyLowStock_noAlerts() {
        when(skuMapper.selectLowStock(eq(COMPANY_ID), isNull()))
                .thenReturn(Collections.emptyList());

        int count = service.notifyLowStock(COMPANY_ID);

        assertThat(count).isZero();
    }

    // ============================================================
    // 入参校验 (2)
    // ============================================================

    /** Test 9 */
    @Test
    @DisplayName("getRealtimeStock - companyId null 抛异常")
    void getRealtimeStock_nullCompanyId_throws() {
        assertThatThrownBy(() -> service.getRealtimeStock(SKU_ID, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为空");
        verify(skuMapper, never()).selectById(anyLong(), anyLong());
    }

    /** Test 10 */
    @Test
    @DisplayName("getInventoryLog - skuId null 抛异常")
    void getInventoryLog_nullSkuId_throws() {
        assertThatThrownBy(() -> service.getInventoryLog(null, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为空");
        verify(inventoryLogMapper, never()).selectBySkuId(anyLong(), anyLong(), any(),
                anyInt(), anyInt());
    }
}
