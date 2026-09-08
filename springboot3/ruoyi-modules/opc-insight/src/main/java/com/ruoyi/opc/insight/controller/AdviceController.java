package com.ruoyi.opc.insight.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.service.IAdviceService;
import com.ruoyi.opc.insight.vo.AdviceVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/opc/insight/advice")
@RequiredArgsConstructor
public class AdviceController {
    private final IAdviceService adviceService;
    @GetMapping
    public R<List<AdviceVo>> list(@RequestParam(required = false) Integer limit) {
        return R.ok(adviceService.listByCompany(resolveCompanyId(), limit));
    }
    @GetMapping("/{id}")
    public R<AdviceVo> detail(@PathVariable Long id) { return R.ok(adviceService.getById(id)); }
    @PostMapping("/{id}/regenerate")
    public R<AdviceVo> regenerate(@PathVariable Long id) { return R.ok(adviceService.regenerate(id)); }
    @PostMapping
    public R<AdviceVo> generate(@RequestParam String topic) { return R.ok(adviceService.generate(resolveCompanyId(), topic)); }
    private Long resolveCompanyId() {
        Long id = SecurityUtils.getUserId();
        if (id == null) throw new OpcException("请先登录");
        return id;
    }
}
