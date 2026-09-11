package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import com.ruoyi.opc.hr.dto.HrSearchRequest;
import com.ruoyi.opc.hr.dto.HrSearchResult;
import com.ruoyi.opc.hr.dto.OpcHrCandidateDto;
import com.ruoyi.opc.hr.service.IOpcHrCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * HR 候选人管理 Controller
 * 7 endpoints: create / list / detail / parse / update / delete / search
 */
@RestController
@RequestMapping("/opc/hr/candidate")
@RequiredArgsConstructor
public class OpcHrCandidateController {

    private final IOpcHrCandidateService candidateService;

    @PostMapping
    public R<Long> create(@RequestBody OpcHrCandidateDto dto) {
        return R.ok(candidateService.create(dto));
    }

    @GetMapping("/list")
    public R<List<OpcHrCandidate>> list(@RequestParam Long companyId,
                                        @RequestParam(defaultValue = "0") int offset,
                                        @RequestParam(defaultValue = "100") int limit) {
        return R.ok(candidateService.list(companyId, offset, limit));
    }

    @GetMapping("/{id}")
    public R<OpcHrCandidate> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(candidateService.detail(id, companyId));
    }

    @PostMapping("/{id}/parse")
    public R<Void> parse(@PathVariable Long id, @RequestParam Long companyId) {
        candidateService.parse(id, companyId);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody OpcHrCandidateDto dto) {
        candidateService.update(id, dto.getCompanyId(), dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam Long companyId) {
        candidateService.delete(id, companyId);
        return R.ok();
    }

    @PostMapping("/search")
    public R<List<HrSearchResult>> search(@RequestParam Long companyId,
                                          @RequestBody HrSearchRequest req) {
        return R.ok(candidateService.search(companyId, req));
    }
}