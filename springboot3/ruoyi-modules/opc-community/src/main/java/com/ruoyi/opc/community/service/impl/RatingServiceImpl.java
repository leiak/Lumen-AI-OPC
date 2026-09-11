package com.ruoyi.opc.community.service.impl;

import com.ruoyi.opc.community.domain.CommunityModule;
import com.ruoyi.opc.community.domain.CommunityRating;
import com.ruoyi.opc.community.dto.RatingCreateRequest;
import com.ruoyi.opc.community.mapper.CommunityModuleMapper;
import com.ruoyi.opc.community.mapper.CommunityRatingMapper;
import com.ruoyi.opc.community.service.RatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final CommunityRatingMapper ratingMapper;
    private final CommunityModuleMapper moduleMapper;

    @Override
    @Transactional
    public CommunityRating rate(RatingCreateRequest req, Long operatorId) {
        if (moduleMapper.selectById(req.getModuleId()) == null) {
            throw new IllegalArgumentException("模块不存在: " + req.getModuleId());
        }
        CommunityRating r = CommunityRating.builder()
                .moduleId(req.getModuleId())
                .userId(operatorId)
                .score(req.getScore())
                .review(req.getReview())
                .build();
        ratingMapper.upsert(r);
        log.info("Rated module={} by user={} score={}", req.getModuleId(), operatorId, req.getScore());
        return r;
    }

    @Override
    public List<CommunityRating> listForModule(Long moduleId) {
        return ratingMapper.selectByModuleId(moduleId);
    }

    @Override
    @Transactional
    public CommunityModule refreshModuleRating(Long moduleId) {
        BigDecimal avg = ratingMapper.averageForModule(moduleId);
        int count = ratingMapper.countForModule(moduleId);
        if (count > 0) {
            moduleMapper.updateRating(moduleId, avg.setScale(2, RoundingMode.HALF_UP), count);
        }
        return moduleMapper.selectById(moduleId);
    }
}
