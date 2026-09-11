package com.ruoyi.opc.community.service.impl;

import com.ruoyi.opc.community.domain.CommunityComment;
import com.ruoyi.opc.community.dto.CommentCreateRequest;
import com.ruoyi.opc.community.mapper.CommunityCommentMapper;
import com.ruoyi.opc.community.mapper.CommunityModuleMapper;
import com.ruoyi.opc.community.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommunityCommentMapper commentMapper;
    private final CommunityModuleMapper moduleMapper;

    @Override
    @Transactional
    public CommunityComment add(CommentCreateRequest req, Long operatorId, String operatorName) {
        if (moduleMapper.selectById(req.getModuleId()) == null) {
            throw new IllegalArgumentException("模块不存在: " + req.getModuleId());
        }
        CommunityComment c = CommunityComment.builder()
                .moduleId(req.getModuleId())
                .userId(operatorId)
                .userName(operatorName)
                .content(req.getContent())
                .parentId(req.getParentId())
                .build();
        commentMapper.insert(c);
        moduleMapper.incrementCommentCount(req.getModuleId(), 1);
        log.info("Comment id={} on module={} by user={}", c.getId(), req.getModuleId(), operatorId);
        return c;
    }

    @Override
    public List<CommunityComment> listByModule(Long moduleId) {
        return commentMapper.selectByModuleId(moduleId);
    }
}
