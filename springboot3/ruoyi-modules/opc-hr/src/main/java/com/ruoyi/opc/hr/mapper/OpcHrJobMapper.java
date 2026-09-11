package com.ruoyi.opc.hr.mapper;

import com.ruoyi.opc.hr.domain.OpcHrJob;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcHrJobMapper {
    int insert(OpcHrJob job);

    int updateById(OpcHrJob job);

    /** 仅 DRAFT 状态可删除(由 mapper xml 的 WHERE 子句保证) */
    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcHrJob selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcHrJob> selectList(@Param("companyId") Long companyId,
                              @Param("status") String status,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId, @Param("status") String status);
}
