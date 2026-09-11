package com.ruoyi.opc.community.service;

import com.ruoyi.opc.community.domain.CommunityComment;
import com.ruoyi.opc.community.dto.CommentCreateRequest;

import java.util.List;

public interface CommentService {
    CommunityComment add(CommentCreateRequest req, Long operatorId, String operatorName);
    List<CommunityComment> listByModule(Long moduleId);
}
