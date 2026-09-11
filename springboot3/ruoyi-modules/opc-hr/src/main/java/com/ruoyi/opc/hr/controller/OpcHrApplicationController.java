package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.dto.OpcHrApplicationDto;
import com.ruoyi.opc.hr.service.IOpcHrApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HR 投递 (Application) 管理 Controller
 * 5 endpoints: create / list / detail / transitionStatus / score
 */
@RestController
@RequestMapping("/opc/hr/application")
@RequiredArgsConstructor
public class OpcHrApplicationController {

    private final IOpcHrApplicationService applicationService;

    @PostMapping
    public R<Long> create(@RequestBody OpcHrApplicationDto dto) {
        return R.ok(applicationService.create(dto));
    }

    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam Long companyId,
                                       @RequestParam(required = false) Long jobId,
                                       @RequestParam(required = false) Long candidateId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "0") int offset,
                                       @RequestParam(defaultValue = "20") int limit) {
        List<OpcHrApplication> rows = applicationService.list(companyId, jobId, candidateId, status, offset, limit);
        int total = applicationService.countList(companyId, jobId, candidateId, status);
        Map<String, Object> page = new HashMap<>();
        page.put("rows", rows);
        page.put("total", total);
        page.put("offset", offset);
        page.put("limit", limit);
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<OpcHrApplication> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(applicationService.detail(id, companyId));
    }

    @PutMapping("/{id}/status")
    public R<Void> transitionStatus(@PathVariable Long id,
                                    @RequestParam Long companyId,
                                    @RequestParam String status) {
        applicationService.transitionStatus(id, companyId, status);
        return R.ok();
    }

    @PostMapping("/{id}/score")
    public R<Void> score(@PathVariable Long id, @RequestParam Long companyId) {
        applicationService.score(id, companyId);
        return R.ok();
    }
}