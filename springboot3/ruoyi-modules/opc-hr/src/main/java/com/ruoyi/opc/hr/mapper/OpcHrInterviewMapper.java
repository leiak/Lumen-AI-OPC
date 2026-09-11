package com.ruoyi.opc.hr.mapper;

import com.ruoyi.opc.hr.domain.OpcHrInterview;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcHrInterviewMapper {
    int insert(OpcHrInterview interview);

    int updateById(OpcHrInterview interview);

    OpcHrInterview selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcHrInterview> selectListByApplication(@Param("companyId") Long companyId,
                                                @Param("applicationId") Long applicationId);

    /** 该 application 已完成的面试轮次最大值(用于 round 自增) */
    Integer maxRoundByApplication(@Param("applicationId") Long applicationId);
}
