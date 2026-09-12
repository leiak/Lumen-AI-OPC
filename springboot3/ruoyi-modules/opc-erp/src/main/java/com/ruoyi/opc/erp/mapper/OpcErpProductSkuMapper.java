package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpProductSkuMapper {
    int insert(OpcErpProductSku sku);

    int updateById(OpcErpProductSku sku);

    /** 乐观锁更新: WHERE id=? AND version=? */
    int updateByIdWithVersion(OpcErpProductSku sku);

    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcErpProductSku selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcErpProductSku selectBySkuCode(@Param("companyId") Long companyId,
                                     @Param("skuCode") String skuCode);

    List<OpcErpProductSku> selectByProductId(@Param("companyId") Long companyId,
                                             @Param("productId") Long productId);

    /** 公司下所有 SKU（Task 7 Quartz Job 用：扫所有 SKU 落每日快照） */
    List<OpcErpProductSku> selectByCompany(@Param("companyId") Long companyId);

    /** 低库存预警: stock < threshold */
    List<OpcErpProductSku> selectLowStock(@Param("companyId") Long companyId,
                                          @Param("threshold") Integer threshold);

    int countList(@Param("companyId") Long companyId,
                  @Param("productId") Long productId);
}
