package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpBatch;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.domain.OpcErpPurchase;
import com.ruoyi.opc.erp.domain.OpcErpPurchaseItem;
import com.ruoyi.opc.erp.domain.OpcErpReturn;
import com.ruoyi.opc.erp.domain.OpcErpSale;
import com.ruoyi.opc.erp.domain.OpcErpSaleItem;
import com.ruoyi.opc.erp.dto.OpcErpReturnDto;
import com.ruoyi.opc.erp.dto.OpcErpReturnItemDto;
import com.ruoyi.opc.erp.enums.ErpInventoryLogType;
import com.ruoyi.opc.erp.enums.ErpPurchaseStatus;
import com.ruoyi.opc.erp.enums.ErpReturnStatus;
import com.ruoyi.opc.erp.enums.ErpReturnType;
import com.ruoyi.opc.erp.enums.ErpSaleStatus;
import com.ruoyi.opc.erp.mapper.OpcErpBatchMapper;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.mapper.OpcErpPurchaseItemMapper;
import com.ruoyi.opc.erp.mapper.OpcErpPurchaseMapper;
import com.ruoyi.opc.erp.mapper.OpcErpReturnMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSaleItemMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSaleMapper;
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
import java.util.Collections;

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
 * OpcErpReturnServiceImpl 单测 (10 cases)
 *
 * <p>覆盖:
 * <ul>
 *   <li>create (3): SALES_RETURN 校验 saleId / SUPPLIER_RETURN 校验 purchaseId / 非法 returnType 拒绝</li>
 *   <li>confirm (4): 销退入库加库存 / 采退出库减库存 / 写流水 / 已 CONFIRMED 拒绝</li>
 *   <li>cancel (1): DRAFT 允许</li>
 *   <li>detail (1)</li>
 *   <li>list (1): type 过滤</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpReturnService 单测 (10 cases)")
class OpcErpReturnServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long OPERATOR_ID = 100L;
    private static final Long RETURN_ID = 9001L;
    private static final Long REF_SALE_ID = 8001L;
    private static final Long REF_PURCHASE_ID = 8002L;
    private static final Long SKU_ID = 500L;
    private static final Long BATCH_ID = 600L;

    @Mock
    private OpcErpReturnMapper returnMapper;
    @Mock
    private OpcErpSaleMapper saleMapper;
    @Mock
    private OpcErpSaleItemMapper saleItemMapper;
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

    @InjectMocks
    private OpcErpReturnServiceImpl service;

    private OpcErpReturnDto sampleSalesReturnDto;
    private OpcErpReturnDto sampleSupplierReturnDto;

    @BeforeEach
    void setUp() {
        sampleSalesReturnDto = OpcErpReturnDto.builder()
                .companyId(COMPANY_ID)
                .returnType(ErpReturnType.SALES_RETURN.getCode())
                .refId(REF_SALE_ID)
                .reason("质量问题")
                .items(Collections.singletonList(
                        OpcErpReturnItemDto.builder()
                                .skuId(SKU_ID)
                                .quantity(2)
                                .unitPrice(new BigDecimal("25.00"))
                                .subtotal(new BigDecimal("50.00"))
                                .build()))
                .build();

        sampleSupplierReturnDto = OpcErpReturnDto.builder()
                .companyId(COMPANY_ID)
                .returnType(ErpReturnType.SUPPLIER_RETURN.getCode())
                .refId(REF_PURCHASE_ID)
                .reason("过期")
                .items(Collections.singletonList(
                        OpcErpReturnItemDto.builder()
                                .skuId(SKU_ID)
                                .quantity(3)
                                .unitPrice(new BigDecimal("10.00"))
                                .subtotal(new BigDecimal("30.00"))
                                .build()))
                .build();

        when(returnMapper.insert(any(OpcErpReturn.class))).thenReturn(1);
        when(returnMapper.countTodayReturns(eq(COMPANY_ID), any(LocalDate.class))).thenReturn(0);
        when(returnMapper.updateById(any(OpcErpReturn.class))).thenReturn(1);
    }

    // ============================================================
    // create (3)
    // ============================================================

    /** Test 1 */
    @Test
    @DisplayName("create - SALES_RETURN 校验 ref 销售单存在且 COMPLETED")
    void create_salesReturn_validatesSaleId() {
        // 缺 sale: 应抛
        when(saleMapper.selectById(REF_SALE_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleSalesReturnDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("销售单不存在");
        verify(returnMapper, never()).insert(any(OpcErpReturn.class));

        // sale 存在但不是 COMPLETED
        OpcErpSale draftSale = OpcErpSale.builder()
                .id(REF_SALE_ID).companyId(COMPANY_ID)
                .status(ErpSaleStatus.DRAFT.getCode()).build();
        when(saleMapper.selectById(REF_SALE_ID, COMPANY_ID)).thenReturn(draftSale);

        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleSalesReturnDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 COMPLETED");
    }

    /** Test 2 */
    @Test
    @DisplayName("create - SUPPLIER_RETURN 校验 ref 采购单存在且 COMPLETED")
    void create_supplierReturn_validatesPurchaseId() {
        when(purchaseMapper.selectById(REF_PURCHASE_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleSupplierReturnDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("采购单不存在");
        verify(returnMapper, never()).insert(any(OpcErpReturn.class));

        OpcErpPurchase draftPurchase = OpcErpPurchase.builder()
                .id(REF_PURCHASE_ID).companyId(COMPANY_ID)
                .status(ErpPurchaseStatus.DRAFT.getCode()).build();
        when(purchaseMapper.selectById(REF_PURCHASE_ID, COMPANY_ID)).thenReturn(draftPurchase);

        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleSupplierReturnDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 COMPLETED");
    }

    /** Test 3 */
    @Test
    @DisplayName("create - 非法 returnType 抛异常")
    void create_invalidReturnType_throws() {
        sampleSalesReturnDto.setReturnType("INVALID_TYPE");

        assertThatThrownBy(() -> service.create(COMPANY_ID, OPERATOR_ID, sampleSalesReturnDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未知 returnType");
        verify(returnMapper, never()).insert(any(OpcErpReturn.class));
    }

    // ============================================================
    // confirm (4)
    // ============================================================

    /** Test 4 */
    @Test
    @DisplayName("confirm - SALES_RETURN 入库: 加批次 remaining + 加 SKU 库存 + 流水 SALES_RETURN_IN")
    void confirm_salesReturn_addsBackStock() {
        OpcErpReturn draftReturn = OpcErpReturn.builder()
                .id(RETURN_ID).companyId(COMPANY_ID)
                .returnNo("RT-20260912-0001")
                .returnType(ErpReturnType.SALES_RETURN.getCode())
                .refId(REF_SALE_ID)
                .status(ErpReturnStatus.DRAFT.getCode())
                .build();
        OpcErpSaleItem saleItem = OpcErpSaleItem.builder()
                .id(1L).companyId(COMPANY_ID).saleId(REF_SALE_ID)
                .skuId(SKU_ID).quantity(2).batchId(BATCH_ID).build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).stock(8).version(5L).build();

        when(returnMapper.selectById(RETURN_ID, COMPANY_ID)).thenReturn(draftReturn);
        when(saleItemMapper.selectBySaleId(COMPANY_ID, REF_SALE_ID))
                .thenReturn(Collections.singletonList(saleItem));
        when(batchMapper.updateRemaining(anyLong(), anyLong(), anyInt())).thenReturn(1);
        when(skuMapper.selectById(SKU_ID, COMPANY_ID)).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any(OpcErpProductSku.class))).thenReturn(1);
        when(inventoryLogMapper.insert(any(OpcErpInventoryLog.class))).thenReturn(1);

        service.confirm(RETURN_ID, COMPANY_ID, OPERATOR_ID);

        // 批次 remaining += 2 (delta=+2)
        verify(batchMapper).updateRemaining(eq(BATCH_ID), eq(COMPANY_ID), eq(2));
        // SKU 库存: 8 + 2 = 10
        ArgumentCaptor<OpcErpProductSku> skuCaptor = ArgumentCaptor.forClass(OpcErpProductSku.class);
        verify(skuMapper).updateByIdWithVersion(skuCaptor.capture());
        assertThat(skuCaptor.getValue().getStock()).isEqualTo(10);
        // 退货单 status 改为 COMPLETED
        ArgumentCaptor<OpcErpReturn> returnCaptor = ArgumentCaptor.forClass(OpcErpReturn.class);
        verify(returnMapper, times(1)).updateById(returnCaptor.capture());
        assertThat(returnCaptor.getValue().getStatus()).isEqualTo(ErpReturnStatus.COMPLETED.getCode());
    }

    /** Test 5 */
    @Test
    @DisplayName("confirm - SUPPLIER_RETURN 出库: 减批次 remaining + 减 SKU 库存 + 流水 SUPPLIER_RETURN_OUT")
    void confirm_supplierReturn_deductsStock() {
        OpcErpReturn draftReturn = OpcErpReturn.builder()
                .id(RETURN_ID).companyId(COMPANY_ID)
                .returnNo("RT-20260912-0002")
                .returnType(ErpReturnType.SUPPLIER_RETURN.getCode())
                .refId(REF_PURCHASE_ID)
                .status(ErpReturnStatus.DRAFT.getCode())
                .build();
        OpcErpPurchaseItem purchaseItem = OpcErpPurchaseItem.builder()
                .id(1L).companyId(COMPANY_ID).purchaseId(REF_PURCHASE_ID)
                .skuId(SKU_ID).quantity(3)
                .batchNo("B20260912-A").build();
        OpcErpBatch batch = OpcErpBatch.builder()
                .id(BATCH_ID).companyId(COMPANY_ID).skuId(SKU_ID)
                .batchNo("B20260912-A").quantity(10).remaining(8).build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).stock(20).version(2L).build();

        when(returnMapper.selectById(RETURN_ID, COMPANY_ID)).thenReturn(draftReturn);
        when(purchaseItemMapper.selectByPurchaseId(COMPANY_ID, REF_PURCHASE_ID))
                .thenReturn(Collections.singletonList(purchaseItem));
        when(batchMapper.selectBySkuAndBatchNo(COMPANY_ID, SKU_ID, "B20260912-A")).thenReturn(batch);
        when(batchMapper.updateRemaining(anyLong(), anyLong(), anyInt())).thenReturn(1);
        when(skuMapper.selectById(SKU_ID, COMPANY_ID)).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any(OpcErpProductSku.class))).thenReturn(1);
        when(inventoryLogMapper.insert(any(OpcErpInventoryLog.class))).thenReturn(1);

        service.confirm(RETURN_ID, COMPANY_ID, OPERATOR_ID);

        // 批次 remaining -= 3 (delta=-3)
        verify(batchMapper).updateRemaining(eq(BATCH_ID), eq(COMPANY_ID), eq(-3));
        // SKU 库存: 20 - 3 = 17
        ArgumentCaptor<OpcErpProductSku> skuCaptor = ArgumentCaptor.forClass(OpcErpProductSku.class);
        verify(skuMapper).updateByIdWithVersion(skuCaptor.capture());
        assertThat(skuCaptor.getValue().getStock()).isEqualTo(17);
        // 退货单 status 改为 COMPLETED
        ArgumentCaptor<OpcErpReturn> returnCaptor = ArgumentCaptor.forClass(OpcErpReturn.class);
        verify(returnMapper).updateById(returnCaptor.capture());
        assertThat(returnCaptor.getValue().getStatus()).isEqualTo(ErpReturnStatus.COMPLETED.getCode());
    }

    /** Test 6 */
    @Test
    @DisplayName("confirm - 写库存流水(类型 + ref + 备注)")
    void confirm_writesInventoryLog() {
        OpcErpReturn draftReturn = OpcErpReturn.builder()
                .id(RETURN_ID).companyId(COMPANY_ID)
                .returnNo("RT-20260912-0003")
                .returnType(ErpReturnType.SALES_RETURN.getCode())
                .refId(REF_SALE_ID)
                .status(ErpReturnStatus.DRAFT.getCode())
                .build();
        OpcErpSaleItem saleItem = OpcErpSaleItem.builder()
                .id(1L).companyId(COMPANY_ID).saleId(REF_SALE_ID)
                .skuId(SKU_ID).quantity(1).batchId(BATCH_ID).build();
        OpcErpProductSku sku = OpcErpProductSku.builder()
                .id(SKU_ID).companyId(COMPANY_ID).stock(5).version(1L).build();

        when(returnMapper.selectById(RETURN_ID, COMPANY_ID)).thenReturn(draftReturn);
        when(saleItemMapper.selectBySaleId(COMPANY_ID, REF_SALE_ID))
                .thenReturn(Collections.singletonList(saleItem));
        when(batchMapper.updateRemaining(anyLong(), anyLong(), anyInt())).thenReturn(1);
        when(skuMapper.selectById(SKU_ID, COMPANY_ID)).thenReturn(sku);
        when(skuMapper.updateByIdWithVersion(any())).thenReturn(1);
        when(inventoryLogMapper.insert(any())).thenReturn(1);

        service.confirm(RETURN_ID, COMPANY_ID, OPERATOR_ID);

        ArgumentCaptor<OpcErpInventoryLog> logCaptor = ArgumentCaptor.forClass(OpcErpInventoryLog.class);
        verify(inventoryLogMapper).insert(logCaptor.capture());
        OpcErpInventoryLog logRow = logCaptor.getValue();
        assertThat(logRow.getType()).isEqualTo(ErpInventoryLogType.SALES_RETURN_IN.getCode());
        assertThat(logRow.getChange()).isEqualTo(1);  // +1
        assertThat(logRow.getRefType()).isEqualTo("RETURN");
        assertThat(logRow.getRefId()).isEqualTo(RETURN_ID);
        assertThat(logRow.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(logRow.getRemark()).contains("RT-20260912-0003");
        assertThat(logRow.getCreatedBy()).isEqualTo(OPERATOR_ID);
    }

    /** Test 7 */
    @Test
    @DisplayName("confirm - 已 CONFIRMED 状态抛异常(状态机保护)")
    void confirm_alreadyConfirmed_throws() {
        OpcErpReturn confirmedReturn = OpcErpReturn.builder()
                .id(RETURN_ID).companyId(COMPANY_ID)
                .returnType(ErpReturnType.SALES_RETURN.getCode())
                .status(ErpReturnStatus.CONFIRMED.getCode())
                .build();
        when(returnMapper.selectById(RETURN_ID, COMPANY_ID)).thenReturn(confirmedReturn);

        assertThatThrownBy(() -> service.confirm(RETURN_ID, COMPANY_ID, OPERATOR_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期望 DRAFT")
                .hasMessageContaining("CONFIRMED");
        verify(batchMapper, never()).updateRemaining(anyLong(), anyLong(), anyInt());
        verify(skuMapper, never()).updateByIdWithVersion(any(OpcErpProductSku.class));
        verify(inventoryLogMapper, never()).insert(any(OpcErpInventoryLog.class));
    }

    // ============================================================
    // cancel (1)
    // ============================================================

    /** Test 8 */
    @Test
    @DisplayName("cancel - DRAFT 状态允许取消")
    void cancel_draftAllowed() {
        OpcErpReturn draftReturn = OpcErpReturn.builder()
                .id(RETURN_ID).companyId(COMPANY_ID)
                .returnNo("RT-20260912-0001")
                .status(ErpReturnStatus.DRAFT.getCode())
                .build();
        when(returnMapper.selectById(RETURN_ID, COMPANY_ID)).thenReturn(draftReturn);

        service.cancel(RETURN_ID, COMPANY_ID, OPERATOR_ID);

        ArgumentCaptor<OpcErpReturn> captor = ArgumentCaptor.forClass(OpcErpReturn.class);
        verify(returnMapper).updateById(captor.capture());
        OpcErpReturn updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(ErpReturnStatus.CANCELLED.getCode());
        // DRAFT 取消不动库存/批次/流水
        verify(batchMapper, never()).updateRemaining(anyLong(), anyLong(), anyInt());
        verify(skuMapper, never()).updateByIdWithVersion(any(OpcErpProductSku.class));
        verify(inventoryLogMapper, never()).insert(any(OpcErpInventoryLog.class));
    }

    // ============================================================
    // detail (1)
    // ============================================================

    /** Test 9 */
    @Test
    @DisplayName("detail - 找到退货单")
    void detail_found() {
        OpcErpReturn existing = OpcErpReturn.builder()
                .id(RETURN_ID).companyId(COMPANY_ID)
                .returnNo("RT-20260912-0001")
                .returnType(ErpReturnType.SALES_RETURN.getCode())
                .refId(REF_SALE_ID)
                .status(ErpReturnStatus.COMPLETED.getCode())
                .build();
        when(returnMapper.selectById(RETURN_ID, COMPANY_ID)).thenReturn(existing);

        OpcErpReturn result = service.detail(RETURN_ID, COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.getReturnNo()).isEqualTo("RT-20260912-0001");
        assertThat(result.getStatus()).isEqualTo(ErpReturnStatus.COMPLETED.getCode());
    }

    // ============================================================
    // list (1)
    // ============================================================

    /** Test 10 */
    @Test
    @DisplayName("list - 按 returnType 过滤")
    void list_filterByType() {
        OpcErpReturn r1 = OpcErpReturn.builder()
                .id(1L).companyId(COMPANY_ID).returnType(ErpReturnType.SALES_RETURN.getCode())
                .status(ErpReturnStatus.COMPLETED.getCode()).build();
        when(returnMapper.selectList(eq(COMPANY_ID), eq(ErpReturnType.SALES_RETURN.getCode()), any(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(r1));

        var result = service.list(COMPANY_ID, ErpReturnType.SALES_RETURN.getCode(), null, 0, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReturnType()).isEqualTo(ErpReturnType.SALES_RETURN.getCode());
    }
}