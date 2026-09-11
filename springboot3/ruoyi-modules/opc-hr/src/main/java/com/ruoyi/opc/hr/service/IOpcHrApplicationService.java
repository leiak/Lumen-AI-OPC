package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.dto.OpcHrApplicationDto;

import java.util.List;

/**
 * 投递 (Application) 服务接口
 * 5 endpoints: create / list / detail / transitionStatus / score
 */
public interface IOpcHrApplicationService {

    /**
     * 创建投递(默认 status=NEW, channel=MANUAL, score=0)
     *
     * @return 新投递的雪花 ID
     */
    Long create(OpcHrApplicationDto dto);

    /**
     * 详情(companyId 隔离)
     */
    OpcHrApplication detail(Long id, Long companyId);

    /**
     * 列表(按 jobId / candidateId / status 过滤,可选)
     */
    List<OpcHrApplication> list(Long companyId, Long jobId, Long candidateId, String status,
                                 int offset, int limit);

    /**
     * 列表总数(配套 list)
     */
    int countList(Long companyId, Long jobId, Long candidateId, String status);

    /**
     * 推进状态(状态机校验:HrApplicationStatus.canTransitionTo)
     */
    void transitionStatus(Long id, Long companyId, String targetStatus);

    /**
     * LLM 评分(占位,Task 7 接入 opc-ai-core HttpLlmClient)
     */
    void score(Long id, Long companyId);
}