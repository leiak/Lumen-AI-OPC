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

        // 租户 id 必须来自登录态,不能信请求体。
        // W80 修复:此前直接 `instanceService.hire(req.companyId, ...)` ——
        // A 公司登录后 POST {"companyId": B} 就能把实例行插进 B 的租户命名空间,
        // 且 companyId=null 时也不 fail-closed(违反"禁止 0/null 兜底"约定)。
        // 一旦下面的 billing TODO 落地,这次雇佣还会记到 B 头上。
        // 语义与 dailyUsage/usageSummary 一致: 请求体带了不一致的 companyId 一律 403。
        Long callerCompanyId = resolveCompanyId();
        if (req.companyId != null && !req.companyId.equals(callerCompanyId)) {
            throw new OpcException(403, "无权访问该租户数据", "AGENT");
        }

        OpcAgentInstance inst = instanceService.hire(
                callerCompanyId, req.definitionId, req.hireType, req.duration, req.nickname);

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
        Long companyId = resolveCompanyId();
        OpcAgentInstance inst = instanceService.getByIdAndCompany(id, companyId);
        if (inst == null) {
            // 不区分「不存在」与「不属于该租户」 — 越权探测也返回 403
            throw new OpcException(403, "无权访问该实例", "AGENT");
        }
        return success(inst);
    }

    @Operation(summary = "暂停/恢复/退订")
    @PostMapping("/instance/{id}/action")
    public AjaxResult instanceAction(@PathVariable Long id, @RequestParam String action) {
        Long companyId = resolveCompanyId();
        String operatorId = String.valueOf(SecurityUtils.getUserId());
        // W48.5: 前端历史调用统一大写 PAUSE/RESUME/REVOKE,这里 toUpperCase 容错
        String act = action == null ? "" : action.toUpperCase();
        int n;
        switch (act) {
            case "PAUSE" -> n = instanceService.pause(id, companyId, operatorId);
            case "RESUME" -> n = instanceService.resume(id, companyId, operatorId);
            case "REVOKE" -> n = instanceService.revoke(id, companyId, operatorId);
            default -> throw new OpcException("未知动作：" + action);
        }
        return success(n > 0);
    }

    @Operation(summary = "实例任务列表")
    @GetMapping("/instance/{id}/tasks")
    public AjaxResult instanceTasks(@PathVariable Long id,
                                     @RequestParam(defaultValue = "20") Integer limit) {
        Long companyId = resolveCompanyId();
        // 必须先验证实例归属 — 否则 listByInstance(id) 会泄漏别家任务
        OpcAgentInstance inst = instanceService.getByIdAndCompany(id, companyId);
        if (inst == null) {
            throw new OpcException(403, "无权访问该实例", "AGENT");
        }
        return success(taskService.listByInstance(id, limit));
    }

    @Operation(summary = "Token 消耗记录")
    @GetMapping("/instance/{id}/usage")
    public AjaxResult instanceUsage(@PathVariable Long id,
                                     @RequestParam(defaultValue = "20") Integer limit) {
        Long companyId = resolveCompanyId();
        OpcAgentInstance inst = instanceService.getByIdAndCompany(id, companyId);
        if (inst == null) {
            throw new OpcException(403, "无权访问该实例", "AGENT");
        }
        // 修正 W80 之前的 bug:之前误把 instanceId 当 companyId 传入 listByCompany
        return success(tokenUsageService.listByCompany(inst.getCompanyId(), limit));
    }

    @Operation(summary = "Token 每日汇总")
    @GetMapping("/usage/daily")
    public AjaxResult dailyUsage(@RequestParam Long companyId,
                                  @RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate) {
        Long callerCompanyId = resolveCompanyId();
        if (companyId == null || !companyId.equals(callerCompanyId)) {
            // 公司越权探测 / 缺 companyId 一律 403 —— 不要让 service 拿到「其他公司 id」
            throw new OpcException(403, "无权访问该租户数据", "AGENT");
        }
        return success(tokenUsageService.aggregateDaily(companyId, startDate, endDate));
    }

    @Operation(summary = "Token 汇总")
    @GetMapping("/usage/summary")
    public AjaxResult usageSummary(@RequestParam Long companyId,
                                    @RequestParam(required = false) String bizDate) {
        Long callerCompanyId = resolveCompanyId();
        if (companyId == null || !companyId.equals(callerCompanyId)) {
            throw new OpcException(403, "无权访问该租户数据", "AGENT");
        }
        return success(tokenUsageService.summary(companyId, bizDate));
    }

    @Operation(summary = "执行任务（同步）")
    @PostMapping("/task/run")
    public AjaxResult runTask(@RequestBody RunTaskRequest req) {
        Long companyId = resolveCompanyId();
        // 实例必须属于当前租户 — 否则「我能跑别家的 Agent」就是 RCE 兜底
        OpcAgentInstance inst = instanceService.getByIdAndCompany(req.instanceId, companyId);
        if (inst == null) {
            throw new OpcException(403, "无权访问该实例", "AGENT");
        }
        // TODO: 调 AgentRuntime.run 并写回 task 表
        return success(Map.of("taskCode", "RUNNING"));
    }

    /**
     * 解析登录态的租户 id。{@code SecurityUtils.getCompanyId()} 在无公司时返回 null
     * (绝不会返回 0);此处 fail-closed 用 403,绝不「默认 0」放行。
     */
    private Long resolveCompanyId() {
        Long companyId = SecurityUtils.getCompanyId();
        if (companyId == null) {
            throw new OpcException(403, "当前账号未关联企业，请先加入或创建企业", "AGENT");
        }
        return companyId;
    }

    @lombok.Data
    public static class HireRequest {
        /**
         * 兼容字段,<b>不作为租户来源</b>。
         * 实际落库的 companyId 取自登录态 {@code SecurityUtils.getCompanyId()}。
         * 传 null 视为不指定;传了且与登录态不一致 → 403。
         * 保留它只为不打断仍在发送该字段的老前端 —— 新调用方应直接省略。
         */
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
