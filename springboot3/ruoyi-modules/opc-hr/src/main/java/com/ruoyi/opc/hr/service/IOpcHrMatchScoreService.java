package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.domain.OpcHrMatchScore;

import java.util.List;

/**
 * HR 匹配打分 (MatchScore) 缓存服务接口
 * 内部辅助接口 — 无 REST endpoint。LLM 评分后写入,避免重复调用。
 */
public interface IOpcHrMatchScoreService {

    /**
     * Upsert — 已存在 (companyId, jobId, candidateId) 则更新 score / reason / computedAt
     */
    void upsert(OpcHrMatchScore score);

    /**
     * 按 job 维度查询该岗位所有候选人的匹配分
     */
    List<OpcHrMatchScore> listByJob(Long companyId, Long jobId);

    /**
     * 查询单个 (job, candidate) 的最新匹配分,无则 null
     */
    OpcHrMatchScore getByJobCandidate(Long companyId, Long jobId, Long candidateId);
}
