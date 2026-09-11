package com.ruoyi.opc.community.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.community.domain.CommunityModule;
import com.ruoyi.opc.community.dto.ModuleCreateRequest;
import com.ruoyi.opc.community.service.ModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/opc/community/module")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(required = false) String category,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "12") int pageSize) {
        List<CommunityModule> rows = moduleService.list(category, keyword, page, pageSize);
        int total = moduleService.countList(category, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @GetMapping("/{id}")
    public R<CommunityModule> get(@PathVariable Long id) {
        return R.ok(moduleService.getById(id));
    }

    @PostMapping
    public R<CommunityModule> create(@Valid @RequestBody ModuleCreateRequest req) {
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        return R.ok(moduleService.create(req, userId, userName));
    }

    @PostMapping("/{id}/install")
    public R<Void> install(@PathVariable Long id) {
        moduleService.incrementInstall(id);
        return R.ok();
    }
}
