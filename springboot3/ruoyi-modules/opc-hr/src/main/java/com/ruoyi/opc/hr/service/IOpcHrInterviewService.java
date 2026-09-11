package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.domain.OpcHrInterview;
import com.ruoyi.opc.hr.dto.OpcHrInterviewDto;

import java.util.List;

/**
 * HR 面试 (Interview) 服务接口
 * 3 endpoints: create / list / updateFeedback
 */
public interface IOpcHrInterviewService {

    /**
     * 创建面试(round 自动自增:已有 maxRound + 1,否则 1)
     *
     * @return 新面试的雪花 ID
     */
    Long create(OpcHrInterviewDto dto);

    /**
     * 列表(按 applicationId 过滤,companyId 隔离)
     */
    List<OpcHrInterview> list(Long companyId, Long applicationId);

    /**
     * 更新反馈 + 结果(可单独更新 result 推进 application 状态)
     */
    int updateFeedback(Long id, Long companyId, String feedback, String result);
}
