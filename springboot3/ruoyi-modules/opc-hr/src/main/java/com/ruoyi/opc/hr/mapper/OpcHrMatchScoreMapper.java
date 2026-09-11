package com.ruoyi.opc.hr.mapper;

import com.ruoyi.opc.hr.domain.OpcHrMatchScore;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcHrMatchScoreMapper {
    /** ON DUPLICATE KEY UPDATE 复用 uk_job_candidate 唯一键 */
    int insertOrUpdate(OpcHrMatchScore score);

    List<OpcHrMatchScore> selectByJobId(@Param("companyId") Long companyId,
                                        @Param("jobId") Long jobId);

    OpcHrMatchScore selectByJobCandidate(@Param("jobId") Long jobId,
                                         @Param("candidateId") Long candidateId);
}
