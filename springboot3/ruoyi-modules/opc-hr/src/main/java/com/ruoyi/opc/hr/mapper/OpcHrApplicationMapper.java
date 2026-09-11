package com.ruoyi.opc.hr.mapper;

import com.ruoyi.opc.hr.domain.OpcHrApplication;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface OpcHrApplicationMapper {
    int insert(OpcHrApplication application);

    int updateById(OpcHrApplication application);

    OpcHrApplication selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcHrApplication> selectList(@Param("companyId") Long companyId,
                                      @Param("jobId") Long jobId,
                                      @Param("candidateId") Long candidateId,
                                      @Param("status") String status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId,
                  @Param("jobId") Long jobId,
                  @Param("candidateId") Long candidateId,
                  @Param("status") String status);

    /** 仪表盘聚合:按 status 分组计数 */
    List<Map<String, Object>> countByStatusForDashboard(@Param("companyId") Long companyId,
                                                        @Param("sinceDays") int sinceDays);

    /** 仪表盘:已 ACCEPTED 的 offer 平均招聘时长(天) */
    Double avgHireDaysForAcceptedOffers(@Param("companyId") Long companyId,
                                        @Param("sinceDays") int sinceDays);
}
