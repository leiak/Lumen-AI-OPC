package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.domain.OpcHrOffer;
import com.ruoyi.opc.hr.dto.OpcHrOfferDto;
import com.ruoyi.opc.hr.enums.HrOfferStatus;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrOfferMapper;
import com.ruoyi.opc.hr.service.IOpcHrOfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpcHrOfferServiceImpl implements IOpcHrOfferService {

    private final OpcHrOfferMapper offerMapper;
    private final OpcHrApplicationMapper applicationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpcHrOfferDto dto) {
        if (dto.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (dto.getApplicationId() == null) {
            throw new ServiceException("applicationId 不能为空");
        }
        if (dto.getSalary() == null) {
            throw new ServiceException("salary 不能为空");
        }
        if (dto.getStartDate() == null) {
            throw new ServiceException("startDate 不能为空");
        }
        if (dto.getExpireAt() == null) {
            throw new ServiceException("expireAt 不能为空");
        }

        // 校验 application 同 company(防跨租户)
        OpcHrApplication app = applicationMapper.selectById(dto.getApplicationId(), dto.getCompanyId());
        if (app == null) {
            throw new ServiceException("投递不存在或无权访问 id=" + dto.getApplicationId());
        }

        // 一个 application 只能有一个 offer(uk_application)
        OpcHrOffer existing = offerMapper.selectByApplicationId(dto.getApplicationId());
        if (existing != null) {
            throw new ServiceException("该投递已有 Offer (id=" + existing.getId() + "),不能重复创建");
        }

        OpcHrOffer offer = OpcHrOffer.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(dto.getCompanyId())
                .applicationId(dto.getApplicationId())
                .salary(dto.getSalary())
                .startDate(dto.getStartDate())
                .expireAt(dto.getExpireAt())
                .status(HrOfferStatus.PENDING.getCode())
                .sentAt(LocalDateTime.now())
                .build();
        offerMapper.insert(offer);
        log.info("创建 Offer id={} app={} salary={} expireAt={}",
                offer.getId(), offer.getApplicationId(), offer.getSalary(), offer.getExpireAt());
        return offer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void respond(Long id, Long companyId, String response) {
        if (id == null || companyId == null) {
            throw new ServiceException("id / companyId 不能为空");
        }
        if (response == null || response.isBlank()) {
            throw new ServiceException("response 不能为空");
        }
        // 校验状态合法(ACCEPTED/REJECTED — 暂不允许 PENDING/EXPIRED 直接响应)
        try {
            HrOfferStatus.of(response);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("不支持的状态: " + response);
        }
        if (HrOfferStatus.PENDING.getCode().equals(response)
                || HrOfferStatus.EXPIRED.getCode().equals(response)) {
            throw new ServiceException("不支持的状态转换: " + response);
        }

        OpcHrOffer offer = validateAndGet(id, companyId);
        offer.setStatus(response);
        offer.setRespondedAt(LocalDateTime.now());
        offerMapper.updateById(offer);
        log.info("候选人响应 Offer id={} status={}", id, response);
    }

    private OpcHrOffer validateAndGet(Long id, Long companyId) {
        OpcHrOffer offer = offerMapper.selectById(id, companyId);
        if (offer == null) {
            throw new ServiceException("Offer 不存在或无权访问 id=" + id);
        }
        return offer;
    }
}
