package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import com.ruoyi.opc.hr.dto.HrSearchRequest;
import com.ruoyi.opc.hr.dto.HrSearchResult;
import com.ruoyi.opc.hr.dto.OpcHrCandidateDto;
import com.ruoyi.opc.hr.mapper.OpcHrCandidateMapper;
import com.ruoyi.opc.hr.service.IOpcHrCandidateService;
import com.ruoyi.opc.hr.service.llm.HrLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpcHrCandidateServiceImpl implements IOpcHrCandidateService {

    private final OpcHrCandidateMapper candidateMapper;
    /** W73 Task 10: 真实接入 opc-ai-core,解析简历 */
    private final HrLlmClient hrLlmClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpcHrCandidateDto dto) {
        if (dto.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ServiceException("name 不能为空");
        }
        if (dto.getResumeUrl() == null || dto.getResumeUrl().isBlank()) {
            throw new ServiceException("resumeUrl 不能为空");
        }

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            OpcHrCandidate existing = candidateMapper.selectByEmail(dto.getCompanyId(), dto.getEmail());
            if (existing != null) {
                throw new ServiceException("同公司邮箱已存在");
            }
        }

        Long operatorId = getCurrentUserId();
        OpcHrCandidate c = OpcHrCandidate.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(dto.getCompanyId())
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .resumeUrl(dto.getResumeUrl())
                .resumeMd(dto.getResumeMd())
                .parsedJson(dto.getParsedJson())
                .source(dto.getSource() == null ? "MANUAL" : dto.getSource())
                .tagsJson(dto.getTagsJson())
                .createdBy(operatorId)
                .build();
        candidateMapper.insert(c);
        log.info("创建候选人 id={} name={} by user={}", c.getId(), c.getName(), operatorId);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, Long companyId, OpcHrCandidateDto dto) {
        OpcHrCandidate existing = validateAndGet(id, companyId);
        if (dto.getTagsJson() != null) existing.setTagsJson(dto.getTagsJson());
        if (dto.getParsedJson() != null) existing.setParsedJson(dto.getParsedJson());
        int rows = candidateMapper.updateById(existing);
        log.info("更新候选人 id={} tags/parsed by user={}", id, getCurrentUserId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id, Long companyId) {
        OpcHrCandidate existing = validateAndGet(id, companyId);
        int rows = candidateMapper.deleteById(id, companyId);
        log.info("删除候选人 id={} name={}", id, existing.getName());
        return rows;
    }

    @Override
    public OpcHrCandidate detail(Long id, Long companyId) {
        return validateAndGet(id, companyId);
    }

    @Override
    public List<OpcHrCandidate> list(Long companyId, int offset, int limit) {
        if (limit <= 0) {
            limit = Integer.MAX_VALUE;
        }
        return candidateMapper.selectList(companyId, offset, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void parse(Long id, Long companyId) {
        OpcHrCandidate existing = validateAndGet(id, companyId);
        if (existing.getResumeMd() == null || existing.getResumeMd().isBlank()) {
            throw new ServiceException("候选人简历为空,无法解析");
        }
        // W73 Task 10: 真实接入 opc-ai-core,场景 hr_resume_parse
        log.info("候选人简历调 LLM 解析 id={} resumeLen={}", id, existing.getResumeMd().length());
        String parsedJson = hrLlmClient.parseResume(existing.getResumeMd());
        existing.setParsedJson(parsedJson);
        candidateMapper.updateById(existing);
        log.info("候选人简历解析完成 id={} parsedLen={}", id, parsedJson.length());
    }

    @Override
    public List<HrSearchResult> search(Long companyId, HrSearchRequest req) {
        if (req == null || req.getQuery() == null || req.getQuery().isBlank()) {
            throw new ServiceException("query 不能为空");
        }
        // 占位实现:Task 8+ 实接 Qdrant 向量召回 top-K + LLM 重排序
        log.info("候选人语义搜索 companyId={} query={} topK={} (占位)",
                companyId, req.getQuery(), req.getTopK());
        return Collections.emptyList();
    }

    private OpcHrCandidate validateAndGet(Long id, Long companyId) {
        OpcHrCandidate c = candidateMapper.selectById(id, companyId);
        if (c == null) {
            throw new ServiceException("候选人不存在或无权访问 id=" + id);
        }
        return c;
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