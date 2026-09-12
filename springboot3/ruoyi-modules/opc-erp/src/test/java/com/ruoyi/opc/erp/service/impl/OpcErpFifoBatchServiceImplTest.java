package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpBatch;
import com.ruoyi.opc.erp.dto.DeductedBatch;
import com.ruoyi.opc.erp.mapper.OpcErpBatchMapper;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcErpFifoBatchServiceImpl 单测 (4 cases)
 *
 * <p>覆盖:
 * <ul>
 *   <li>单批次足量(全部从 1 个批次扣完)</li>
 *   <li>多批次跨度扣减(3 批次, 数量横跨全部)</li>
 *   <li>库存不足抛 ServiceException</li>
 *   <li>数量 &lt;= 0 抛 ServiceException</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpFifoBatchService 单测 (4 cases)")
class OpcErpFifoBatchServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long SKU_ID = 500L;
    private static final int FIFO_LIMIT = 100;

    @Mock
    private OpcErpBatchMapper batchMapper;

    @InjectMocks
    private OpcErpFifoBatchServiceImpl service;

    /** Test 1: 单批次足量 */
    @Test
    @DisplayName("deductFifo - 单批次足量, 全部从 1 个批次扣完")
    void deductFifo_singleBatchSufficient() {
        OpcErpBatch b1 = OpcErpBatch.builder()
                .id(101L).companyId(COMPANY_ID).skuId(SKU_ID)
                .batchNo("B001").quantity(50).remaining(50)
                .productionDate(LocalDate.of(2026, 1, 1))
                .build();
        when(batchMapper.selectFifoOrderForUpdate(COMPANY_ID, SKU_ID, FIFO_LIMIT))
                .thenReturn(Collections.singletonList(b1));
        when(batchMapper.updateRemaining(eq(101L), eq(COMPANY_ID), anyInt())).thenReturn(1);

        List<DeductedBatch> result = service.deductFifo(COMPANY_ID, SKU_ID, 30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBatchId()).isEqualTo(101L);
        assertThat(result.get(0).getQuantity()).isEqualTo(30);
        // 校验 updateRemaining 用 delta = -30
        verify(batchMapper).updateRemaining(101L, COMPANY_ID, -30);
    }

    /** Test 2: 多批次跨度扣减 */
    @Test
    @DisplayName("deductFifo - 多批次跨度扣减, 数量横跨 3 个批次")
    void deductFifo_multiBatchSpanning() {
        OpcErpBatch b1 = OpcErpBatch.builder()
                .id(101L).companyId(COMPANY_ID).skuId(SKU_ID)
                .batchNo("B-OLD").quantity(100).remaining(20)  // 最早批次, 只够扣 20
                .productionDate(LocalDate.of(2026, 1, 1))
                .build();
        OpcErpBatch b2 = OpcErpBatch.builder()
                .id(102L).companyId(COMPANY_ID).skuId(SKU_ID)
                .batchNo("B-MID").quantity(80).remaining(30)   // 扣 30, 还剩 0
                .productionDate(LocalDate.of(2026, 5, 1))
                .build();
        OpcErpBatch b3 = OpcErpBatch.builder()
                .id(103L).companyId(COMPANY_ID).skuId(SKU_ID)
                .batchNo("B-NEW").quantity(200).remaining(200) // 扣 50, 还剩 150
                .productionDate(LocalDate.of(2026, 9, 1))
                .build();
        when(batchMapper.selectFifoOrderForUpdate(COMPANY_ID, SKU_ID, FIFO_LIMIT))
                .thenReturn(Arrays.asList(b1, b2, b3));
        when(batchMapper.updateRemaining(anyLong(), eq(COMPANY_ID), anyInt())).thenReturn(1);

        // 总需求 100 = 20(b1) + 30(b2) + 50(b3)
        List<DeductedBatch> result = service.deductFifo(COMPANY_ID, SKU_ID, 100);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(new DeductedBatch(101L, 20));
        assertThat(result.get(1)).isEqualTo(new DeductedBatch(102L, 30));
        assertThat(result.get(2)).isEqualTo(new DeductedBatch(103L, 50));

        // 校验每次扣减的 delta 都为负数
        ArgumentCaptor<Integer> deltaCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(batchMapper, times(3)).updateRemaining(anyLong(), eq(COMPANY_ID), deltaCaptor.capture());
        assertThat(deltaCaptor.getAllValues()).containsExactly(-20, -30, -50);
    }

    /** Test 3: 库存不足 */
    @Test
    @DisplayName("deductFifo - 总可用 < 请求数量, 抛 ServiceException")
    void deductFifo_insufficientRemainingThrows() {
        OpcErpBatch b1 = OpcErpBatch.builder()
                .id(201L).companyId(COMPANY_ID).skuId(SKU_ID)
                .batchNo("B1").quantity(50).remaining(20)
                .productionDate(LocalDate.of(2026, 1, 1))
                .build();
        OpcErpBatch b2 = OpcErpBatch.builder()
                .id(202L).companyId(COMPANY_ID).skuId(SKU_ID)
                .batchNo("B2").quantity(80).remaining(30)
                .productionDate(LocalDate.of(2026, 5, 1))
                .build();
        when(batchMapper.selectFifoOrderForUpdate(COMPANY_ID, SKU_ID, FIFO_LIMIT))
                .thenReturn(Arrays.asList(b1, b2));
        // totalRemaining = 50, 请求 100 → 不足

        assertThatThrownBy(() -> service.deductFifo(COMPANY_ID, SKU_ID, 100))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("库存不足")
                .hasMessageContaining("需要 100")
                .hasMessageContaining("可用 50");
        verify(batchMapper, never()).updateRemaining(anyLong(), anyLong(), anyInt());
    }

    /** Test 4: 数量 <= 0 */
    @Test
    @DisplayName("deductFifo - quantity <= 0 抛 ServiceException")
    void deductFifo_zeroQuantityThrows() {
        assertThatThrownBy(() -> service.deductFifo(COMPANY_ID, SKU_ID, 0))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("扣减数量必须 > 0");

        assertThatThrownBy(() -> service.deductFifo(COMPANY_ID, SKU_ID, -5))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("扣减数量必须 > 0");

        verify(batchMapper, never()).selectFifoOrderForUpdate(anyLong(), anyLong(), anyInt());
        verify(batchMapper, never()).updateRemaining(anyLong(), anyLong(), anyInt());
    }
}
