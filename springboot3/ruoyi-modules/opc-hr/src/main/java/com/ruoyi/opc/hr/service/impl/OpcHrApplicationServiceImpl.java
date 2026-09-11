package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.dto.OpcHrApplicationDto;
import com.ruoyi.opc.hr.enums.HrApplicationStatus;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrCandidateMapper;
import com.ruoyi.opc.hr.mapper.OpcHrJobMapper;
import com.ruoyi.opc.hr.service.IOpcHrApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpcHrApplicationServiceImpl implements IOpcHrApplicationService {

    private final OpcHrApplicationMapper applicationMapper;
    private final OpcHrJobMapper jobMapper;
    private final OpcHrCandidateMapper candidateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpcHrApplicationDto dto) {
        if (dto.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (dto.getJobId() == null) {
            throw new ServiceException("jobId 不能为空");
        }
        if (dto.getCandidateId() == null) {
            throw new ServiceException("candidateId 不能为空");
        }

        // 校验 job 与 candidate 同 company(防跨租户)
        OpcHrJob job = jobMapper.selectById(dto.getJobId(), dto.getCompanyId());
        if (job == null) {
            throw new ServiceException("岗位不存在或无权访问 id=" + dto.getJobId());
        }
        OpcHrCandidate candidate = candidateMapper.selectById(dto.getCandidateId(), dto.getCompanyId());
        if (candidate == null) {
            throw new ServiceException("候选人不存在或无权访问 id=" + dto.getCandidateId());
        }

        // W50 教训 1: score 列 NOT NULL DEFAULT 0,显式列名 INSERT 不应用 DEFAULT,service 层兜底
        if (dto.getScore() == null) {
            dto.setScore(0);
        }

        OpcHrApplication app = OpcHrApplication.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(dto.getCompanyId())
                .jobId(dto.getJobId())
                .candidateId(dto.getCandidateId())
                .channel(dto.getChannel() == null || dto.getChannel().isBlank() ? "MANUAL" : dto.getChannel())
                .score(dto.getScore())
                .scoreReason(dto.getScoreReason())
                .status(HrApplicationStatus.NEW.getCode())
                .appliedAt(LocalDateTime.now())
                .build();
        applicationMapper.insert(app);
        log.info("创建投递 id={} jobId={} candidateId={} channel={}",
                app.getId(), app.getJobId(), app.getCandidateId(), app.getChannel());
        return app.getId();
    }

    @Override
    public OpcHrApplication detail(Long id, Long companyId) {
        return validateAndGet(id, companyId);
    }

    @Override
    public List<OpcHrApplication> list(Long companyId, Long jobId, Long candidateId, String status,
                                       int offset, int limit) {
        if (limit <= 0) {
            limit = Integer.MAX_VALUE;
        }
        return applicationMapper.selectList(companyId, jobId, candidateId, status, offset, limit);
    }

    @Override
    public int countList(Long companyId, Long jobId, Long candidateId, String status) {
        return applicationMapper.countList(companyId, jobId, candidateId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transitionStatus(Long id, Long companyId, String targetStatus) {
        if (targetStatus == null || targetStatus.isBlank()) {
            throw new ServiceException("目标状态不能为空");
        }
        OpcHrApplication app = validateAndGet(id, companyId);
        HrApplicationStatus current = HrApplicationStatus.of(app.getStatus());
        HrApplicationStatus target;
        try {
            target = HrApplicationStatus.of(targetStatus);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("未知的目标状态: " + targetStatus);
        }
        if (!current.canTransitionTo(target)) {
            throw new ServiceException("状态 " + current.getCode() + " 无法转换为 " + target.getCode());
        }
        String prev = current.getCode();
        app.setStatus(target.getCode());
        app.setCurrentStage(target.getDesc());
        applicationMapper.updateById(app);
        log.info("投递状态转换 id={} {} -> {}", id, prev, target.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void score(Long id, Long companyId) {
        OpcHrApplication app = validateAndGet(id, companyId);
        // 占位实现:Task 8+ 实接 opc-ai-core HttpLlmClient 调用 hr_candidate_score prompt,
        //   入参 full_jd + parsedJson,出参 score (0-100) + reason,落库 application.score/scoreReason,
        //   并写入 opc_hr_match_score 表
        app.setScore(50);
        app.setScoreReason("待 LLM 评分(占位默认 50 分)");
        applicationMapper.updateById(app);
        log.info("投递评分占位 id={} score=50 (待接 LLM)", id);
    }

    private OpcHrApplication validateAndGet(Long id, Long companyId) {
        OpcHrApplication app = applicationMapper.selectById(id, companyId);
        if (app == null) {
            throw new ServiceException("投递不存在或无权访问 id=" + id);
        }
        return app;
    }

    /**
     * 获取当前登录用户 ID;若未登录(单元测试场景)返回 0L。
     */
    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return 0L;
        }
    }
}