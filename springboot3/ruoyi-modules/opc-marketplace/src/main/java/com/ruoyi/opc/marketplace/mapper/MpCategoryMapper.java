package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}