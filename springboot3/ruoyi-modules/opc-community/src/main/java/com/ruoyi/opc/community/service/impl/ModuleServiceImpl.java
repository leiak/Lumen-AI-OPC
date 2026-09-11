package com.ruoyi.opc.community.service.impl;

import com.ruoyi.opc.community.domain.CommunityModule;
import com.ruoyi.opc.community.dto.ModuleCreateRequest;
import com.ruoyi.opc.community.mapper.CommunityModuleMapper;
import com.ruoyi.opc.community.mapper.CommunityRatingMapper;
import com.ruoyi.opc.community.service.ModuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

    private final CommunityModuleMapper moduleMapper;
    private final CommunityRatingMapper ratingMapper;

    @Override
    public CommunityModule create(ModuleCreateRequest req, Long operatorId, String operatorName) {
        CommunityModule existing = moduleMapper.selectByCode(req.getCode());
        if (existing != null) {
            throw new IllegalArgumentException("模块 code 已存在: " + req.getCode());
        }
        CommunityModule m = CommunityModule.builder()
                .code(req.getCode())
                .name(req.getName())
                .category(req.getCategory())
                .description(req.getDescription())
                .icon(req.getIcon())
                .tags(req.getTags())
                .ownerId(operatorId)
                .ownerName(operatorName)
                .build();
        moduleMapper.insert(m);
        log.info("Created community module id={} code={} name={} by operator={}",
                m.getId(), m.getCode(), m.getName(), operatorId);
        return m;
    }

    @Override
    public CommunityModule getById(Long id) {
        CommunityModule m = moduleMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("模块不存在: " + id);
        }
        return m;
    }

    @Override
    public List<CommunityModule> list(String category, String keyword, int page, int pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        params.put("keyword", keyword);
        params.put("offset", (page - 1) * pageSize);
        params.put("limit", pageSize);
        return moduleMapper.selectList(params);
    }

    @Override
    public int countList(String category, String keyword) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        params.put("keyword", keyword);
        return moduleMapper.countList(params);
    }

    @Override
    public void incrementInstall(Long id) {
        CommunityModule m = moduleMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("模块不存在: " + id);
        }
        moduleMapper.incrementInstall(id);
        log.info("Module id={} install count incremented", id);
    }

    @Override
    public CommunityModule updateRating(Long id) {
        BigDecimal avg = ratingMapper.averageForModule(id);
        int count = ratingMapper.countForModule(id);
        if (count > 0) {
            moduleMapper.updateRating(id, avg.setScale(2, RoundingMode.HALF_UP), count);
        }
        return moduleMapper.selectById(id);
    }
}
