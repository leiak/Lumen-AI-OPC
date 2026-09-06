package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;
import com.ruoyi.opc.agent.service.IWorkflowTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流触发内部 API：供 ruoyi-job(Quartz) 通过 Feign 调用。
 *
 * <p>标注 {@link InnerAuth}，要求请求头 {@code from-source: inner}。
 * 网关的 AuthFilter 会剥掉外部请求的该请求头，因此这些端点不会被公网触达。
 *
 * @author OAC
 */
@Tag(name = "OPC 工作流触发（内部）")
@RestController
@RequestMapping("/opc/agent/workflows")
@RequiredArgsConstructor
public class OpcWorkflowTriggerController {

    private final IWorkflowTriggerService triggerService;

    @Operation(summary = "触发一次工作流执行")
    @InnerAuth
    @PostMapping("/{workflowCode}/trigger")
    public R<Map<String, Object>> trigger(@PathVariable("workflowCode") String workflowCode,
                                          @RequestParam(value = "triggerSource", required = false) String triggerSource) {
        OpcAgentWorkflowRun run = triggerService.trigger(workflowCode, triggerSource);
        Map<String, Object> body = new HashMap<>();
        body.put("runCode", run.getRunCode());
        body.put("status", run.getStatus());
        body.put("durationMs", run.getDurationMs());
        body.put("errorMessage", run.getErrorMessage());
        return R.ok(body);
    }

}
