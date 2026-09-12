package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.domain.OpcErpSale;
import com.ruoyi.opc.erp.domain.OpcErpSaleItem;
import com.ruoyi.opc.erp.dto.DeductedBatch;
import com.ruoyi.opc.erp.dto.OpcErpSaleDto;
import com.ruoyi.opc.erp.dto.OpcErpSaleItemDto;
import com.ruoyi.opc.erp.enums.ErpInventoryLogType;
import com.ruoyi.opc.erp.enums.ErpSaleStatus;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSaleItemMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSaleMapper;
import com.ruoyi.opc.erp.service.IOpcErpFifoBatchService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcErpSaleServiceImpl 单测 (15 cases)
 *
 * <p>覆盖:
 * <ul>
 *   <li>create (5): DRAFT 不写库存 / 库存预检拒绝 / customerName 空拒绝 / saleNo 格式 / saleNo 序号</li>
 *   <li>confirm (5): FIFO 单批次全扣 / FIFO 多批次拆分 / 写库存日志 / 状态机保护 CONFIRMED 拒绝 / 多批次拆分写多条 item</li>
 *   <li>cancel (2): DRAFT 允许 / COMPLETED 拒绝</li>
 *   <li>detail (2): 找到 / 找不到</li>
 *   <li>detailBySaleNo (1)</li>
 *   <li>list (1): status 过滤</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpSaleService 单测 (15 cases)")
class OpcErpSaleServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long OPERATOR_ID = 100L;
    private static final Long SALE_ID = 9001L;
    private static final Long SKU_ID = 500L;

    @Mock
    private OpcErpSaleMapper saleMapper;
    @Mock
    private OpcErpSaleItemMapper saleItemMapper;
    @Mock
    private OpcErpProductSkuMapper skuMapper;
    @Mock
    private OpcErpInventoryLogMapper inventoryLogMapper;
    @Mock
    private IOpcErpFifoBatchService fifoBatchService;

    @InjectMocks
    private OpcErpSaleServiceImpl service;

    private OpcErpSaleDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcErpSaleDto.builder()
                .companyId(COMPANY_ID)
                .customerName("测试客户")
                .customerPhone("13800000000")
                .remark("测试销售")
                .items(Collections.singletonList(
                        OpcErpSaleItemDto.builder()
                                .skuId(SKU_ID)
                                .quantity(10)
                                .unitPrice(new BigDecimal("25.00"))
                                .build()))
                .build();
        when(saleMapper.insert(any(OpcErpSale.class))).thenReturn(1);
        when(saleItemMapper.insertBatch(any())).thenReturn(1);
        when(saleMapper.countTodaySales(eq(COMPANY_ID), any(LocalDate.class))).thenReturn(0);
        // 默认 SKU 库存充足
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001-黑-M")
                .stock(100).version(1L).status("ACTIVE").build();
        when(skuMapper.selectById(eq(SKU_ID), eq(COMPANY_ID))).thenReturn(sku);
    }

    // ============================================================
    // create (5)
    // ============================================================

    /** Test 1 */
    @Test
    @DisplayName("create - 创建后 status=DRAFT, 不写库存/批次/流水")
    void create_draft_noInventoryChange() {
        Long id = service.create(COMPANY_ID, OPERATOR_ID, sampleDto);

        assertThat(id).isNotNull().isPositive();

        ArgumentCaptor<OpcErpSale> saleCaptor = ArgumentCaptor.forClass(OpcErpSale.class);
        verify(saleMapper).insert(saleCaptor.capture());
        OpcErpSale saved = saleCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ErpSaleStatus.DRAFT.getCode());
        assertThat(saved.getCustomerName()).isEqualTo("测试客户");
        assertThat(saved.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(saved.getCreatedBy()).isEqualTo(OPERATOR_ID);
        // totalAmount = 25.00 * 10 = 250.00
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("250.00");

        // DRAFT 阶段不动库存/批次/流水
        verify(inventoryLogMapper, never()).insert(any(OpcErpInventoryLog.class));
        verify(skuMapper, never()).updateByIdWithVersion(any(OpcErpProductSku.class));
        verify(fifoBatchService, never()).deductFifo(anyLong(), anyLong(), anyInt());
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 库存预检 sku.stock < quantity 时拒绝")
    void create_insufficientStock_throws() {
        OpcErpProductSku lowStockSku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001")
                .stock(3).version(1L).status("ACTIVE").build();
        when(skuMapper.selectById(eq(SKU_ID), eq(COMPANY_ID))).thenReturn(lowStockSku);

        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("库存不足")
                .hasMessageContaining("需要 10")
                .hasMessageContaining("可用 3");
        verify(saleMapper, never()).insert(any(OpcErpSale.class));
    }

    /** Test 3 */
    @Test
    @DisplayName("create - 缺 customerName 抛异常")
    void create_missingCustomerName_throws() {
        sampleDto.setCustomerName(null);
        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("customerName");
        verify(saleMapper, never()).insert(any(OpcErpSale.class));

        sampleDto.setCustomerName("  ");
        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("customerName");
    }

    /** Test 4 */
    @Test
    @DisplayName("create - saleNo 格式 SO-yyyyMMdd-NNNN")
    void saleNoFormat() {
        when(saleMapper.countTodaySales(eq(COMPANY_ID), any(LocalDate.class))).thenReturn(0);

        service.create(COMPANY_ID, OPERATOR_ID, sampleDto);

        ArgumentCaptor<OpcErpSale> captor = ArgumentCaptor.forClass(OpcErpSale.class);
        verify(saleMapper).insert(captor.capture());
        String saleNo = captor.getValue().getSaleNo();
        assertThat(saleNo).matches("^SO-\\d{8}-\\d{4}$");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(saleNo).isEqualTo("SO-" + today + "-0001");
    }

    /** Test 5: 序号按 countTodaySales+1 递增 */
    @Test
    @DisplayName("create - saleNo 序号按 countTodaySales+1 递增")
    void create_saleNoSeq() {
        when(saleMapper.countTodaySales(eq(COMPANY_ID), any(LocalDate.class))).thenReturn(7);

        service.create(COMPANY_ID, OPERATOR_ID, sampleDto);

        ArgumentCaptor<OpcErpSale> captor = ArgumentCaptor.forClass(OpcErpSale.class);
        verify(saleMapper).insert(captor.capture());
        String saleNo = captor.getValue().getSaleNo();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(saleNo).isEqualTo("SO-" + today + "-0008");
    }

    // ============================================================
    // confirm (5)
    // ============================================================

    /** Test 6 */
    @Test
    @DisplayName("confirm - 单批次足量, FIFO 返回 1 个 batch, 生成 1 条新 item, COMPLETED")
    void confirm_singleBatchFullDeduct() {
        OpcErpSale draft = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID).saleNo("SO-20260912-0001")
                .customerName("C").status(ErpSaleStatus.DRAFT.getCode())
                .build();
        OpcErpSaleItem draftItem = OpcErpSaleItem.builder()
                .id(1L).companyId(COMPANY_ID).saleId(SALE_ID).skuId(SKU_ID)
                .quantity(10).unitPrice(new BigDecimal("25.00")).subtotal(new BigDecimal("250.00"))
                .batchId(null).build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001")
                .stock(50).version(2L).status("ACTIVE").build();

        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(draft);
        when(saleItemMapper.selectBySaleId(COMPANY_ID, SALE_ID))
                .thenReturn(Collections.singletonList(draftItem));
        when(saleItemMapper.deleteBySaleId(COMPANY_ID, SALE_ID)).thenReturn(1);
        when(fifoBatchService.deductFifo(COMPANY_ID, SKU_ID, 10))
                .thenReturn(Collections.singletonList(new DeductedBatch(777L, 10)));
        when(skuMapper.selectById(eq(SKU_ID), eq(COMPANY_ID))).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any(OpcErpProductSku.class))).thenReturn(1);
        when(inventoryLogMapper.insert(any(OpcErpInventoryLog.class))).thenReturn(1);
        when(saleMapper.updateById(any(OpcErpSale.class))).thenReturn(1);

        service.confirm(SALE_ID, COMPANY_ID, OPERATOR_ID);

        // 校验 FIFO 调用 1 次
        verify(fifoBatchService, times(1)).deductFifo(COMPANY_ID, SKU_ID, 10);
        // 校验 SKU 库存减: 50 - 10 = 40
        ArgumentCaptor<OpcErpProductSku> skuCaptor = ArgumentCaptor.forClass(OpcErpProductSku.class);
        verify(skuMapper).updateByIdWithVersion(skuCaptor.capture());
        assertThat(skuCaptor.getValue().getStock()).isEqualTo(40);
        // 校验 sale status → COMPLETED
        ArgumentCaptor<OpcErpSale> saleCaptor = ArgumentCaptor.forClass(OpcErpSale.class);
        verify(saleMapper).updateById(saleCaptor.capture());
        assertThat(saleCaptor.getValue().getStatus()).isEqualTo(ErpSaleStatus.COMPLETED.getCode());
        assertThat(saleCaptor.getValue().getConfirmedBy()).isEqualTo(OPERATOR_ID);
        assertThat(saleCaptor.getValue().getConfirmedAt()).isNotNull();
        assertThat(saleCaptor.getValue().getCompletedAt()).isNotNull();
    }

    /** Test 7 */
    @Test
    @DisplayName("confirm - FIFO 拆分 2 个批次, 生成 2 条新 sale_item")
    void confirm_multiBatchPartialDeduct() {
        OpcErpSale draft = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID).saleNo("SO-20260912-0001")
                .customerName("C").status(ErpSaleStatus.DRAFT.getCode())
                .build();
        OpcErpSaleItem draftItem = OpcErpSaleItem.builder()
                .id(1L).companyId(COMPANY_ID).saleId(SALE_ID).skuId(SKU_ID)
                .quantity(15).unitPrice(new BigDecimal("20.00")).subtotal(new BigDecimal("300.00"))
                .batchId(null).build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001")
                .stock(100).version(5L).status("ACTIVE").build();

        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(draft);
        when(saleItemMapper.selectBySaleId(COMPANY_ID, SALE_ID))
                .thenReturn(Collections.singletonList(draftItem));
        when(saleItemMapper.deleteBySaleId(COMPANY_ID, SALE_ID)).thenReturn(1);
        // FIFO 拆分: 旧批次 5, 新批次 10
        when(fifoBatchService.deductFifo(COMPANY_ID, SKU_ID, 15))
                .thenReturn(Arrays.asList(new DeductedBatch(101L, 5), new DeductedBatch(102L, 10)));
        when(skuMapper.selectById(eq(SKU_ID), eq(COMPANY_ID))).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any(OpcErpProductSku.class))).thenReturn(1);
        when(inventoryLogMapper.insert(any(OpcErpInventoryLog.class))).thenReturn(1);
        when(saleMapper.updateById(any(OpcErpSale.class))).thenReturn(1);

        service.confirm(SALE_ID, COMPANY_ID, OPERATOR_ID);

        // 校验: confirm 时新 sale_item 插入 1 次(包含 2 行)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OpcErpSaleItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(saleItemMapper, times(1)).insertBatch(itemsCaptor.capture());
        List<OpcErpSaleItem> insertedItems = itemsCaptor.getValue();
        assertThat(insertedItems).hasSize(2);
        assertThat(insertedItems.get(0).getBatchId()).isEqualTo(101L);
        assertThat(insertedItems.get(0).getQuantity()).isEqualTo(5);
        assertThat(insertedItems.get(1).getBatchId()).isEqualTo(102L);
        assertThat(insertedItems.get(1).getQuantity()).isEqualTo(10);
        // subtotal 按 take/quantity 比例分配
        // 5/15 * 300 = 100.00, 10/15 * 300 = 200.00
        assertThat(insertedItems.get(0).getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(insertedItems.get(1).getSubtotal()).isEqualByComparingTo("200.00");

        // SKU 库存 100 - 15 = 85
        ArgumentCaptor<OpcErpProductSku> skuCaptor = ArgumentCaptor.forClass(OpcErpProductSku.class);
        verify(skuMapper).updateByIdWithVersion(skuCaptor.capture());
        assertThat(skuCaptor.getValue().getStock()).isEqualTo(85);
    }

    /** Test 8 */
    @Test
    @DisplayName("confirm - 写库存流水 SALE_OUT, change=-quantity")
    void confirm_writesInventoryLog() {
        OpcErpSale draft = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID).saleNo("SO-20260912-0001")
                .customerName("C").status(ErpSaleStatus.DRAFT.getCode())
                .build();
        OpcErpSaleItem draftItem = OpcErpSaleItem.builder()
                .id(1L).companyId(COMPANY_ID).saleId(SALE_ID).skuId(SKU_ID)
                .quantity(8).unitPrice(new BigDecimal("10.00")).subtotal(new BigDecimal("80.00"))
                .batchId(null).build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001")
                .stock(20).version(0L).build();

        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(draft);
        when(saleItemMapper.selectBySaleId(COMPANY_ID, SALE_ID))
                .thenReturn(Collections.singletonList(draftItem));
        when(saleItemMapper.deleteBySaleId(COMPANY_ID, SALE_ID)).thenReturn(1);
        when(fifoBatchService.deductFifo(COMPANY_ID, SKU_ID, 8))
                .thenReturn(Collections.singletonList(new DeductedBatch(555L, 8)));
        when(skuMapper.selectById(eq(SKU_ID), eq(COMPANY_ID))).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any())).thenReturn(1);
        when(inventoryLogMapper.insert(any(OpcErpInventoryLog.class))).thenReturn(1);
        when(saleMapper.updateById(any())).thenReturn(1);

        service.confirm(SALE_ID, COMPANY_ID, OPERATOR_ID);

        ArgumentCaptor<OpcErpInventoryLog> logCaptor = ArgumentCaptor.forClass(OpcErpInventoryLog.class);
        verify(inventoryLogMapper, times(1)).insert(logCaptor.capture());
        OpcErpInventoryLog logRow = logCaptor.getValue();
        assertThat(logRow.getType()).isEqualTo(ErpInventoryLogType.SALE_OUT.getCode());
        assertThat(logRow.getChange()).isEqualTo(-8);  // 负数出库
        assertThat(logRow.getRefType()).isEqualTo("SALE");
        assertThat(logRow.getRefId()).isEqualTo(SALE_ID);
        assertThat(logRow.getBatchId()).isEqualTo(555L);
        assertThat(logRow.getCreatedBy()).isEqualTo(OPERATOR_ID);
        assertThat(logRow.getRemark()).contains("SO-20260912-0001");
    }

    /** Test 9 */
    @Test
    @DisplayName("confirm - 状态已是 CONFIRMED 抛异常(状态机保护)")
    void confirm_alreadyConfirmed_throws() {
        OpcErpSale confirmed = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID)
                .status(ErpSaleStatus.CONFIRMED.getCode())
                .build();
        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(confirmed);

        assertThatThrownBy(() -> service.confirm(SALE_ID, COMPANY_ID, OPERATOR_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 DRAFT")
                .hasMessageContaining("CONFIRMED");
        verify(fifoBatchService, never()).deductFifo(anyLong(), anyLong(), anyInt());
        verify(skuMapper, never()).updateByIdWithVersion(any(OpcErpProductSku.class));
        verify(inventoryLogMapper, never()).insert(any(OpcErpInventoryLog.class));
    }

    /** Test 10 */
    @Test
    @DisplayName("confirm - FIFO 拆 2 批次写 2 条库存日志(每批次 1 条)")
    void confirm_fifoDeductsCorrectBatches() {
        OpcErpSale draft = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID).saleNo("SO-20260912-0001")
                .customerName("C").status(ErpSaleStatus.DRAFT.getCode())
                .build();
        OpcErpSaleItem draftItem = OpcErpSaleItem.builder()
                .id(1L).companyId(COMPANY_ID).saleId(SALE_ID).skuId(SKU_ID)
                .quantity(7).unitPrice(new BigDecimal("10.00")).subtotal(new BigDecimal("70.00"))
                .build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001")
                .stock(20).version(0L).build();

        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(draft);
        when(saleItemMapper.selectBySaleId(COMPANY_ID, SALE_ID))
                .thenReturn(Collections.singletonList(draftItem));
        when(saleItemMapper.deleteBySaleId(COMPANY_ID, SALE_ID)).thenReturn(1);
        when(fifoBatchService.deductFifo(COMPANY_ID, SKU_ID, 7))
                .thenReturn(Arrays.asList(new DeductedBatch(11L, 3), new DeductedBatch(22L, 4)));
        when(skuMapper.selectById(eq(SKU_ID), eq(COMPANY_ID))).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any())).thenReturn(1);
        when(inventoryLogMapper.insert(any())).thenReturn(1);
        when(saleMapper.updateById(any())).thenReturn(1);

        service.confirm(SALE_ID, COMPANY_ID, OPERATOR_ID);

        // 校验写 2 条库存日志
        ArgumentCaptor<OpcErpInventoryLog> logCaptor = ArgumentCaptor.forClass(OpcErpInventoryLog.class);
        verify(inventoryLogMapper, times(2)).insert(logCaptor.capture());
        List<OpcErpInventoryLog> logs = logCaptor.getAllValues();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getBatchId()).isEqualTo(11L);
        assertThat(logs.get(0).getChange()).isEqualTo(-3);
        assertThat(logs.get(1).getBatchId()).isEqualTo(22L);
        assertThat(logs.get(1).getChange()).isEqualTo(-4);
    }

    // ============================================================
    // cancel (2)
    // ============================================================

    /** Test 11 */
    @Test
    @DisplayName("cancel - DRAFT 状态允许取消")
    void cancel_draftAllowed() {
        OpcErpSale draft = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID).saleNo("SO-20260912-0001")
                .status(ErpSaleStatus.DRAFT.getCode()).build();
        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(draft);
        when(saleMapper.updateById(any())).thenReturn(1);

        service.cancel(SALE_ID, COMPANY_ID, OPERATOR_ID);

        ArgumentCaptor<OpcErpSale> captor = ArgumentCaptor.forClass(OpcErpSale.class);
        verify(saleMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ErpSaleStatus.CANCELLED.getCode());
        assertThat(captor.getValue().getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(captor.getValue().getCancelledAt()).isNotNull();
    }

    /** Test 12 */
    @Test
    @DisplayName("cancel - COMPLETED 状态不允许取消(需走退货流程)")
    void cancel_confirmed_throws() {
        OpcErpSale completed = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID)
                .status(ErpSaleStatus.COMPLETED.getCode())
                .build();
        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(completed);

        assertThatThrownBy(() -> service.cancel(SALE_ID, COMPANY_ID, OPERATOR_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 DRAFT")
                .hasMessageContaining("COMPLETED");
        verify(saleMapper, never()).updateById(any(OpcErpSale.class));
    }

    // ============================================================
    // detail (2)
    // ============================================================

    /** Test 13 */
    @Test
    @DisplayName("detail - 找到时返回正确数据")
    void detail_found() {
        OpcErpSale existing = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID).saleNo("SO-20260912-0001")
                .customerName("C").status(ErpSaleStatus.COMPLETED.getCode())
                .build();
        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(existing);

        OpcErpSale result = service.detail(SALE_ID, COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(SALE_ID);
        assertThat(result.getSaleNo()).isEqualTo("SO-20260912-0001");
        assertThat(result.getStatus()).isEqualTo(ErpSaleStatus.COMPLETED.getCode());
    }

    /** Test 14 */
    @Test
    @DisplayName("detail - 找不到时抛异常")
    void detail_notFound_throws() {
        when(saleMapper.selectById(SALE_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(SALE_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("销售单不存在");
    }

    // ============================================================
    // detailBySaleNo + list (2)
    // ============================================================

    /** Test 15 */
    @Test
    @DisplayName("detailBySaleNo - 返回正确销售单")
    void detailBySaleNo_returnsCorrect() {
        OpcErpSale existing = OpcErpSale.builder()
                .id(SALE_ID).companyId(COMPANY_ID).saleNo("SO-20260912-0042")
                .customerName("C").status(ErpSaleStatus.DRAFT.getCode())
                .build();
        when(saleMapper.selectBySaleNo(COMPANY_ID, "SO-20260912-0042")).thenReturn(existing);

        OpcErpSale result = service.detailBySaleNo(COMPANY_ID, "SO-20260912-0042");

        assertThat(result).isNotNull();
        assertThat(result.getSaleNo()).isEqualTo("SO-20260912-0042");
        verify(saleMapper).selectBySaleNo(COMPANY_ID, "SO-20260912-0042");
    }

    /** Test 16 (extra) */
    @Test
    @DisplayName("list - status 过滤生效")
    void list_withStatusFilter() {
        OpcErpSale s1 = OpcErpSale.builder()
                .id(11L).companyId(COMPANY_ID).saleNo("SO-A").status(ErpSaleStatus.DRAFT.getCode()).build();
        when(saleMapper.selectList(COMPANY_ID, ErpSaleStatus.DRAFT.getCode(), null, 0, Integer.MAX_VALUE))
                .thenReturn(Collections.singletonList(s1));

        List<OpcErpSale> result = service.list(COMPANY_ID, ErpSaleStatus.DRAFT.getCode(), 0, Integer.MAX_VALUE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ErpSaleStatus.DRAFT.getCode());
        verify(saleMapper).selectList(COMPANY_ID, ErpSaleStatus.DRAFT.getCode(), null, 0, Integer.MAX_VALUE);
    }
}
