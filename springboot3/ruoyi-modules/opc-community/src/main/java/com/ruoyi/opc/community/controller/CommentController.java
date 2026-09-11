package com.ruoyi.opc.community.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.community.domain.CommunityComment;
import com.ruoyi.opc.community.dto.CommentCreateRequest;
import com.ruoyi.opc.community.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/opc/community/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public R<List<CommunityComment>> list(@RequestParam Long moduleId) {
        return R.ok(commentService.listByModule(moduleId));
    }

    @PostMapping
    public R<CommunityComment> add(@Valid @RequestBody CommentCreateRequest req) {
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        return R.ok(commentService.add(req, userId, userName));
    }
}
