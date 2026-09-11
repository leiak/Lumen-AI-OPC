package com.ruoyi.opc.community.mapper;

import com.ruoyi.opc.community.domain.CommunityRating;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CommunityRatingMapper {
    int upsert(CommunityRating rating);
    CommunityRating selectByModuleAndUser(Long moduleId, Long userId);
    List<CommunityRating> selectByModuleId(Long moduleId);
    BigDecimal averageForModule(Long moduleId);
    int countForModule(Long moduleId);
}
