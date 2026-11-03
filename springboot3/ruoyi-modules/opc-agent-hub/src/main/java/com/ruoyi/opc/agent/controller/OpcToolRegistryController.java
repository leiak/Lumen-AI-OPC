package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.ai.tools.ToolRegistry;
import com.ruoyi.opc.ai.tools.ToolSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表的对外视图。
 *
 * <p><b>为什么要有这个端点</b>：不接任何断言，「假工具下线了」就只是嘴上说 ——
 * 注册表里还有没有 {@code query_daily_report} / {@code create_voucher} /
 * {@code extract_voucher}，从外面根本看不见。健康检查第 26.5 条打这个端点，
 * 那条断言才有东西可打。
 *
 * <p><b>只读、只回名字</b>：返回工具名、spec 描述与「有没有 spec」，
 * 不返回 handler 的实现细节，也不返回任何凭据。
 *
 * <p>{@code @InnerAuth} —— 要求请求头 {@code from-source: inner}，网关的 AuthFilter
 * 会剥掉外部请求的这个头，所以只有服务间调用能命中（健康检查直连 127.0.0.1:9303）。
 *
 * @author OAC
 */
@Tag(name = "工具注册表")
@RestController
@RequestMapping("/opc/agent/tools")
@RequiredArgsConstructor
public class OpcToolRegistryController {

    private final ToolRegistry registry;

    @Operation(summary = "列出当前进程里已注册的工具（健康检查断言假工具已下线）")
    @InnerAuth
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        Map<String, ToolSpec> specs = registry.specs();
        return R.ok(registry.names().stream()
                .map(name -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    ToolSpec spec = specs.get(name);
                    item.put("hasSpec", spec != null);
                    item.put("specDescription", spec == null ? null : spec.getDescription());
                    return item;
                })
                .toList());
    }

}
