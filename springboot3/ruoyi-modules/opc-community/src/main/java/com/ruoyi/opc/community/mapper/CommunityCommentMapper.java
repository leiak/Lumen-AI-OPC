package com.ruoyi.opc.community.mapper;

import com.ruoyi.opc.community.domain.CommunityComment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommunityCommentMapper {
    int insert(CommunityComment comment);
    CommunityComment selectById(Long id);
    List<CommunityComment> selectByModuleId(Long moduleId);
    int countByModuleId(Long moduleId);
    int deleteById(Long id);
}
