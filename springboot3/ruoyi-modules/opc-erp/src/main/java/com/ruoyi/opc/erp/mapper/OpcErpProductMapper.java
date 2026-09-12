package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpProductMapper {
    int insert(OpcErpProduct product);

    int updateById(OpcErpProduct product);

    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcErpProduct selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcErpProduct> selectList(@Param("companyId") Long companyId,
                                   @Param("keyword") String keyword,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId,
                  @Param("keyword") String keyword,
                  @Param("status") String status);

    List<OpcErpProduct> selectListByCategory(@Param("companyId") Long companyId,
                                             @Param("category") String category,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);
}
