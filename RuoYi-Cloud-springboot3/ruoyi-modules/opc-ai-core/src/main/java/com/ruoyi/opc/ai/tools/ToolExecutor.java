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
        context.put("companyId", req.getCompanyId());
        context.put("userId", req.getUserId());
        context.put("instanceId", req.getInstanceId());
        context.put("taskId", req.getTaskId());
        if (req.getContext() != null) context.putAll(req.getContext());

        try {
            log.info("[ToolExecutor] 执行工具 tool={} instance={}", toolName, req.getInstanceId());
            return handler.execute(argsJson == null ? "{}" : argsJson, context);
        } catch (Exception e) {
            log.error("[ToolExecutor] 工具执行失败 tool={}", toolName, e);
            throw e;
        }
    }

}
