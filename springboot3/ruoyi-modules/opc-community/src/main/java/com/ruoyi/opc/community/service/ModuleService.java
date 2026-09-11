package com.ruoyi.opc.community.service;

import com.ruoyi.opc.community.domain.CommunityModule;
import com.ruoyi.opc.community.dto.ModuleCreateRequest;

import java.util.List;
import java.util.Map;

public interface ModuleService {
    CommunityModule create(ModuleCreateRequest req, Long operatorId, String operatorName);
    CommunityModule getById(Long id);
    List<CommunityModule> list(String category, String keyword, int page, int pageSize);
    int countList(String category, String keyword);
    void incrementInstall(Long id);
    CommunityModule updateRating(Long id);
}
