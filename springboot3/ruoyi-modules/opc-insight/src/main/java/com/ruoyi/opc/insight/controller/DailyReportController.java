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
        // 原为 getUserId():把 user_id 当 company_id 用,详见 DashboardController 同名方法注释
        Long id = SecurityUtils.getCompanyId();
        if (id == null) throw new OpcException("当前账号未关联企业，请先加入或创建企业");
        return id;
    }
}
