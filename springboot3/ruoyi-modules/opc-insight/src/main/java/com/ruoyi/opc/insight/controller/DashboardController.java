package com.ruoyi.opc.insight.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.service.IAdviceService;
import com.ruoyi.opc.insight.service.IAnomalyService;
import com.ruoyi.opc.insight.service.IKpiService;
import com.ruoyi.opc.insight.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/opc/insight/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final IKpiService kpiService;
    private final IAnomalyService anomalyService;
    private final IAdviceService adviceService;

    @GetMapping
    public R<DashboardVo> dashboard() {
        Long companyId = resolveCompanyId();
        return R.ok(DashboardVo.builder()
                .kpi(kpiService.snapshot(companyId, null))
                .alerts(anomalyService.listOpen(companyId, 5))
                .advice(adviceService.listByCompany(companyId, 3))
                .build());
    }

    private Long resolveCompanyId() {
        // 这里原本错用了 getUserId(),把 user_id 当 company_id 传给下游查询,
        // 导致 opc_insight_* 的数据只有 company_id 恰好等于 user_id 时才查得到,
        // 且 user_id=1 的 admin 能看到所有公司的洞察数据(跨租户)。
        // 其余 OPC 模块(marketplace / agent-hub)一律用 getCompanyId(),这里对齐。
        Long id = SecurityUtils.getCompanyId();
        if (id == null) throw new OpcException("当前账号未关联企业，请先加入或创建企业");
        return id;
    }
}
