package com.ruoyi.opc.ai.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 * Agent Runtime 通过 ToolRegistry 发现并执行工具
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();

    public void register(String name, ToolHandler handler) {
        handlers.put(name, handler);
        log.info("[ToolRegistry] 注册工具 name={}", name);
    }

    public void unregister(String name) {
        handlers.remove(name);
    }

    public ToolHandler get(String name) {
        return handlers.get(name);
    }

    public List<String> names() {
        return new ArrayList<>(handlers.keySet());
    }

    public boolean exists(String name) {
        return handlers.containsKey(name);
    }

    /** 工具处理函数接口 */
    @FunctionalInterface
    public interface ToolHandler {
        /** 返回 JSON 字符串结果 */
        String execute(String argsJson, Map<String, Object> context) throws Exception;
    }

}
