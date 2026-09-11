package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.domain.OpcHrInterview;
import com.ruoyi.opc.hr.dto.OpcHrInterviewDto;
import com.ruoyi.opc.hr.service.IOpcHrInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HR 面试 (Interview) Controller
 * 3 endpoints: create / list / updateFeedback
 */
@RestController
@RequestMapping("/opc/hr/interview")
@RequiredArgsConstructor
public class OpcHrInterviewController {

    private final IOpcHrInterviewService interviewService;

    @PostMapping
    public R<Long> create(@RequestBody OpcHrInterviewDto dto) {
        return R.ok(interviewService.create(dto));
    }

    @GetMapping("/list")
    public R<List<OpcHrInterview>> list(@RequestParam Long companyId,
                                        @RequestParam(required = false) Long applicationId) {
        return R.ok(interviewService.list(companyId, applicationId));
    }

    @PutMapping("/{id}")
    public R<Void> updateFeedback(@PathVariable Long id,
                                  @RequestParam Long companyId,
                                  @RequestBody OpcHrInterviewDto dto) {
        interviewService.updateFeedback(id, companyId, dto.getFeedback(), dto.getResult());
        return R.ok();
    }
}
