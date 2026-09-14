package com.ruoyi.opc.content.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.domain.OpcContentPublish;
import com.ruoyi.opc.content.dto.OpcContentListResponse;
import com.ruoyi.opc.content.dto.OpcContentPublishRequest;
import com.ruoyi.opc.content.service.IOpcContentPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPC 内容发布 Controller — 脚本发布到抖音 + 发布记录管理 + 失败重试。
 *
 * <p>W74 Task 5: 4 endpoints。
 * <ul>
 *   <li>POST  /                       发布脚本(仅 READY 状态)</li>
 *   <li>GET   /list                   公司下发布记录分页列表</li>
 *   <li>GET   /{id}                   发布详情</li>
 *   <li>POST  /{id}/retry             失败重试(FAILED → PENDING 状态机守卫)</li>
 * </ul>
 */
@Tag(name = "OPC 内容发布")
@RestController
@RequestMapping("/opc/content/publish")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OpcContentPublishController {

    private final IOpcContentPublishService publishService;

    /**
     * 发布脚本到指定平台账号(脚本 status 必须为 READY)。
     */
    @Operation(summary = "发布脚本到指定平台账号(仅 READY)")
    @PostMapping
    public R<Long> publish(@RequestBody @Valid OpcContentPublishRequest req) {
        log.info("[opc-content] action=publish scriptId={} platformAccountId={}",
                req.getScriptId(), req.getPlatformAccountId());
        return R.ok(publishService.publish(req));
    }

    /**
     * 公司下发布记录分页列表(status 可选过滤)。
     */
    @Operation(summary = "公司下发布记录分页列表")
    @GetMapping("/list")
    public R<OpcContentListResponse<OpcContentPublish>> list(
            @RequestParam Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须 >= 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 必须 >= 1") @Max(value = 100, message = "size 必须 <= 100") int size) {
        return R.ok(publishService.listByCompany(companyId, status, page, size));
    }

    /**
     * 发布详情(跨租户隔离)。
     */
    @Operation(summary = "发布详情")
    @GetMapping("/{id}")
    public R<OpcContentPublish> detail(@PathVariable Long id,
                                       @RequestParam Long companyId) {
        return R.ok(publishService.detail(id, companyId));
    }

    /**
     * 失败重试: FAILED → PENDING(状态机守卫,非 FAILED 状态抛 ServiceException)。
     */
    @Operation(summary = "失败重试(FAILED → PENDING)")
    @PostMapping("/{id}/retry")
    public R<Void> retry(@PathVariable Long id,
                         @RequestParam Long companyId) {
        publishService.retry(id, companyId);
        return R.ok();
    }
}
