package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.dto.OpcHrJobDto;
import com.ruoyi.opc.hr.enums.HrJobStatus;
import com.ruoyi.opc.hr.mapper.OpcHrJobMapper;
import com.ruoyi.opc.hr.service.IOpcHrJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpcHrJobServiceImpl implements IOpcHrJobService {

    private final OpcHrJobMapper jobMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpcHrJobDto dto) {
        if (dto.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new ServiceException("title 不能为空");
        }
        if (dto.getCategory() == null || dto.getCategory().isBlank()) {
            throw new ServiceException("category 不能为空");
        }
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new ServiceException("description 不能为空");
        }

        OpcHrJob job = OpcHrJob.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(dto.getCompanyId())
                .title(dto.getTitle())
                .category(dto.getCategory())
                .description(dto.getDescription())
                .fullJd(dto.getFullJd())
                .skillsJson(dto.getSkillsJson())
                .salaryMin(dto.getSalaryMin())
                .salaryMax(dto.getSalaryMax())
                .location(dto.getLocation())
                .status(HrJobStatus.DRAFT.getCode())
                .build();
        jobMapper.insert(job);
        log.info("Created JD id={} title={} companyId={}", job.getId(), job.getTitle(), job.getCompanyId());
        return job.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, Long companyId, OpcHrJobDto dto) {
        OpcHrJob existing = validateAndGet(id, companyId);
        if (!HrJobStatus.DRAFT.getCode().equals(existing.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态的 JD 可修改 (当前: " + existing.getStatus() + ")");
        }
        if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
        if (dto.getCategory() != null) existing.setCategory(dto.getCategory());
        if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
        if (dto.getFullJd() != null) existing.setFullJd(dto.getFullJd());
        if (dto.getSkillsJson() != null) existing.setSkillsJson(dto.getSkillsJson());
        if (dto.getSalaryMin() != null) existing.setSalaryMin(dto.getSalaryMin());
        if (dto.getSalaryMax() != null) existing.setSalaryMax(dto.getSalaryMax());
        if (dto.getLocation() != null) existing.setLocation(dto.getLocation());
        return jobMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id, Long companyId) {
        OpcHrJob existing = validateAndGet(id, companyId);
        if (!HrJobStatus.DRAFT.getCode().equals(existing.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态的 JD 可删除 (当前: " + existing.getStatus() + ")");
        }
        return jobMapper.deleteById(id, companyId);
    }

    @Override
    public OpcHrJob detail(Long id, Long companyId) {
        return validateAndGet(id, companyId);
    }

    @Override
    public List<OpcHrJob> list(Long companyId, String status) {
        return jobMapper.selectList(companyId, status, 0, Integer.MAX_VALUE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Long companyId) {
        OpcHrJob job = validateAndGet(id, companyId);
        String cur = job.getStatus();
        if (!HrJobStatus.DRAFT.getCode().equals(cur) && !HrJobStatus.PAUSED.getCode().equals(cur)) {
            throw new ServiceException("仅 DRAFT/PAUSED 状态的 JD 可发布 (当前: " + cur + ")");
        }
        job.setStatus(HrJobStatus.OPEN.getCode());
        job.setPublishAt(LocalDateTime.now());
        jobMapper.updateById(job);
        log.info("Published JD id={} from {} -> OPEN", id, cur);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, Long companyId) {
        OpcHrJob job = validateAndGet(id, companyId);
        if (!HrJobStatus.OPEN.getCode().equals(job.getStatus())) {
            throw new ServiceException("仅 OPEN 状态的 JD 可关闭 (当前: " + job.getStatus() + ")");
        }
        job.setStatus(HrJobStatus.CLOSED.getCode());
        job.setCloseAt(LocalDateTime.now());
        jobMapper.updateById(job);
        log.info("Closed JD id={}", id);
    }

    @Override
    public String generateLlm(Long companyId, String title, String category, String description) {
        // TODO Task 4: 调 opc-ai-core HttpLlmClient 生成 JD 文案
        log.warn("generateLlm is not yet implemented (Task 4). companyId={} title={}", companyId, title);
        return null;
    }

    private OpcHrJob validateAndGet(Long id, Long companyId) {
        OpcHrJob job = jobMapper.selectById(id, companyId);
        if (job == null) {
            throw new ServiceException("岗位不存在或无权访问 id=" + id);
        }
        return job;
    }
}
