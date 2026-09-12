package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpSupplier;
import com.ruoyi.opc.erp.dto.OpcErpSupplierDto;
import com.ruoyi.opc.erp.enums.ErpSupplierLevel;
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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcErpSupplierServiceImpl 单测 (8 cases)
 *
 * <p>覆盖:
 * <ul>
 *   <li>create (2): 成功创建 / 重名抛异常</li>
 *   <li>update (1): 部分字段更新</li>
 *   <li>delete (3): NORMAL 无采购单可删 / 有关联采购单拒绝 / PREFERRED 保护</li>
 *   <li>detail (1)</li>
 *   <li>list (1): level 过滤</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpSupplierService 单测 (8 cases)")
class OpcErpSupplierServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long OPERATOR_ID = 100L;
    private static final Long SUPPLIER_ID = 200L;

    @Mock
    private OpcErpSupplierMapper supplierMapper;
    @Mock
    private OpcErpPurchaseMapper purchaseMapper;

    @InjectMocks
    private OpcErpSupplierServiceImpl service;

    private OpcErpSupplierDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcErpSupplierDto.builder()
                .companyId(COMPANY_ID)
                .name("测试供应商")
                .contact("张总")
                .phone("13800001234")
                .email("test@example.com")
                .address("上海市浦东新区")
                .level(ErpSupplierLevel.NORMAL.getCode())
                .status("ACTIVE")
                .build();
        when(supplierMapper.insert(any(OpcErpSupplier.class))).thenReturn(1);
    }

    // ============================================================
    // create (2)
    // ============================================================

    /** Test 1 */
    @Test
    @DisplayName("create - 成功创建供应商")
    void create_success() {
        when(supplierMapper.selectByName(COMPANY_ID, "测试供应商")).thenReturn(null);

        Long id = service.create(OPERATOR_ID, sampleDto);

        assertThat(id).isNotNull().isPositive();

        ArgumentCaptor<OpcErpSupplier> captor = ArgumentCaptor.forClass(OpcErpSupplier.class);
        verify(supplierMapper).insert(captor.capture());
        OpcErpSupplier saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("测试供应商");
        assertThat(saved.getLevel()).isEqualTo(ErpSupplierLevel.NORMAL.getCode());
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreatedBy()).isEqualTo(OPERATOR_ID);
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 重复 name 抛异常")
    void create_duplicateName_throws() {
        OpcErpSupplier existing = OpcErpSupplier.builder()
                .id(99L).companyId(COMPANY_ID).name("测试供应商").build();
        when(supplierMapper.selectByName(COMPANY_ID, "测试供应商")).thenReturn(existing);

        assertThatThrownBy(() -> service.create(OPERATOR_ID, sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("供应商名称已存在");
        verify(supplierMapper, never()).insert(any(OpcErpSupplier.class));
    }

    // ============================================================
    // update (1)
    // ============================================================

    /** Test 3 */
    @Test
    @DisplayName("update - 部分字段更新(只改 contact/phone/email/address)")
    void update_draft_changesContact() {
        OpcErpSupplier existing = OpcErpSupplier.builder()
                .id(SUPPLIER_ID).companyId(COMPANY_ID).name("测试供应商")
                .contact("旧联系人").phone("1111111")
                .level(ErpSupplierLevel.NORMAL.getCode()).status("ACTIVE").build();
        when(supplierMapper.selectById(SUPPLIER_ID, COMPANY_ID)).thenReturn(existing);
        when(supplierMapper.updateById(any(OpcErpSupplier.class))).thenReturn(1);

        OpcErpSupplierDto updateDto = OpcErpSupplierDto.builder()
                .companyId(COMPANY_ID)
                .contact("新联系人")
                .phone("13800009999")
                .build();

        int affected = service.update(SUPPLIER_ID, COMPANY_ID, updateDto);

        assertThat(affected).isEqualTo(1);
        ArgumentCaptor<OpcErpSupplier> captor = ArgumentCaptor.forClass(OpcErpSupplier.class);
        verify(supplierMapper).updateById(captor.capture());
        OpcErpSupplier updated = captor.getValue();
        assertThat(updated.getContact()).isEqualTo("新联系人");
        assertThat(updated.getPhone()).isEqualTo("13800009999");
        // level 没改,保持旧值
        assertThat(updated.getLevel()).isEqualTo(ErpSupplierLevel.NORMAL.getCode());
    }

    // ============================================================
    // delete (3)
    // ============================================================

    /** Test 4 */
    @Test
    @DisplayName("delete - NORMAL + 无采购单允许删除")
    void delete_noPurchaseAllowed() {
        OpcErpSupplier existing = OpcErpSupplier.builder()
                .id(SUPPLIER_ID).companyId(COMPANY_ID).name("测试供应商")
                .level(ErpSupplierLevel.NORMAL.getCode()).build();
        when(supplierMapper.selectById(SUPPLIER_ID, COMPANY_ID)).thenReturn(existing);
        when(purchaseMapper.countBySupplier(COMPANY_ID, SUPPLIER_ID)).thenReturn(0);
        when(supplierMapper.deleteById(SUPPLIER_ID, COMPANY_ID)).thenReturn(1);

        service.delete(SUPPLIER_ID, COMPANY_ID);

        verify(supplierMapper).deleteById(SUPPLIER_ID, COMPANY_ID);
    }

    /** Test 5 */
    @Test
    @DisplayName("delete - 有关联采购单拒绝")
    void delete_hasPurchase_throws() {
        OpcErpSupplier existing = OpcErpSupplier.builder()
                .id(SUPPLIER_ID).companyId(COMPANY_ID).name("测试供应商")
                .level(ErpSupplierLevel.NORMAL.getCode()).build();
        when(supplierMapper.selectById(SUPPLIER_ID, COMPANY_ID)).thenReturn(existing);
        when(purchaseMapper.countBySupplier(COMPANY_ID, SUPPLIER_ID)).thenReturn(3);

        assertThatThrownBy(() -> service.delete(SUPPLIER_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已存在关联采购单");
        verify(supplierMapper, never()).deleteById(anyLong(), anyLong());
    }

    /** Test 6 */
    @Test
    @DisplayName("delete - PREFERRED 状态拒绝")
    void delete_preferredBlocked_throws() {
        OpcErpSupplier existing = OpcErpSupplier.builder()
                .id(SUPPLIER_ID).companyId(COMPANY_ID).name("测试供应商")
                .level(ErpSupplierLevel.PREFERRED.getCode()).build();
        when(supplierMapper.selectById(SUPPLIER_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> service.delete(SUPPLIER_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PREFERRED")
                .hasMessageContaining("仅 NORMAL 可删");
        verify(purchaseMapper, never()).countBySupplier(anyLong(), anyLong());
        verify(supplierMapper, never()).deleteById(anyLong(), anyLong());
    }

    // ============================================================
    // detail (1)
    // ============================================================

    /** Test 7 */
    @Test
    @DisplayName("detail - 返回正确供应商")
    void detail_found() {
        OpcErpSupplier existing = OpcErpSupplier.builder()
                .id(SUPPLIER_ID).companyId(COMPANY_ID).name("测试供应商")
                .level(ErpSupplierLevel.PREFERRED.getCode()).build();
        when(supplierMapper.selectById(SUPPLIER_ID, COMPANY_ID)).thenReturn(existing);

        OpcErpSupplier result = service.detail(SUPPLIER_ID, COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("测试供应商");
        assertThat(result.getLevel()).isEqualTo(ErpSupplierLevel.PREFERRED.getCode());
    }

    // ============================================================
    // list (1)
    // ============================================================

    /** Test 8 */
    @Test
    @DisplayName("list - 带 level 过滤")
    void list_withLevelFilter() {
        OpcErpSupplier s1 = OpcErpSupplier.builder()
                .id(1L).companyId(COMPANY_ID).name("供应商A")
                .level(ErpSupplierLevel.NORMAL.getCode()).build();
        OpcErpSupplier s2 = OpcErpSupplier.builder()
                .id(2L).companyId(COMPANY_ID).name("供应商B")
                .level(ErpSupplierLevel.NORMAL.getCode()).build();
        when(supplierMapper.selectList(eq(COMPANY_ID), eq(ErpSupplierLevel.NORMAL.getCode()), any(), anyInt(), anyInt()))
                .thenReturn(java.util.Arrays.asList(s1, s2));

        List<OpcErpSupplier> result = service.list(COMPANY_ID, ErpSupplierLevel.NORMAL.getCode(), 0, 100);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("供应商A");
        assertThat(result.get(1).getName()).isEqualTo("供应商B");
    }
}