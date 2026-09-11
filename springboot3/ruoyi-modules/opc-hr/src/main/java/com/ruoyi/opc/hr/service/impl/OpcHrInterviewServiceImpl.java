package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.domain.OpcHrInterview;
import com.ruoyi.opc.hr.dto.OpcHrInterviewDto;
import com.ruoyi.opc.hr.enums.HrInterviewResult;
import com.ruoyi.opc.hr.enums.HrInterviewType;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrInterviewMapper;
import com.ruoyi.opc.hr.service.IOpcHrInterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpcHrInterviewServiceImpl implements IOpcHrInterviewService {

    private final OpcHrInterviewMapper interviewMapper;
    private final OpcHrApplicationMapper applicationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpcHrInterviewDto dto) {
        if (dto.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (dto.getApplicationId() == null) {
            throw new ServiceException("applicationId 不能为空");
        }
        if (dto.getType() == null || dto.getType().isBlank()) {
            throw new ServiceException("type 不能为空");
        }
        if (dto.getScheduledAt() == null) {
            throw new ServiceException("scheduledAt 不能为空");
        }
        if (dto.getInterviewerId() == null) {
            throw new ServiceException("interviewerId 不能为空");
        }
        // 校验 type 合法
        HrInterviewType.of(dto.getType());

        // 校验 application 同 company(防跨租户)
        OpcHrApplication app = applicationMapper.selectById(dto.getApplicationId(), dto.getCompanyId());
        if (app == null) {
            throw new ServiceException("投递不存在或无权访问 id=" + dto.getApplicationId());
        }

        // round 自增:已有 maxRound + 1,否则 1
        Integer maxRound = interviewMapper.maxRoundByApplication(dto.getApplicationId());
        int nextRound = (maxRound == null ? 0 : maxRound) + 1;

        OpcHrInterview iv = OpcHrInterview.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(dto.getCompanyId())
                .applicationId(dto.getApplicationId())
                .round(nextRound)
                .type(dto.getType())
                .interviewerId(dto.getInterviewerId())
                .scheduledAt(dto.getScheduledAt())
                .durationMin(dto.getDurationMin() == null ? 60 : dto.getDurationMin())
                .feedback(dto.getFeedback())
                .result(HrInterviewResult.PENDING.getCode())
                .build();
        interviewMapper.insert(iv);
        log.info("创建面试 id={} app={} round={} type={}",
                iv.getId(), iv.getApplicationId(), iv.getRound(), iv.getType());
        return iv.getId();
    }

    @Override
    public List<OpcHrInterview> list(Long companyId, Long applicationId) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (applicationId == null) {
            return Collections.emptyList();
        }
        return interviewMapper.selectListByApplication(companyId, applicationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateFeedback(Long id, Long companyId, String feedback, String result) {
        if (id == null || companyId == null) {
            throw new ServiceException("id / companyId 不能为空");
        }
        OpcHrInterview iv = validateAndGet(id, companyId);
        if (feedback != null) {
            iv.setFeedback(feedback);
        }
        if (result != null && !result.isBlank()) {
            // 校验 result 合法(PENDING/PASS/FAIL)
            HrInterviewResult.of(result);
            iv.setResult(result);
        }
        int rows = interviewMapper.updateById(iv);
        log.info("更新面试反馈 id={} feedback.len={} result={}", id,
                feedback == null ? 0 : feedback.length(), iv.getResult());
        return rows;
    }

    private OpcHrInterview validateAndGet(Long id, Long companyId) {
        OpcHrInterview iv = interviewMapper.selectById(id, companyId);
        if (iv == null) {
            throw new ServiceException("面试不存在或无权访问 id=" + id);
        }
        return iv;
    }
}
