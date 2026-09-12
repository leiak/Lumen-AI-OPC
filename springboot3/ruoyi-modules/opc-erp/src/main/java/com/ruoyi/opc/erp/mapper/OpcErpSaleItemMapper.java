package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpSaleItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpSaleItemMapper {
    int insert(OpcErpSaleItem item);

    int insertBatch(@Param("list") List<OpcErpSaleItem> list);

    List<OpcErpSaleItem> selectBySaleId(@Param("companyId") Long companyId,
                                        @Param("saleId") Long saleId);
}
