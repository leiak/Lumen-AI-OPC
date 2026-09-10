package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.agent.domain.OpcAgentDefinition;
import com.ruoyi.opc.agent.domain.OpcAgentInstance;
import com.ruoyi.opc.agent.domain.OpcAgentTask;
import com.ruoyi.opc.agent.domain.OpcAgentTokenUsage;
import com.ruoyi.opc.agent.service.IOpcAgentDefinitionService;
import com.ruoyi.opc.agent.service.IOpcAgentInstanceService;
import com.ruoyi.opc.agent.service.IOpcAgentTaskService;
import com.ruoyi.opc.agent.service.IOpcTokenUsageService;
import com.ruoyi.opc.common.exception.OpcException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Hub 对外 API
 *
 * @author OAC
 */
@Tag(name = "OPC Agent Hub")
@RestController
@RequestMapping("/opc/agent")
@RequiredArgsConstructor
public class OpcAgentController extends BaseController {

    private final IOpcAgentDefinitionService definitionService;
    private final IOpcAgentInstanceService instanceService;
    private final IOpcAgentTaskService taskService;
    private final IOpcTokenUsageService tokenUsageService;

    @Operation(summary = "Agent 市场列表")
    @GetMapping("/market")
    public AjaxResult market(@RequestParam(required = false) String category) {
        List<OpcAgentDefinition> list = definitionService.listPublished(category);
        return success(list);
    }

    @Operation(summary = "Agent 详情")
    @GetMapping("/detail/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        OpcAgentDefinition def = definitionService.getById(id);
        if (def == null) throw new OpcException("Agent 不存在");
        return success(def);
    }

    @Operation(summary = "雇佣 Agent")
    @PostMapping("/hire")
    public AjaxResult hire(@RequestBody HireRequest req) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) throw new OpcException("未登录");

        OpcAgentInstance inst = instanceService.hire(
                req.companyId, req.definitionId, req.hireType, req.duration, req.nickname);

        // TODO: 调用 opc-billing 扣费

        return success(inst);
    }

    @Operation(summary = "我的 Agent 实例")
    @GetMapping("/instances")
    public AjaxResult myInstances() {
        Long userId = SecurityUtils.getUserId();
        return success(instanceService.listRunningByUser(userId));
    }

    @Operation(summary = "实例详情")
    @GetMapping("/instance/{id}")
    public AjaxResult instance(@PathVariable Long id) {
        return success(instanceService.getById(id));
    }

    @Operation(summary = "暂停/恢复/退订")
    @PostMapping("/instance/{id}/action")
    public AjaxResult instanceAction(@PathVariable Long id, @RequestParam String action) {
        // W48.5: 前端历史调用统一大写 PAUSE/RESUME/REVOKE,这里 toUpperCase 容错
        String act = action == null ? "" : action.toUpperCase();
        int n;
        switch (act) {
            case "PAUSE" -> n = instanceService.pause(id);
            case "RESUME" -> n = instanceService.resume(id);
            case "REVOKE" -> n = instanceService.revoke(id);
            default -> throw new OpcException("未知动作：" + action);
        }
        return success(n > 0);
    }

    @Operation(summary = "实例任务列表")
    @GetMapping("/instance/{id}/tasks")
    public AjaxResult instanceTasks(@PathVariable Long id,
                                     @RequestParam(defaultValue = "20") Integer limit) {
        return success(taskService.listByInstance(id, limit));
    }

    @Operation(summary = "Token 消耗记录")
    @GetMapping("/instance/{id}/usage")
    public AjaxResult instanceUsage(@PathVariable Long id,
                                     @RequestParam(defaultValue = "20") Integer limit) {
        return success(tokenUsageService.listByCompany(id, limit));
    }

    @Operation(summary = "Token 每日汇总")
    @GetMapping("/usage/daily")
    public AjaxResult dailyUsage(@RequestParam Long companyId,
                                  @RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate) {
        return success(tokenUsageService.aggregateDaily(companyId, startDate, endDate));
    }

    @Operation(summary = "Token 汇总")
    @GetMapping("/usage/summary")
    public AjaxResult usageSummary(@RequestParam Long companyId,
                                    @RequestParam(required = false) String bizDate) {
        return success(tokenUsageService.summary(companyId, bizDate));
    }

    @Operation(summary = "执行任务（同步）")
    @PostMapping("/task/run")
    public AjaxResult runTask(@RequestBody RunTaskRequest req) {
        // TODO: 调 AgentRuntime.run 并写回 task 表
        return success(Map.of("taskCode", "RUNNING"));
    }

    @lombok.Data
    public static class HireRequest {
        public Long companyId;
        public Long definitionId;
        public String hireType;
        public Integer duration;
        public String nickname;
    }

    @lombok.Data
    public static class RunTaskRequest {
        public Long instanceId;
        public String taskType;
        public String input;
        public String sessionId;
    }

}
