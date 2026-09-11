package com.ruoyi.opc.hr.mapper;

import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcHrCandidateMapper {
    int insert(OpcHrCandidate candidate);

    int updateById(OpcHrCandidate candidate);

    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcHrCandidate selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcHrCandidate selectByEmail(@Param("companyId") Long companyId,
                                @Param("email") String email);

    List<OpcHrCandidate> selectList(@Param("companyId") Long companyId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId);
}
