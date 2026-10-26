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
        AnomalyVo vo = anomalyService.getById(id);
        // 详情端点只按 id 取数 —— 改一下 id 就能看别家公司的异常，必须挡住
        requireOwned(vo, id);
        return R.ok(vo);
    }
    @PostMapping("/{id}/ack")
    public R<Void> ack(@PathVariable Long id) {
        // 写操作同样只按 id —— 不校验归属的话，改个 id 就能把别家的异常标记成已确认
        requireOwned(anomalyService.getById(id), id);
        anomalyService.acknowledge(id);
        return R.ok();
    }
    private Long resolveCompanyId() {
        // 原为 getUserId():把 user_id 当 company_id 用,详见 DashboardController 同名方法注释
        Long id = SecurityUtils.getCompanyId();
        if (id == null) throw new OpcException("当前账号未关联企业，请先加入或创建企业");
        return id;
    }
    /**
     * 归属校验：记录自身的 companyId 必须等于当前登录用户所属公司。
     *
     * @param vo  取出来的异常（null 表示记录不存在）
     * @param id  异常 id（只用于错误信息）
     */
    private void requireOwned(AnomalyVo vo, Long id) {
        InsightTenantGuard.requireOwned(vo == null ? null : vo.getCompanyId(),
                resolveCompanyId(), "异常");
    }
}
