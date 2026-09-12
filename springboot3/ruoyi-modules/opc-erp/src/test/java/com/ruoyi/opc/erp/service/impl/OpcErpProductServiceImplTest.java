package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpProduct;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.dto.OpcErpProductDto;
import com.ruoyi.opc.erp.mapper.OpcErpProductMapper;
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
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OpcErpProductServiceImpl 单测 (15 cases)
 *
 * <p>覆盖: create(7)/ update(2)/ delete(2)/ detail(2)/ list(1)/ autoCategory(1)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpProductService 单测 (15 cases)")
class OpcErpProductServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long PRODUCT_ID = 1001L;
    private static final String SKU_ROOT = "T001";
    /** specAttrs 2 colors × 2 sizes → 4 SKUs */
    private static final String SPECS_CARTESIAN = "[{\"name\":\"颜色\",\"values\":[\"黑\",\"白\"]},"
            + "{\"name\":\"尺码\",\"values\":[\"M\",\"L\"]}]";
    /** specAttrs 1 attr × 3 values → 3 SKUs */
    private static final String SPECS_LINEAR = "[{\"name\":\"颜色\",\"values\":[\"黑\",\"白\",\"红\"]}]";

    @Mock
    private OpcErpProductMapper productMapper;

    @Mock
    private OpcErpProductSkuMapper skuMapper;

    @InjectMocks
    private OpcErpProductServiceImpl productService;

    private OpcErpProductDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcErpProductDto.builder()
                .companyId(COMPANY_ID)
                .skuRoot(SKU_ROOT)
                .name("测试 T 恤")
                .category("服装")
                .brand("OPC")
                .unit("件")
                .description("100% 纯棉")
                .specAttrs(SPECS_CARTESIAN)
                .build();
    }

    // ============================================================
    // create (7)
    // ============================================================

    /** Test 1 */
    @Test
    @DisplayName("create - 2x2 笛卡尔积生成 4 个 SKU")
    void create_success_generatesCartesianProduct() {
        when(productMapper.insert(any(OpcErpProduct.class))).thenReturn(1);
        when(skuMapper.insert(any(OpcErpProductSku.class))).thenReturn(1);

        Long id = productService.create(sampleDto, 7L);

        assertThat(id).isNotNull().isPositive();
        // 验证 product 插入 1 次
        ArgumentCaptor<OpcErpProduct> productCaptor = ArgumentCaptor.forClass(OpcErpProduct.class);
        verify(productMapper).insert(productCaptor.capture());
        OpcErpProduct saved = productCaptor.getValue();
        assertThat(saved.getSkuRoot()).isEqualTo(SKU_ROOT);
        assertThat(saved.getName()).isEqualTo("测试 T 恤");
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");

        // 验证 4 个 SKU 被插入
        ArgumentCaptor<OpcErpProductSku> skuCaptor = ArgumentCaptor.forClass(OpcErpProductSku.class);
        verify(skuMapper, times(4)).insert(skuCaptor.capture());
        List<OpcErpProductSku> skus = skuCaptor.getAllValues();
        assertThat(skus).hasSize(4);
        // 检查 SKU code 拼接
        List<String> skuCodes = skus.stream().map(OpcErpProductSku::getSkuCode).sorted().collect(java.util.stream.Collectors.toList());
        assertThat(skuCodes).containsExactlyInAnyOrder(
                "T001-黑-L", "T001-黑-M", "T001-白-L", "T001-白-M");
        // 所有 SKU 的 stock = 0, threshold = 10, version = 0
        skus.forEach(s -> {
            assertThat(s.getStock()).isZero();
            assertThat(s.getThreshold()).isEqualTo(10);
            assertThat(s.getVersion()).isZero();
            assertThat(s.getProductId()).isEqualTo(saved.getId());
        });
    }

    /** Test 2 */
    @Test
    @DisplayName("create - 单规格 3 值生成 3 个 SKU")
    void create_singleSpec_generatesLinearSku() {
        sampleDto.setSpecAttrs(SPECS_LINEAR);
        when(productMapper.insert(any(OpcErpProduct.class))).thenReturn(1);
        when(skuMapper.insert(any(OpcErpProductSku.class))).thenReturn(1);

        Long id = productService.create(sampleDto, 0L);

        assertThat(id).isNotNull();
        verify(skuMapper, times(3)).insert(any(OpcErpProductSku.class));
        ArgumentCaptor<OpcErpProductSku> skuCaptor = ArgumentCaptor.forClass(OpcErpProductSku.class);
        verify(skuMapper, times(3)).insert(skuCaptor.capture());
        List<String> codes = skuCaptor.getAllValues().stream()
                .map(OpcErpProductSku::getSkuCode).sorted().collect(java.util.stream.Collectors.toList());
        assertThat(codes).containsExactly("T001-白", "T001-红", "T001-黑");
    }

    /** Test 3 */
    @Test
    @DisplayName("create - 空 specAttrs 抛异常")
    void create_emptySpecAttrs_throws() {
        sampleDto.setSpecAttrs("[]");
        assertThatThrownBy(() -> productService.create(sampleDto, 0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("specAttrs");
        verifyNoInteractions(productMapper);
        verifyNoInteractions(skuMapper);
    }

    /** Test 4 */
    @Test
    @DisplayName("create - 缺 skuRoot 抛异常")
    void create_missingSkuRoot_throws() {
        sampleDto.setSkuRoot(null);
        assertThatThrownBy(() -> productService.create(sampleDto, 0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("skuRoot");
        verifyNoInteractions(productMapper);
    }

    /** Test 5 */
    @Test
    @DisplayName("create - 缺 name 抛异常")
    void create_missingName_throws() {
        sampleDto.setName(null);
        assertThatThrownBy(() -> productService.create(sampleDto, 0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("name");
        verifyNoInteractions(productMapper);
    }

    /** Test 6 */
    @Test
    @DisplayName("create - 缺 companyId 抛异常")
    void create_missingCompanyId_throws() {
        sampleDto.setCompanyId(null);
        assertThatThrownBy(() -> productService.create(sampleDto, 0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
        verifyNoInteractions(productMapper);
    }

    /** Test 7 */
    @Test
    @DisplayName("create - 同公司 skuRoot 重复抛业务异常")
    void create_duplicateSkuRootInSameCompany_throws() {
        when(productMapper.insert(any(OpcErpProduct.class)))
                .thenThrow(new DuplicateKeyException("uk_company_sku_root"));

        assertThatThrownBy(() -> productService.create(sampleDto, 0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("skuRoot 已存在");
        verify(skuMapper, never()).insert(any(OpcErpProductSku.class));
    }

    // ============================================================
    // update (2)
    // ============================================================

    /** Test 8 */
    @Test
    @DisplayName("update - 成功修改商品名称/分类")
    void update_draft_changesName() {
        OpcErpProduct existing = OpcErpProduct.builder()
                .id(PRODUCT_ID).companyId(COMPANY_ID).skuRoot(SKU_ROOT)
                .name("Old Name").category("Old Cat").status("ACTIVE").build();
        when(productMapper.selectById(PRODUCT_ID, COMPANY_ID)).thenReturn(existing);
        when(productMapper.updateById(any(OpcErpProduct.class))).thenReturn(1);

        int rows = productService.update(PRODUCT_ID, COMPANY_ID,
                OpcErpProductDto.builder().name("New Name").category("新分类").build());

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<OpcErpProduct> captor = ArgumentCaptor.forClass(OpcErpProduct.class);
        verify(productMapper).updateById(captor.capture());
        OpcErpProduct updated = captor.getValue();
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getCategory()).isEqualTo("新分类");
        assertThat(updated.getSkuRoot()).isEqualTo(SKU_ROOT);  // 字段未在 DTO 中 → 不变
    }

    /** Test 9 */
    @Test
    @DisplayName("update - 商品不存在抛异常")
    void update_nonExistent_throws() {
        when(productMapper.selectById(999L, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() ->
                productService.update(999L, COMPANY_ID, OpcErpProductDto.builder().name("x").build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("商品不存在");
        verify(productMapper, never()).updateById(any(OpcErpProduct.class));
    }

    // ============================================================
    // delete (2)
    // ============================================================

    /** Test 10 */
    @Test
    @DisplayName("delete - 所有 SKU 库存为 0 时可删除")
    void delete_noStockAllowed() {
        OpcErpProduct existing = OpcErpProduct.builder()
                .id(PRODUCT_ID).companyId(COMPANY_ID).skuRoot(SKU_ROOT).build();
        List<OpcErpProductSku> skus = Arrays.asList(
                OpcErpProductSku.builder().id(1L).companyId(COMPANY_ID).skuCode("T001-黑-M").stock(0).build(),
                OpcErpProductSku.builder().id(2L).companyId(COMPANY_ID).skuCode("T001-白-L").stock(0).build()
        );
        when(productMapper.selectById(PRODUCT_ID, COMPANY_ID)).thenReturn(existing);
        when(skuMapper.selectByProductId(COMPANY_ID, PRODUCT_ID)).thenReturn(skus);
        when(skuMapper.deleteById(anyLong(), eq(COMPANY_ID))).thenReturn(1);
        when(productMapper.deleteById(PRODUCT_ID, COMPANY_ID)).thenReturn(1);

        productService.delete(PRODUCT_ID, COMPANY_ID);

        verify(skuMapper, times(2)).deleteById(anyLong(), eq(COMPANY_ID));
        verify(productMapper).deleteById(PRODUCT_ID, COMPANY_ID);
    }

    /** Test 11 */
    @Test
    @DisplayName("delete - 任一 SKU 库存 > 0 时拒绝删除")
    void delete_hasStock_throws() {
        OpcErpProduct existing = OpcErpProduct.builder()
                .id(PRODUCT_ID).companyId(COMPANY_ID).skuRoot(SKU_ROOT).build();
        List<OpcErpProductSku> skus = Arrays.asList(
                OpcErpProductSku.builder().id(1L).companyId(COMPANY_ID).skuCode("T001-黑-M").stock(0).build(),
                OpcErpProductSku.builder().id(2L).companyId(COMPANY_ID).skuCode("T001-白-L").stock(5).build()
        );
        when(productMapper.selectById(PRODUCT_ID, COMPANY_ID)).thenReturn(existing);
        when(skuMapper.selectByProductId(COMPANY_ID, PRODUCT_ID)).thenReturn(skus);

        assertThatThrownBy(() -> productService.delete(PRODUCT_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存在库存");
        verify(skuMapper, never()).deleteById(anyLong(), anyLong());
        verify(productMapper, never()).deleteById(anyLong(), anyLong());
    }

    // ============================================================
    // detail (2)
    // ============================================================

    /** Test 12 */
    @Test
    @DisplayName("detail - 找到返回商品")
    void detail_found() {
        OpcErpProduct existing = OpcErpProduct.builder()
                .id(PRODUCT_ID).companyId(COMPANY_ID).skuRoot(SKU_ROOT)
                .name("Test").status("ACTIVE").build();
        when(productMapper.selectById(PRODUCT_ID, COMPANY_ID)).thenReturn(existing);

        OpcErpProduct result = productService.detail(PRODUCT_ID, COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.getSkuRoot()).isEqualTo(SKU_ROOT);
        assertThat(result.getName()).isEqualTo("Test");
    }

    /** Test 13 */
    @Test
    @DisplayName("detail - 找不到抛异常")
    void detail_notFound_throws() {
        when(productMapper.selectById(999L, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> productService.detail(999L, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("商品不存在");
    }

    // ============================================================
    // list (1)
    // ============================================================

    /** Test 14 */
    @Test
    @DisplayName("list - 按 category 过滤返回列表")
    void list_withCategoryFilter() {
        OpcErpProduct p1 = OpcErpProduct.builder().id(1L).companyId(COMPANY_ID).category("服装").build();
        when(productMapper.selectListByCategory(eq(COMPANY_ID), eq("服装"), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(p1));

        List<OpcErpProduct> rows = productService.list(COMPANY_ID, "服装", 0, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCategory()).isEqualTo("服装");
        verify(productMapper).selectListByCategory(COMPANY_ID, "服装", 0, 100);
        verify(productMapper, never()).selectList(anyLong(), any(), any(), anyInt(), anyInt());
    }

    // ============================================================
    // autoCategory (1)
    // ============================================================

    /** Test 15 */
    @Test
    @DisplayName("autoCategory - 占位实现根据关键词返回分类")
    void autoCategory_llmSuccess_returnsCategory() {
        // 占位实现基于 name 启发式分类
        String cat1 = productService.autoCategory("小米手机", "性价比");
        String cat2 = productService.autoCategory("纯棉 T 恤", "舒适透气");
        String cat3 = productService.autoCategory("新鲜水果", "产地直发");

        assertThat(cat1).isEqualTo("电子产品");
        assertThat(cat2).isEqualTo("服装鞋帽");
        assertThat(cat3).isEqualTo("食品饮料");
        // null name 应返回 null
        assertThat(productService.autoCategory(null, "x")).isNull();
    }
}