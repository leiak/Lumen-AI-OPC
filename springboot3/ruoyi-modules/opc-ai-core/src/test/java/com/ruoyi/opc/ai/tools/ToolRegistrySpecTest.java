package com.ruoyi.opc.ai.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolRegistry} 的 spec 存储行为。
 *
 * <p><b>为什么要单独测</b>：spec 与 handler 同生共死 —— 注册了 handler 却没注册 spec，
 * 对外就会出现「规格说有这个工具、实际调用不了」；反之注销时只删 handler 留着 spec，
 * 就会出现「规格说没有、实际有」。两种都是假话。
 *
 * <p>同时守住向后兼容：既有的两参 {@code register(name, handler)} 不能被改坏
 * —— 那是全仓既有的调用方式。
 *
 * @author OAC
 */
class ToolRegistrySpecTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    private static ToolSpec spec(String name) {
        return ToolSpec.of(name, "描述-" + name, Map.of("type", "object"));
    }

    @Test
    @DisplayName("register(name, spec, handler) — spec 存进注册表，specs() 里能查到")
    void register_withSpec_storesSpec() {
        ToolSpec spec = spec("x");
        ToolRegistry.ToolHandler handler = (argsJson, context) -> "{\"ok\":true}";

        registry.register("x", spec, handler);

        assertSame(spec, registry.spec("x"), "spec() 应返回注册进去的那个 spec 实例");
        assertTrue(registry.specs().containsKey("x"), "specs() 应含 \"x\"");
        assertSame(spec, registry.specs().get("x"));
        assertNotNull(registry.get("x"), "handler 也应该注册上了");
        assertTrue(registry.exists("x"));
    }

    @Test
    @DisplayName("register(name, handler) — 旧的两参签名仍可用，spec() 返回 null")
    void register_withoutSpec_leavesSpecNull() {
        ToolRegistry.ToolHandler handler = (argsJson, context) -> "{\"ok\":true}";

        registry.register("x", handler);

        assertTrue(registry.exists("x"), "handler 应注册成功");
        assertNull(registry.spec("x"), "没带 spec 注册时 spec() 必须为 null");
        assertFalse(registry.specs().containsKey("x"), "specs() 不该凭空多出 \"x\"");
    }

    @Test
    @DisplayName("unregister(name) — handler 与 spec 一起消失")
    void unregister_removesBothHandlerAndSpec() {
        registry.register("x", spec("x"), (argsJson, context) -> "{}");

        registry.unregister("x");

        assertFalse(registry.exists("x"), "注销后 handler 应消失");
        assertNull(registry.spec("x"), "注销后 spec 应一起消失");
        assertFalse(registry.specs().containsKey("x"));
        assertEquals(0, registry.specs().size());
    }

    @Test
    @DisplayName("specs() — 返回不可变副本，外部改不动注册表")
    void specs_returnsImmutableCopy() {
        registry.register("x", spec("x"), (argsJson, context) -> "{}");

        Map<String, ToolSpec> snapshot = registry.specs();
        assertThrowsUnsupported(() -> snapshot.put("y", spec("y")));
    }

    private static void assertThrowsUnsupported(Runnable r) {
        try {
            r.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("specs() 返回的应当是不可变副本");
    }

}
