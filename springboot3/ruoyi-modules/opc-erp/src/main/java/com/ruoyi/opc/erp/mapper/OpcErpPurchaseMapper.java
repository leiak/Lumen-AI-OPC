package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpPurchase;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpPurchaseMapper {
    int insert(OpcErpPurchase purchase);

    int updateById(OpcErpPurchase purchase);

    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcErpPurchase selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcErpPurchase> selectList(@Param("companyId") Long companyId,
                                    @Param("status") String status,
                                    @Param("supplierId") Long supplierId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId,
                  @Param("status") String status,
                  @Param("supplierId") Long supplierId);

    OpcErpPurchase selectByPurchaseNo(@Param("companyId") Long companyId,
                                      @Param("purchaseNo") String purchaseNo);

    List<OpcErpPurchase> selectListByStatus(@Param("companyId") Long companyId,
                                            @Param("status") String status);

    /**
     * 仅更新 status 字段（状态机迁移用）
     */
    int updateStatus(@Param("id") Long id,
                     @Param("companyId") Long companyId,
                     @Param("status") String status);
}
