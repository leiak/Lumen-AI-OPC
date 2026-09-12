package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpSale;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpSaleMapper {
    int insert(OpcErpSale sale);

    int updateById(OpcErpSale sale);

    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcErpSale selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcErpSale> selectList(@Param("companyId") Long companyId,
                                @Param("status") String status,
                                @Param("customerName") String customerName,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId,
                  @Param("status") String status,
                  @Param("customerName") String customerName);

    OpcErpSale selectBySaleNo(@Param("companyId") Long companyId,
                              @Param("saleNo") String saleNo);

    List<OpcErpSale> selectListByStatus(@Param("companyId") Long companyId,
                                        @Param("status") String status);

    int updateStatus(@Param("id") Long id,
                     @Param("companyId") Long companyId,
                     @Param("status") String status);
}
