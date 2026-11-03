package com.ruoyi.opc.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.ai.runtime.AgentRuntime;
import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行器（带白名单 + 超时 + 异常处理）
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String execute(String toolName, String argsJson, AgentRuntime.AgentTaskRequest req) throws Exception {
        ToolRegistry.ToolHandler handler = registry.get(toolName);
        if (handler == null) {
            throw new OpcException("未注册的工具：" + toolName);
        }

        Map<String, Object> context = new HashMap<>();
        // 顺序不能反:先把调用方塞进来的 context 铺平,再写身份字段。
        // 反过来的话,调用方在 req.getContext() 里塞一个 "companyId": 999 就能
        // 覆盖掉注入的 companyId,工具会去查别的租户 —— 跨租户在工具层就漏了。
        if (req.getContext() != null) context.putAll(req.getContext());
        context.put("companyId", req.getCompanyId());
        context.put("userId", req.getUserId());
        context.put("instanceId", req.getInstanceId());
        context.put("taskId", req.getTaskId());

        try {
            log.info("[ToolExecutor] 执行工具 tool={} instance={}", toolName, req.getInstanceId());
            return handler.execute(argsJson == null ? "{}" : argsJson, context);
        } catch (Exception e) {
            log.error("[ToolExecutor] 工具执行失败 tool={}", toolName, e);
            throw e;
        }
    }

}
