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
 * <p><b>spec 与 handler 同生共死</b>：注册了 handler 却没注册 spec，
 * 对外就会出现「规格说有这个工具、实际调用不了」—— 又是一句假话。
 * {@link #unregister(String)} 两个一起删，{@link #spec(String)} 与
 * {@link #specs()} 只反映真实注册过的工具。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();

    private final Map<String, ToolSpec> specs = new ConcurrentHashMap<>();

    /**
     * 注册工具（带规格）。
     *
     * @param spec 工具规格；为 null 时不覆盖已有 spec（兼容旧的两参注册方式）
     */
    public void register(String name, ToolSpec spec, ToolHandler handler) {
        handlers.put(name, handler);
        if (spec != null) {
            specs.put(name, spec);
        }
        log.info("[ToolRegistry] 注册工具 name={} hasSpec={}", name, spec != null);
    }

    /**
     * 注册工具（不带规格）—— 保留旧签名，既有调用方不受影响。
     */
    public void register(String name, ToolHandler handler) {
        register(name, null, handler);
    }

    public void unregister(String name) {
        handlers.remove(name);
        specs.remove(name);
    }

    public ToolHandler get(String name) {
        return handlers.get(name);
    }

    /**
     * 取工具规格 —— 没有注册过（或注册时没带 spec）返回 null。
     */
    public ToolSpec spec(String name) {
        return specs.get(name);
    }

    /**
     * 全部已注册工具的规格 —— 返回不可变副本，外部改不动注册表。
     */
    public Map<String, ToolSpec> specs() {
        return Map.copyOf(specs);
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
