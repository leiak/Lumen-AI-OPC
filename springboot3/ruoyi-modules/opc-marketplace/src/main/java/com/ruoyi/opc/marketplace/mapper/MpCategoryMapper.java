package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * mp_category Mapper（只读，类目由种子数据维护）
 *
 * @author OAC
 */
@Mapper
public interface MpCategoryMapper {
    List<MpCategory> selectAll();

    List<MpCategory> selectByProductType(String productType);

    /** uk_cat_code(product_type, code) */
    MpCategory selectByCode(@Param("productType") String productType, @Param("code") String code);

    MpCategory selectById(Long id);

    /**
     * 批量按 (product_type, code) 查询类目,用于列表页一次性填充 {@code categoryName},避免 N+1.
     *
     * <p><b>调用方契约</b>: 传入空集合时<b>不要</b>调用本方法 —— MyBatis 会发出非法 SQL
     * {@code IN ()},需在 service 层短路. 参数为空时直接返回 {@code Collections.emptyList()}.
     *
     * @param productType AGENT 或 MODULE
     * @param codes       类目 code 集合,不可为空
     * @return 命中的类目列表(顺序与传入 codes 无关)
     */
    List<MpCategory> selectByCodes(@Param("productType") String productType,
                                   @Param("codes") Collection<String> codes);
}