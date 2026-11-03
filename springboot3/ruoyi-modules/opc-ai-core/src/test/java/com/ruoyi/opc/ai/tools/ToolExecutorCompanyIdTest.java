package com.ruoyi.opc.ai.tools;

import com.ruoyi.opc.ai.runtime.AgentRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link ToolExecutor} 的身份注入护栏（R-03）。
 *
 * <p><b>老代码的洞</b>：context 先放身份字段、最后 {@code putAll(req.getContext())} ——
 * 调用方只要在 {@code req.getContext()} 里塞一个 {@code "companyId": 999}，
 * 工具 handler 拿到的就是 999，工具去查<b>别的租户</b>的数据。
 *
 * <p><b>定死的口径</b>：先 {@code putAll(req.getContext())}，再 put 身份字段 ——
 * 身份字段后写，调用方覆盖不了。
 *
 * @author OAC
 */
class ToolExecutorCompanyIdTest {

    private ToolRegistry registry;
    private ToolExecutor executor;
    private AtomicReference<Map<String, Object>> seen;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        executor = new ToolExecutor(registry);
        seen = new AtomicReference<>();
        registry.register("probe", (argsJson, context) -> {
            seen.set(new HashMap<>(context));
            return "{\"ok\":true}";
        });
    }

    @Test
    @DisplayName("execute — req.getContext() 里的 companyId 覆盖不了注入的 companyId")
    void execute_contextCompanyId_cannotOverrideInjectedCompanyId() throws Exception {
        AgentRuntime.AgentTaskRequest req = AgentRuntime.AgentTaskRequest.builder()
                .companyId(2L)
                .userId(11L)
                .instanceId(22L)
                .taskId(33L)
                .context(Map.of("companyId", 999L))
                .build();

        executor.execute("probe", "{}", req);

        assertNotNull(seen.get(), "handler 应当被调用过");
        assertEquals(2L, ((Number) seen.get().get("companyId")).longValue(),
                "companyId 必须是注入的 2，不能被 args/context 里的 999 顶掉");
    }

    @Test
    @DisplayName("execute — 没有 context 覆盖时，companyId 就是注入的那个")
    void execute_withoutOverride_companyIdIsInjectedOne() throws Exception {
        AgentRuntime.AgentTaskRequest req = AgentRuntime.AgentTaskRequest.builder()
                .companyId(2L)
                .userId(11L)
                .instanceId(22L)
                .taskId(33L)
                .build();

        executor.execute("probe", "{}", req);

        assertEquals(2L, ((Number) seen.get().get("companyId")).longValue());
        assertEquals(11L, ((Number) seen.get().get("userId")).longValue());
        assertEquals(22L, ((Number) seen.get().get("instanceId")).longValue());
        assertEquals(33L, ((Number) seen.get().get("taskId")).longValue());
    }

    @Test
    @DisplayName("execute — context 里的其它键仍能透传给工具（只是身份字段不许改）")
    void execute_otherContextKeysStillPassThrough() throws Exception {
        AgentRuntime.AgentTaskRequest req = AgentRuntime.AgentTaskRequest.builder()
                .companyId(2L)
                .context(Map.of("scene", "finance_daily_report"))
                .build();

        executor.execute("probe", "{}", req);

        assertEquals("finance_daily_report", seen.get().get("scene"),
                "非身份字段的 context 键应当照常透传");
    }

}
