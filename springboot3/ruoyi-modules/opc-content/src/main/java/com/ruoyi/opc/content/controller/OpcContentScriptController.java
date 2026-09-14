package com.ruoyi.opc.content.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.domain.OpcContentPublish;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentAdaptRequest;
import com.ruoyi.opc.content.dto.OpcContentDashboardDto;
import com.ruoyi.opc.content.dto.OpcContentGenerateRequest;
import com.ruoyi.opc.content.dto.OpcContentListResponse;
import com.ruoyi.opc.content.dto.OpcContentRefineRequest;
import com.ruoyi.opc.content.dto.OpcContentScriptDto;
import com.ruoyi.opc.content.service.IOpcContentAdaptService;
import com.ruoyi.opc.content.service.IOpcContentScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OPC 内容脚本 Controller — 创建 / 生成 / 精修 / 适配 / 发布历史 / Dashboard。
 *
 * <p>W74 Task 5: 11 endpoints (含 Dashboard)。
 * <ul>
 *   <li>POST  /                          创建脚本 + 触发 LLM 生成</li>
 *   <li>GET   /list                      分页列表 + type/status 过滤</li>
 *   <li>GET   /{id}                      脚本详情</li>
 *   <li>PUT   /{id}                      更新(仅 DRAFT)</li>
 *   <li>DELETE /{id}                     删除(仅 DRAFT,软删)</li>
 *   <li>POST  /{id}/generate             重新生成</li>
 *   <li>POST  /{id}/refine               部分精修</li>
 *   <li>POST  /{id}/ready                DRAFT → READY</li>
 *   <li>POST  /adapt                     平台适配(原文 → 新脚本)</li>
 *   <li>GET   /{id}/publish-history      脚本发布历史</li>
 *   <li>GET   /dashboard                 Dashboard 聚合统计</li>
 * </ul>
 */
@Tag(name = "OPC 内容脚本")
@RestController
@RequestMapping("/opc/content/script")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OpcContentScriptController {

    private final IOpcContentScriptService scriptService;
    private final IOpcContentAdaptService adaptService;

    /**
     * 创建脚本并触发 LLM 生成(DRAMA/VIDEO/ARTICLE)。
     */
    @Operation(summary = "创建脚本 + 触发 LLM 生成")
    @PostMapping
    public R<Long> create(@RequestBody @Valid OpcContentGenerateRequest req) {
        log.info("[opc-content] action=create script type={} promptLen={}",
                req.getType(), req.getPromptInput() == null ? 0 : req.getPromptInput().length());
        return R.ok(scriptService.create(req));
    }

    /**
     * 脚本分页列表 + type/status 过滤。
     */
    @Operation(summary = "脚本分页列表(type/status/page 过滤)")
    @GetMapping("/list")
    public R<OpcContentListResponse<OpcContentScriptDto>> list(
            @RequestParam Long companyId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须 >= 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 必须 >= 1") @Max(value = 100, message = "size 必须 <= 100") int size) {
        OpcContentListResponse<OpcContentScript> resp =
                scriptService.list(companyId, type, status, page, size);
        return R.ok(OpcContentListResponse.<OpcContentScriptDto>builder()
                .rows(resp.getRows().stream().map(this::toDto).toList())
                .total(resp.getTotal())
                .build());
    }

    /**
     * 脚本详情(跨租户隔离)。
     */
    @Operation(summary = "脚本详情")
    @GetMapping("/{id}")
    public R<OpcContentScriptDto> detail(@PathVariable Long id,
                                         @RequestParam Long companyId) {
        OpcContentScript s = scriptService.detail(id, companyId);
        return R.ok(toDto(s));
    }

    /**
     * 更新脚本 title/contentMd(仅 DRAFT 状态可写)。
     */
    @Operation(summary = "更新脚本(仅 DRAFT 状态)")
    @PutMapping("/{id}")
    public R<Integer> update(@PathVariable Long id,
                             @RequestParam Long companyId,
                             @RequestBody @Valid OpcContentScriptDto dto) {
        return R.ok(scriptService.update(id, companyId, dto));
    }

    /**
     * 删除脚本(仅 DRAFT 状态,软删 → DELETED)。
     */
    @Operation(summary = "删除脚本(仅 DRAFT,软删)")
    @DeleteMapping("/{id}")
    public R<Integer> delete(@PathVariable Long id,
                             @RequestParam Long companyId) {
        return R.ok(scriptService.delete(id, companyId));
    }

    /**
     * 基于新 promptInput 重新调 LLM 生成(覆盖 content_json/content_md)。
     */
    @Operation(summary = "重新生成(覆盖 content)")
    @PostMapping("/{id}/generate")
    public R<Long> regenerate(@PathVariable Long id,
                              @RequestParam Long companyId,
                              @RequestBody @Valid OpcContentGenerateRequest req) {
        return R.ok(scriptService.regenerate(id, companyId, req.getPromptInput()));
    }

    /**
     * 部分精修: lineNo + instruction 注入 prompt 后缀重新生成。
     */
    @Operation(summary = "部分精修(lineNo + instruction)")
    @PostMapping("/{id}/refine")
    public R<Void> refine(@PathVariable Long id,
                          @RequestParam Long companyId,
                          @RequestBody @Valid OpcContentRefineRequest req) {
        scriptService.refine(id, companyId, req.getLineNo(), req.getInstruction());
        return R.ok();
    }

    /**
     * 标记脚本为可发布状态(DRAFT → READY,状态机守卫)。
     */
    @Operation(summary = "DRAFT → READY")
    @PostMapping("/{id}/ready")
    public R<Void> ready(@PathVariable Long id,
                         @RequestParam Long companyId) {
        scriptService.markReady(id, companyId);
        return R.ok();
    }

    /**
     * 平台适配: 原文脚本 → 调 LLM.adapt → 创建新脚本(type=ADAPTER)。
     */
    @Operation(summary = "平台适配(原文 → 新脚本 type=ADAPTER)")
    @PostMapping("/adapt")
    public R<Long> adapt(@RequestBody @Valid OpcContentAdaptRequest req) {
        return R.ok(adaptService.adapt(req));
    }

    /**
     * 该脚本的全部发布历史(按 created_at 倒序)。
     */
    @Operation(summary = "脚本发布历史")
    @GetMapping("/{id}/publish-history")
    public R<List<OpcContentPublish>> publishHistory(@PathVariable Long id,
                                                     @RequestParam Long companyId) {
        return R.ok(scriptService.publishHistory(id, companyId));
    }

    /**
     * Dashboard 聚合: 今日生成数 / 待发布 / 已发布 / 失败率 / 7 天趋势 / 最近脚本。
     */
    @Operation(summary = "Dashboard 聚合统计(今日生成/待发布/已发布/失败率/7天趋势)")
    @GetMapping("/dashboard")
    public R<OpcContentDashboardDto> dashboard(@RequestParam Long companyId) {
        return R.ok(scriptService.dashboard(companyId));
    }

    /**
     * OpcContentScript → OpcContentScriptDto(snake_case 字段映射由 DTO @JsonProperty 处理)。
     */
    private OpcContentScriptDto toDto(OpcContentScript s) {
        OpcContentScriptDto dto = new OpcContentScriptDto();
        dto.setId(s.getId());
        dto.setCompanyId(s.getCompanyId());
        dto.setUserId(s.getUserId());
        dto.setType(s.getType());
        dto.setTitle(s.getTitle());
        dto.setPromptInput(s.getPromptInput());
        dto.setContentJson(s.getContentJson());
        dto.setContentMd(s.getContentMd());
        dto.setWordCount(s.getWordCount());
        dto.setStatus(s.getStatus());
        dto.setSourceScriptId(s.getSourceScriptId());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }
}
