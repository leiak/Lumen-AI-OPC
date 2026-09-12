package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OpcErpInventoryLogMapper {
    int insert(OpcErpInventoryLog log);

    /**
     * 按 sku 查询库存流水（sinceDate 之后）
     */
    List<OpcErpInventoryLog> selectBySkuId(@Param("companyId") Long companyId,
                                           @Param("skuId") Long skuId,
                                           @Param("sinceDate") LocalDateTime sinceDate,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    int countBySkuId(@Param("companyId") Long companyId,
                     @Param("skuId") Long skuId,
                     @Param("sinceDate") LocalDateTime sinceDate);
}
