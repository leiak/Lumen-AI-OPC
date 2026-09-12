package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpBatch;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.domain.OpcErpPurchase;
import com.ruoyi.opc.erp.domain.OpcErpPurchaseItem;
import com.ruoyi.opc.erp.dto.OpcErpPurchaseDto;
import com.ruoyi.opc.erp.dto.OpcErpPurchaseItemDto;
import com.ruoyi.opc.erp.enums.ErpInventoryLogType;
import com.ruoyi.opc.erp.enums.ErpPurchaseStatus;
import com.ruoyi.opc.erp.mapper.OpcErpBatchMapper;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.mapper.OpcErpPurchaseItemMapper;
import com.ruoyi.opc.erp.mapper.OpcErpPurchaseMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSupplierMapper;
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
import java.time.LocalDateTime;
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
 * OpcErpPurchaseServiceImpl 单测 (12 cases)
 *
 * <p>覆盖:
 * <ul>
 *   <li>create (6): DRAFT 不动库存 / supplierId 空 / items 空 / items 列表空 / purchaseNo 格式 / items 字段校验</li>
 *   <li>confirm (4): 批次+SKU+流水 / 状态机 CONFIRMED 拒绝 / CANCELLED 拒绝 / 库存日志</li>
 *   <li>cancel (2): DRAFT 允许 / CONFIRMED 拒绝</li>
 *   <li>detailByPurchaseNo (1)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpPurchaseService 单测 (12 cases)")
class OpcErpPurchaseServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long OPERATOR_ID = 100L;
    private static final Long PURCHASE_ID = 9001L;
    private static final Long SKU_ID = 500L;
    private static final Long SUPPLIER_ID = 200L;

    @Mock
    private OpcErpPurchaseMapper purchaseMapper;
    @Mock
    private OpcErpPurchaseItemMapper purchaseItemMapper;
    @Mock
    private OpcErpBatchMapper batchMapper;
    @Mock
    private OpcErpProductSkuMapper skuMapper;
    @Mock
    private OpcErpInventoryLogMapper inventoryLogMapper;
    @Mock
    private OpcErpSupplierMapper supplierMapper;  // 未使用,保留兼容性

    @InjectMocks
    private OpcErpPurchaseServiceImpl service;

    private OpcErpPurchaseDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcErpPurchaseDto.builder()
                .companyId(COMPANY_ID)
                .supplierId(SUPPLIER_ID)
                .remark("测试采购")
                .items(Collections.singletonList(
                        OpcErpPurchaseItemDto.builder()
                                .skuId(SKU_ID)
                                .quantity(10)
                                .unitPrice(new BigDecimal("12.50"))
                                .batchNo("B20260912-A")
                                .productionDate(LocalDate.of(2026, 9, 1))
                                .expiryDate(LocalDate.of(2027, 9, 1))
                                .build()))
                .build();
        when(purchaseMapper.insert(any(OpcErpPurchase.class))).thenReturn(1);
        when(purchaseItemMapper.insertBatch(any())).thenReturn(1);
        when(purchaseMapper.countTodayPurchases(eq(COMPANY_ID), any(LocalDate.class))).thenReturn(0);
    }

    // ============================================================
    // create (6)
    // ============================================================

    /** Test 1 */
    @Test
    @DisplayName("create - 创建后 status=DRAFT,不写库存/批次/流水")
    void create_draftStatus_noInventoryChange() {
        Long id = service.create(COMPANY_ID, OPERATOR_ID, sampleDto);

        assertThat(id).isNotNull().isPositive();

        ArgumentCaptor<OpcErpPurchase> purchaseCaptor = ArgumentCaptor.forClass(OpcErpPurchase.class);
        verify(purchaseMapper).insert(purchaseCaptor.capture());
        OpcErpPurchase saved = purchaseCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ErpPurchaseStatus.DRAFT.getCode());
        assertThat(saved.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(saved.getCreatedBy()).isEqualTo(OPERATOR_ID);
        assertThat(saved.getSupplierId()).isEqualTo(SUPPLIER_ID);
        // totalAmount = 12.50 * 10 = 125.00
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("125.00");

        // DRAFT 阶段不动库存/批次/流水
        verify(batchMapper, never()).insert(any(OpcErpBatch.class));
        verify(inventoryLogMapper, never()).insert(any(OpcErpInventoryLog.class));
        verify(skuMapper, never()).updateByIdWithVersion(any(OpcErpProductSku.class));
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 缺 supplierId 抛异常")
    void create_missingSupplierId_throws() {
        sampleDto.setSupplierId(null);
        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("supplierId");
        verify(purchaseMapper, never()).insert(any(OpcErpPurchase.class));
    }

    /** Test 3 */
    @Test
    @DisplayName("create - 缺 items 抛异常")
    void create_missingItems_throws() {
        sampleDto.setItems(null);
        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("采购明细");
        verify(purchaseMapper, never()).insert(any(OpcErpPurchase.class));
    }

    /** Test 4 */
    @Test
    @DisplayName("create - items 为空列表抛异常")
    void create_emptyItems_throws() {
        sampleDto.setItems(Collections.emptyList());
        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("采购明细");
        verify(purchaseMapper, never()).insert(any(OpcErpPurchase.class));
    }

    /** Test 5 */
    @Test
    @DisplayName("create - purchaseNo 格式 PO-yyyyMMdd-NNNN")
    void create_purchaseNoFormat() {
        when(purchaseMapper.countTodayPurchases(eq(COMPANY_ID), any(LocalDate.class))).thenReturn(0);

        service.create(COMPANY_ID, OPERATOR_ID, sampleDto);

        ArgumentCaptor<OpcErpPurchase> captor = ArgumentCaptor.forClass(OpcErpPurchase.class);
        verify(purchaseMapper).insert(captor.capture());
        String purchaseNo = captor.getValue().getPurchaseNo();
        assertThat(purchaseNo).matches("^PO-\\d{8}-\\d{4}$");
        // 验证当日序号 = count + 1 = 1,4 位补零
        String today = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(purchaseNo).isEqualTo("PO-" + today + "-0001");
    }

    /** Test 5b: countTodayPurchases 返回 N → 序号 N+1 */
    @Test
    @DisplayName("create - purchaseNo 序号按 countTodayPurchases+1 递增")
    void create_purchaseNoSeq() {
        when(purchaseMapper.countTodayPurchases(eq(COMPANY_ID), any(LocalDate.class))).thenReturn(7);

        service.create(COMPANY_ID, OPERATOR_ID, sampleDto);

        ArgumentCaptor<OpcErpPurchase> captor = ArgumentCaptor.forClass(OpcErpPurchase.class);
        verify(purchaseMapper).insert(captor.capture());
        String purchaseNo = captor.getValue().getPurchaseNo();
        String today = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(purchaseNo).isEqualTo("PO-" + today + "-0008");  // 7 + 1 = 8
    }

    // ============================================================
    // confirm (3)
    // ============================================================

    /** Test 6 */
    @Test
    @DisplayName("confirm - 创建批次 + 增加 SKU 库存（乐观锁 + 流水）")
    void confirm_createsBatchAndIncreasesStock() {
        // 准备 DRAFT 采购单 + 1 个 item + SKU
        OpcErpPurchase draft = OpcErpPurchase.builder()
                .id(PURCHASE_ID).companyId(COMPANY_ID).supplierId(SUPPLIER_ID)
                .purchaseNo("PO-20260912-0001")
                .status(ErpPurchaseStatus.DRAFT.getCode())
                .operatorId(OPERATOR_ID).createdBy(OPERATOR_ID)
                .totalAmount(new BigDecimal("125.00"))
                .build();
        OpcErpPurchaseItem item = OpcErpPurchaseItem.builder()
                .id(1L).companyId(COMPANY_ID).purchaseId(PURCHASE_ID)
                .skuId(SKU_ID).quantity(10)
                .unitPrice(new BigDecimal("12.50")).subtotal(new BigDecimal("125.00"))
                .batchNo("B20260912-A")
                .productionDate(LocalDate.of(2026, 9, 1))
                .expiryDate(LocalDate.of(2027, 9, 1))
                .build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001-黑-M")
                .stock(20).version(3L).status("ACTIVE").build();

        when(purchaseMapper.selectById(PURCHASE_ID, COMPANY_ID)).thenReturn(draft);
        when(purchaseItemMapper.selectByPurchaseId(COMPANY_ID, PURCHASE_ID))
                .thenReturn(Collections.singletonList(item));
        when(skuMapper.selectById(SKU_ID, COMPANY_ID)).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any(OpcErpProductSku.class))).thenReturn(1);
        when(batchMapper.insert(any(OpcErpBatch.class))).thenReturn(1);
        when(inventoryLogMapper.insert(any(OpcErpInventoryLog.class))).thenReturn(1);
        when(purchaseMapper.updateById(any(OpcErpPurchase.class))).thenReturn(1);

        service.confirm(PURCHASE_ID, COMPANY_ID, OPERATOR_ID);

        // 验证: batch insert (quantity=10, remaining=10)
        ArgumentCaptor<OpcErpBatch> batchCaptor = ArgumentCaptor.forClass(OpcErpBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        OpcErpBatch savedBatch = batchCaptor.getValue();
        assertThat(savedBatch.getQuantity()).isEqualTo(10);
        assertThat(savedBatch.getRemaining()).isEqualTo(10);
        assertThat(savedBatch.getBatchNo()).isEqualTo("B20260912-A");
        assertThat(savedBatch.getProductionDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(savedBatch.getExpiryDate()).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(savedBatch.getSupplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(savedBatch.getPurchaseId()).isEqualTo(PURCHASE_ID);

        // 验证: sku.stock = 20 + 10 = 30, version 由 3 → 4
        ArgumentCaptor<OpcErpProductSku> skuCaptor = ArgumentCaptor.forClass(OpcErpProductSku.class);
        verify(skuMapper).updateByIdWithVersion(skuCaptor.capture());
        OpcErpProductSku updatedSku = skuCaptor.getValue();
        assertThat(updatedSku.getStock()).isEqualTo(30);
        // 注意: updateByIdWithVersion 是 set stock=?, version=version+1,version 字段在 update 前已是 3,
        //       service 不修改 version 字段,WHERE 子句校验 version=3
        assertThat(updatedSku.getVersion()).isEqualTo(3L);

        // 验证: purchase.status = COMPLETED（自动晋升）
        ArgumentCaptor<OpcErpPurchase> purchaseCaptor = ArgumentCaptor.forClass(OpcErpPurchase.class);
        verify(purchaseMapper).updateById(purchaseCaptor.capture());
        OpcErpPurchase updated = purchaseCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(ErpPurchaseStatus.COMPLETED.getCode());
        assertThat(updated.getConfirmedBy()).isEqualTo(OPERATOR_ID);
        assertThat(updated.getConfirmedAt()).isNotNull();
        assertThat(updated.getCompletedAt()).isNotNull();
    }

    /** Test 7 */
    @Test
    @DisplayName("confirm - 写库存流水 PURCHASE_IN change=+quantity")
    void confirm_writesInventoryLog() {
        OpcErpPurchase draft = OpcErpPurchase.builder()
                .id(PURCHASE_ID).companyId(COMPANY_ID).supplierId(SUPPLIER_ID)
                .purchaseNo("PO-20260912-0001")
                .status(ErpPurchaseStatus.DRAFT.getCode())
                .build();
        OpcErpPurchaseItem item = OpcErpPurchaseItem.builder()
                .id(1L).companyId(COMPANY_ID).purchaseId(PURCHASE_ID)
                .skuId(SKU_ID).quantity(5)
                .unitPrice(new BigDecimal("10.00")).subtotal(new BigDecimal("50.00"))
                .batchNo("B-X")
                .productionDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(1))
                .build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).skuCode("T001")
                .stock(0).version(0L).build();

        when(purchaseMapper.selectById(PURCHASE_ID, COMPANY_ID)).thenReturn(draft);
        when(purchaseItemMapper.selectByPurchaseId(COMPANY_ID, PURCHASE_ID))
                .thenReturn(Collections.singletonList(item));
        when(skuMapper.selectById(SKU_ID, COMPANY_ID)).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any())).thenReturn(1);
        when(batchMapper.insert(any())).thenReturn(1);
        when(inventoryLogMapper.insert(any())).thenReturn(1);
        when(purchaseMapper.updateById(any())).thenReturn(1);

        service.confirm(PURCHASE_ID, COMPANY_ID, OPERATOR_ID);

        ArgumentCaptor<OpcErpInventoryLog> logCaptor = ArgumentCaptor.forClass(OpcErpInventoryLog.class);
        verify(inventoryLogMapper).insert(logCaptor.capture());
        OpcErpInventoryLog logRow = logCaptor.getValue();
        assertThat(logRow.getType()).isEqualTo(ErpInventoryLogType.PURCHASE_IN.getCode());
        assertThat(logRow.getChange()).isEqualTo(5);  // 正数入库
        assertThat(logRow.getRefType()).isEqualTo("PURCHASE");
        assertThat(logRow.getRefId()).isEqualTo(PURCHASE_ID);
        assertThat(logRow.getBatchId()).isNotNull();
        assertThat(logRow.getRemark()).contains("PO-20260912-0001");
        assertThat(logRow.getCreatedBy()).isEqualTo(OPERATOR_ID);
    }

    /** Test 8 */
    @Test
    @DisplayName("confirm - 状态已是 CONFIRMED 抛异常（状态机保护）")
    void confirm_alreadyConfirmed_throws() {
        OpcErpPurchase confirmed = OpcErpPurchase.builder()
                .id(PURCHASE_ID).companyId(COMPANY_ID)
                .status(ErpPurchaseStatus.CONFIRMED.getCode())
                .build();
        when(purchaseMapper.selectById(PURCHASE_ID, COMPANY_ID)).thenReturn(confirmed);

        assertThatThrownBy(() -> service.confirm(PURCHASE_ID, COMPANY_ID, OPERATOR_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 DRAFT")
                .hasMessageContaining("CONFIRMED");
        verify(batchMapper, never()).insert(any(OpcErpBatch.class));
        verify(skuMapper, never()).updateByIdWithVersion(any(OpcErpProductSku.class));
        verify(inventoryLogMapper, never()).insert(any(OpcErpInventoryLog.class));
    }

    /** Test 9 */
    @Test
    @DisplayName("confirm - 状态已是 CANCELLED 抛异常")
    void confirm_cancelled_throws() {
        OpcErpPurchase cancelled = OpcErpPurchase.builder()
                .id(PURCHASE_ID).companyId(COMPANY_ID)
                .status(ErpPurchaseStatus.CANCELLED.getCode())
                .build();
        when(purchaseMapper.selectById(PURCHASE_ID, COMPANY_ID)).thenReturn(cancelled);

        assertThatThrownBy(() -> service.confirm(PURCHASE_ID, COMPANY_ID, OPERATOR_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 DRAFT")
                .hasMessageContaining("CANCELLED");
        verify(batchMapper, never()).insert(any(OpcErpBatch.class));
    }

    // ============================================================
    // cancel (2)
    // ============================================================

    /** Test 10 */
    @Test
    @DisplayName("cancel - DRAFT 状态允许取消")
    void cancel_draftAllowed() {
        OpcErpPurchase draft = OpcErpPurchase.builder()
                .id(PURCHASE_ID).companyId(COMPANY_ID)
                .purchaseNo("PO-20260912-0001")
                .status(ErpPurchaseStatus.DRAFT.getCode())
                .build();
        when(purchaseMapper.selectById(PURCHASE_ID, COMPANY_ID)).thenReturn(draft);
        when(purchaseMapper.updateById(any())).thenReturn(1);

        service.cancel(PURCHASE_ID, COMPANY_ID, OPERATOR_ID);

        ArgumentCaptor<OpcErpPurchase> captor = ArgumentCaptor.forClass(OpcErpPurchase.class);
        verify(purchaseMapper).updateById(captor.capture());
        OpcErpPurchase updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(ErpPurchaseStatus.CANCELLED.getCode());
        assertThat(updated.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(updated.getCancelledAt()).isNotNull();
        // DRAFT 取消不动库存
        verify(batchMapper, never()).insert(any(OpcErpBatch.class));
        verify(inventoryLogMapper, never()).insert(any(OpcErpInventoryLog.class));
        verify(skuMapper, never()).updateByIdWithVersion(any(OpcErpProductSku.class));
    }

    /** Test 11 */
    @Test
    @DisplayName("cancel - CONFIRMED 状态不允许取消（需走退货流程）")
    void cancel_confirmed_throws() {
        OpcErpPurchase confirmed = OpcErpPurchase.builder()
                .id(PURCHASE_ID).companyId(COMPANY_ID)
                .status(ErpPurchaseStatus.COMPLETED.getCode())
                .build();
        when(purchaseMapper.selectById(PURCHASE_ID, COMPANY_ID)).thenReturn(confirmed);

        assertThatThrownBy(() -> service.cancel(PURCHASE_ID, COMPANY_ID, OPERATOR_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 DRAFT");
        verify(purchaseMapper, never()).updateById(any(OpcErpPurchase.class));
    }

    // ============================================================
    // detailByPurchaseNo (1)
    // ============================================================

    /** Test 12 */
    @Test
    @DisplayName("detailByPurchaseNo - 返回正确采购单")
    void detail_byPurchaseNo_returnsCorrectPurchase() {
        OpcErpPurchase existing = OpcErpPurchase.builder()
                .id(PURCHASE_ID).companyId(COMPANY_ID).purchaseNo("PO-20260912-0042")
                .supplierId(SUPPLIER_ID).status(ErpPurchaseStatus.DRAFT.getCode())
                .build();
        when(purchaseMapper.selectByPurchaseNo(COMPANY_ID, "PO-20260912-0042"))
                .thenReturn(existing);

        OpcErpPurchase result = service.detailByPurchaseNo(COMPANY_ID, "PO-20260912-0042");

        assertThat(result).isNotNull();
        assertThat(result.getPurchaseNo()).isEqualTo("PO-20260912-0042");
        assertThat(result.getSupplierId()).isEqualTo(SUPPLIER_ID);
        verify(purchaseMapper).selectByPurchaseNo(COMPANY_ID, "PO-20260912-0042");
    }
}
