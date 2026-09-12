package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpPurchaseItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpPurchaseItemMapper {
    int insert(OpcErpPurchaseItem item);

    /**
     * 批量插入
     */
    int insertBatch(@Param("list") List<OpcErpPurchaseItem> list);

    List<OpcErpPurchaseItem> selectByPurchaseId(@Param("companyId") Long companyId,
                                                @Param("purchaseId") Long purchaseId);

    int deleteByPurchaseId(@Param("companyId") Long companyId,
                           @Param("purchaseId") Long purchaseId);
}
