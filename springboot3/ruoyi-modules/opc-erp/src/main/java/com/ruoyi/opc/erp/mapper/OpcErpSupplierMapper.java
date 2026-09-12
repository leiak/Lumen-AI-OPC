package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpSupplier;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpSupplierMapper {
    int insert(OpcErpSupplier supplier);

    int updateById(OpcErpSupplier supplier);

    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcErpSupplier selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcErpSupplier> selectList(@Param("companyId") Long companyId,
                                    @Param("level") String level,
                                    @Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId,
                  @Param("level") String level,
                  @Param("keyword") String keyword);

    OpcErpSupplier selectByName(@Param("companyId") Long companyId,
                                @Param("name") String name);

    List<OpcErpSupplier> selectListByLevel(@Param("companyId") Long companyId,
                                           @Param("level") String level);
}
