package com.ruoyi.opc.community.service;

import com.ruoyi.opc.community.domain.CommunityModule;
import com.ruoyi.opc.community.domain.CommunityRating;
import com.ruoyi.opc.community.dto.RatingCreateRequest;

import java.util.List;

public interface RatingService {
    CommunityRating rate(RatingCreateRequest req, Long operatorId);
    List<CommunityRating> listForModule(Long moduleId);
    CommunityModule refreshModuleRating(Long moduleId);
}
