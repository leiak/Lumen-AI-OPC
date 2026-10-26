package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.dto.OpcHrJobDto;
import com.ruoyi.opc.hr.dto.OpcHrJobLlmRequest;
import com.ruoyi.opc.hr.service.IOpcHrJobService;
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
 * HR 招聘需求 (JD) 管理 Controller
 * 9 endpoints: create / list / detail / update / delete / publish / close / pause / generate-llm
 */
@RestController
@RequestMapping("/opc/hr/job")
@RequiredArgsConstructor
public class OpcHrJobController {

    private final IOpcHrJobService jobService;

    @PostMapping
    public R<Long> create(@RequestBody OpcHrJobDto dto) {
        return R.ok(jobService.create(dto));
    }

    @GetMapping("/list")
    public R<List<OpcHrJob>> list(@RequestParam Long companyId,
                                  @RequestParam(required = false) String status) {
        return R.ok(jobService.list(companyId, status));
    }

    @GetMapping("/{id}")
    public R<OpcHrJob> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(jobService.detail(id, companyId));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody OpcHrJobDto dto) {
        jobService.update(id, dto.getCompanyId(), dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam Long companyId) {
        jobService.delete(id, companyId);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id, @RequestParam Long companyId) {
        jobService.publish(id, companyId);
        return R.ok();
    }

    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id, @RequestParam Long companyId) {
        jobService.close(id, companyId);
        return R.ok();
    }

    @PostMapping("/{id}/pause")
    public R<Void> pause(@PathVariable Long id, @RequestParam Long companyId) {
        jobService.pause(id, companyId);
        return R.ok();
    }

    @PostMapping("/generate-llm")
    public R<String> generateLlm(@RequestBody OpcHrJobLlmRequest req) {
        // companyId 由租户守卫按登录态改写过了 (CompanyIdBodyAdvice)。
        // 还轮不到 null 只剩一种情况: 登录了但没加入任何公司 —— 没有作用域可用,
        // 认 403; 拿 1 兜底等于把「无租户」当成「公开租户」, 白开一条跨租户的缝。
        Long companyId = req.getCompanyId();
        if (companyId == null) {
            throw new ServiceException("当前账号未关联企业, 无法生成招聘 JD", 403);
        }
        return R.ok(jobService.generateLlm(companyId, req.getTitle(), req.getCategory(), req.getDescription()));
    }
}
