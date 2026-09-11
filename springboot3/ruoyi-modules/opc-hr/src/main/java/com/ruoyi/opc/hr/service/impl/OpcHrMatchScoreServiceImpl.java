package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.hr.domain.OpcHrMatchScore;
import com.ruoyi.opc.hr.mapper.OpcHrMatchScoreMapper;
import com.ruoyi.opc.hr.service.IOpcHrMatchScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HR 匹配打分缓存 — 内部辅助服务,无 REST。
 * 复用 opc_hr_match_score.uk_job_candidate 唯一键,mapper.insertOrUpdate
 * 用 ON DUPLICATE KEY UPDATE 单 SQL 同时处理 insert + update。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcHrMatchScoreServiceImpl implements IOpcHrMatchScoreService {

    private final OpcHrMatchScoreMapper matchScoreMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(OpcHrMatchScore score) {
        if (score == null) {
            return;
        }
        OpcHrMatchScore existing = matchScoreMapper.selectByJobCandidate(
                score.getCompanyId(), score.getJobId(), score.getCandidateId());
        if (existing == null) {
            score.setId(SnowflakeIdGenerator.nextId());
            score.setComputedAt(LocalDateTime.now());
            matchScoreMapper.insertOrUpdate(score);
            log.info("新增 match score id={} job={} candidate={} score={}",
                    score.getId(), score.getJobId(), score.getCandidateId(), score.getScore());
        } else {
            existing.setScore(score.getScore());
            existing.setReason(score.getReason());
            existing.setComputedAt(LocalDateTime.now());
            matchScoreMapper.insertOrUpdate(existing);
            log.info("更新 match score id={} job={} candidate={} score={}",
                    existing.getId(), existing.getJobId(), existing.getCandidateId(), existing.getScore());
        }
    }

    @Override
    public List<OpcHrMatchScore> listByJob(Long companyId, Long jobId) {
        return matchScoreMapper.selectByJobId(companyId, jobId);
    }

    @Override
    public OpcHrMatchScore getByJobCandidate(Long companyId, Long jobId, Long candidateId) {
        return matchScoreMapper.selectByJobCandidate(companyId, jobId, candidateId);
    }
}
