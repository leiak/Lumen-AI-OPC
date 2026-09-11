package com.ruoyi.opc.community.mapper;

import com.ruoyi.opc.community.domain.CommunityModule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommunityModuleMapper {
    int insert(CommunityModule module);
    CommunityModule selectById(Long id);
    CommunityModule selectByCode(String code);
    List<CommunityModule> selectList(Map<String, Object> params);
    int countList(Map<String, Object> params);
    int updateById(CommunityModule module);
    int incrementInstall(Long id);
    int updateRating(Long id, java.math.BigDecimal rating, int ratingCount);
    int incrementCommentCount(Long id, int delta);
}
