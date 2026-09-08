package com.ruoyi.opc.insight.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.service.IDailyReportService;
import com.ruoyi.opc.insight.vo.DailyReportVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/opc/insight/daily")
@RequiredArgsConstructor
public class DailyReportController {
    private final IDailyReportService dailyReportService;
    @GetMapping
    public R<List<DailyReportVo>> list(@RequestParam String from, @RequestParam String to,
                                       @RequestParam(required = false) Integer limit) {
        return R.ok(dailyReportService.listByDateRange(resolveCompanyId(), LocalDate.parse(from), LocalDate.parse(to), limit));
    }
    @GetMapping("/{id}")
    public R<DailyReportVo> detail(@PathVariable Long id) { return R.ok(dailyReportService.getById(id)); }
    @PostMapping("/generate")
    public R<Void> generate(@RequestParam String date) { dailyReportService.generate(resolveCompanyId(), date); return R.ok(); }
    private Long resolveCompanyId() {
        Long id = SecurityUtils.getUserId();
        if (id == null) throw new OpcException("请先登录");
        return id;
    }
}
