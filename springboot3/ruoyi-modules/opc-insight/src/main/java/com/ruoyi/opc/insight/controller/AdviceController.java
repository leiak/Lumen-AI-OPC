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
    public R<AdviceVo> detail(@PathVariable Long id) {
        AdviceVo vo = adviceService.getById(id);
        // 详情端点只按 id 取数 —— 改一下 id 就能看别家公司的建议，必须挡住
        InsightTenantGuard.requireOwned(vo == null ? null : vo.getCompanyId(),
                resolveCompanyId(), "建议");
        return R.ok(vo);
    }
    @PostMapping("/{id}/regenerate")
    public R<AdviceVo> regenerate(@PathVariable Long id) {
        // 重生成会覆写原行（advice_md / llm_used / confidence）—— 写操作必须先校验归属
        AdviceVo vo = adviceService.getById(id);
        InsightTenantGuard.requireOwned(vo == null ? null : vo.getCompanyId(),
                resolveCompanyId(), "建议");
        return R.ok(adviceService.regenerate(id));
    }
    @PostMapping
    public R<AdviceVo> generate(@RequestParam String topic) { return R.ok(adviceService.generate(resolveCompanyId(), topic)); }
    private Long resolveCompanyId() {
        // 原为 getUserId():把 user_id 当 company_id 用,详见 DashboardController 同名方法注释
        Long id = SecurityUtils.getCompanyId();
        if (id == null) throw new OpcException("当前账号未关联企业，请先加入或创建企业");
        return id;
    }
}
