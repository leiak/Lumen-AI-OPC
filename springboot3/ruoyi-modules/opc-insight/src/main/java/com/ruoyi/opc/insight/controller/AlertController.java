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
        return R.ok(anomalyService.listOpen(resolveCompanyId(), null).stream()
                .filter(a -> id.equals(a.getId())).findFirst()
                .orElseThrow(() -> new OpcException("异常不存在: " + id)));
    }
    @PostMapping("/{id}/ack")
    public R<Void> ack(@PathVariable Long id) { anomalyService.acknowledge(id); return R.ok(); }
    private Long resolveCompanyId() {
        Long id = SecurityUtils.getUserId();
        if (id == null) throw new OpcException("请先登录");
        return id;
    }
}
