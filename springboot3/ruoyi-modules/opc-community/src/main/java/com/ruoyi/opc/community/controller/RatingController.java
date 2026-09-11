package com.ruoyi.opc.community.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.community.domain.CommunityModule;
import com.ruoyi.opc.community.domain.CommunityRating;
import com.ruoyi.opc.community.dto.RatingCreateRequest;
import com.ruoyi.opc.community.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/opc/community/rating")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public R<CommunityRating> rate(@Valid @RequestBody RatingCreateRequest req) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(ratingService.rate(req, userId));
    }

    @GetMapping
    public R<List<CommunityRating>> list(@RequestParam Long moduleId) {
        return R.ok(ratingService.listForModule(moduleId));
    }

    @PostMapping("/{moduleId}/refresh")
    public R<CommunityModule> refresh(@PathVariable Long moduleId) {
        return R.ok(ratingService.refreshModuleRating(moduleId));
    }
}
