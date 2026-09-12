package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpSaleItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpSaleItemMapper {
    int insert(OpcErpSaleItem item);

    int insertBatch(@Param("list") List<OpcErpSaleItem> list);

    List<OpcErpSaleItem> selectBySaleId(@Param("companyId") Long companyId,
                                        @Param("saleId") Long saleId);

    /**
     * 按 sale_id 删除明细(confirm 时清空 DRAFT 旧明细,重写为含 batchId 的新明细)
     */
    int deleteBySaleId(@Param("companyId") Long companyId, @Param("saleId") Long saleId);
}
