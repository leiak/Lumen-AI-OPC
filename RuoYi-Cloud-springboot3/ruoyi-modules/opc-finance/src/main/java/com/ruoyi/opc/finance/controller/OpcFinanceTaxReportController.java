package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OPC 月度税务报表 REST API（W1 Sub-task 4.3）
 *
 * <p>3 个核心端点：
 * <ul>
 *   <li>{@code POST /opc/finance/tax-reports/generate} — 触发月度报表生成</li>
 *   <li>{@code GET  /opc/finance/tax-reports}            — 列出某公司报表</li>
 *   <li>{@code GET  /opc/finance/tax-reports/{id}}      — 查询单条</li>
 * </ul>
 *
 * <p>鉴权沿用 RuoYi 网关的 {@code /opc/finance/**} 白名单（同现有 voucher 控制器），
 * createBy 取自 {@link SecurityUtils#getUsername()}。
 *
 * @author OAC
 */
@Tag(name = "OPC 月度税务报表")
@RestController
@RequestMapping("/opc/finance/tax-reports")
@RequiredArgsConstructor
public class OpcFinanceTaxReportController extends BaseController {

    private final IOpcFinanceTaxReportService taxReportService;

    @Operation(summary = "生成月度税务报表（异步落库，<5s 返回新报表 ID）")
    @PostMapping("/generate")
    public AjaxResult generate(@Parameter(description = "公司 ID") @RequestParam Long companyId,
                                @Parameter(description = "所属期 YYYY-MM") @RequestParam String period) {
        if (companyId == null || period == null) {
            return error("companyId 和 period 不能为空");
        }
        Long reportId = taxReportService.generateMonthlyReport(companyId, period, SecurityUtils.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("reportId", reportId);
        return success(data);
    }

    @Operation(summary = "查询某公司税务报表（支持 period / status 过滤）")
    @GetMapping
    public AjaxResult list(@Parameter(description = "公司 ID") @RequestParam Long companyId,
                            @Parameter(description = "所属期 YYYY-MM(可选)") @RequestParam(required = false) String period,
                            @Parameter(description = "状态 DRAFT/SUBMITTED/PAID(可选)") @RequestParam(required = false) String status,
                            @Parameter(description = "最大条数(默认 20)") @RequestParam(defaultValue = "20") Integer limit) {
        if (companyId == null) {
            return error("companyId 不能为空");
        }
        List<OpcFinanceTaxReport> list = taxReportService.listByCompany(companyId, period, status, limit);
        return success(list);
    }

    @Operation(summary = "查询单条税务报表（含报税建议/附件）")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        OpcFinanceTaxReport r = taxReportService.getById(id);
        if (r == null) {
            return error("报表不存在");
        }
        return success(r);
    }

}