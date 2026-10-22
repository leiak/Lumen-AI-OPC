package com.ruoyi.opc.insight.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.service.IAnomalyService;
import com.ruoyi.opc.insight.vo.AnomalyVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/opc/insight/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final IAnomalyService anomalyService;

    @GetMapping
    public R<List<AnomalyVo>> list(@RequestParam(required = false) Integer limit) {
        return R.ok(anomalyService.listOpen(resolveCompanyId(), limit));
    }
    @GetMapping("/{id}")
    public R<AnomalyVo> detail(@PathVariable Long id) {
        return R.ok(anomalyService.getById(id));
    }
    @PostMapping("/{id}/ack")
    public R<Void> ack(@PathVariable Long id) { anomalyService.acknowledge(id); return R.ok(); }
    private Long resolveCompanyId() {
        // 原为 getUserId():把 user_id 当 company_id 用,详见 DashboardController 同名方法注释
        Long id = SecurityUtils.getCompanyId();
        if (id == null) throw new OpcException("当前账号未关联企业，请先加入或创建企业");
        return id;
    }
}
